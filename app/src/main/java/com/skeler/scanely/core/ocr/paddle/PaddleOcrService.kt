package com.skeler.scanely.core.ocr.paddle

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.skeler.scanely.core.ocr.OcrResult
import com.skeler.scanely.core.ocr.OcrSource
import com.skeler.scanely.core.ocr.PdfRendererHelper
import com.skeler.scanely.core.ocr.TextBlockData
import com.skeler.scanely.settings.data.SettingsKeys
import com.skeler.scanely.settings.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

private const val TAG = "PaddleOcrService"
private const val DET_LIMIT = 960
private const val MIN_CONFIDENCE = 0.3f
private const val MIN_TABLE_SIDE = 32

// Lines sampled to settle upright vs upside-down, widest first: a long line carries the
// most evidence, and one batch of them costs a fraction of the full recognition pass.
private const val ORIENTATION_SAMPLE = 5

// A flipped read has to beat the upright one by this much before the page is turned over.
private const val FLIP_MARGIN = 1.15f

// Below this, neither orientation read as text (wrong script, or an unreadable page):
// there is nothing to base a decision on, so the page is left as it is.
private const val ORIENTATION_MIN_SCORE = 3f

// At or above this confident-character density (per detected line) the page is a genuine Latin
// read the universal pack has nothing better to offer, so the other bundled packs are skipped.
// A real Latin page runs 40+; an Arabic page misread on the universal pack stays under 10.
private const val DENSE_CHARS_PER_LINE = 25

// A candidate read only displaces the primary when this much of its directional text is RTL. The
// universal model can misread an Arabic scan as confident Latin or CJK that a line count cannot
// tell from real text; a page's worth of RTL is the one thing a real Latin page cannot fake. A
// bilingual ID card sits near 0.5, a genuine Arabic page at 0.75+.
private const val MIN_RTL_FRACTION = 0.6f

/** Offline PP-OCR pipeline: orient -> dewarp -> detect -> deskew lines -> recognize. */
@Singleton
class PaddleOcrService @Inject constructor(
    private val engine: PaddleOcrEngine,
    private val settings: SettingsRepository,
    private val pdfRendererHelper: PdfRendererHelper
) {

    suspend fun recognize(bitmap: Bitmap): OcrResult = withContext(Dispatchers.Default) {
        val derived = mutableListOf<Bitmap>()
        var crops = listOf<Bitmap>()
        try {
            val pack = ScriptPack.fromId(settings.getString(SettingsKeys.PADDLE_SCRIPT).first())
            val useDocOrientation = settings.getBoolean(SettingsKeys.PADDLE_DOC_ORIENTATION).first()
            val useUnwarp = settings.getBoolean(SettingsKeys.PADDLE_DOC_UNWARP).first()
            val useLineOrientation = settings.getBoolean(SettingsKeys.PADDLE_LINE_ORIENTATION).first()

            var page = bitmap

            if (useDocOrientation) {
                // Only the sideways decision comes from the classifier. It centre-crops to 224,
                // so on a photo where the page does not fill the frame it judges mostly margin
                // and answers "180" whichever way up the page is (0.90 confidence on both a real
                // upright photo and its flip). The 90/270 axis survives that framing; the
                // upright-vs-upside-down call does not, and acting on it reverses the reading
                // order of the whole page. That call is settled below, by recognition, which is
                // the only signal here that knows what text is supposed to look like.
                val degrees = engine.detectPageRotation(page)
                if (degrees % 180 != 0) {
                    page = ImageOps.rotate(page, degrees)
                    derived.add(page)
                }
            }
            if (useUnwarp && engine.canUnwarp()) {
                page = engine.unwarp(page)
                derived.add(page)
            }

            var quads = engine.detect(page, DET_LIMIT)
            if (quads.isEmpty()) return@withContext OcrResult.Empty

            var visualLines = groupLines(quads)
            var scanned = visualLines.flatten()
            crops = cropLines(page, scanned)

            if (useDocOrientation && isUpsideDown(crops, pack)) {
                // Turned as a page, never per line: the quads carry the reading order, so a page
                // read bottom-up has to be re-detected on the corrected page, not patched crop by
                // crop.
                crops.forEach { it.recycle() }
                crops = emptyList()
                page = ImageOps.rotate(page, 180)
                derived.add(page)
                quads = engine.detect(page, DET_LIMIT)
                if (quads.isEmpty()) return@withContext OcrResult.Empty
                visualLines = groupLines(quads)
                scanned = visualLines.flatten()
                crops = cropLines(page, scanned)
            }

            if (useLineOrientation) {
                val flipped = engine.detectFlippedLines(crops)
                crops = crops.mapIndexed { i, crop ->
                    if (flipped[i]) ImageOps.rotate180(crop).also { crop.recycle() } else crop
                }
            }

            var recognized = engine.recognizeWithLatin(crops, pack)
            recognized = preferBundledScript(crops, pack, recognized)
            crops.forEach { it.recycle() }
            crops = emptyList()

            // Direction is a property of the text, not of the installed pack: an English
            // table must not reverse just because the Arabic pack is selected.
            val order = rtlAwareOrder(visualLines, recognized)
            val ordered = order.map { scanned[it] }
            val lines = order.map { recognized[it] }

            val kept = lines.indices.filter {
                lines[it].text.isNotBlank() && lines[it].confidence >= MIN_CONFIDENCE
            }
            if (kept.isEmpty()) return@withContext OcrResult.Empty

            val markdown = if (settings.getBoolean(SettingsKeys.PADDLE_STRUCTURE).first()) {
                structuredMarkdown(page, ordered, kept, lines)
            } else {
                null
            }

            val blocks = kept.map { i ->
                val q = ordered[i]
                TextBlockData(
                    text = lines[i].text,
                    boundingBoxLeft = q.minX.toInt(),
                    boundingBoxTop = q.minY.toInt(),
                    boundingBoxRight = q.maxX.toInt(),
                    boundingBoxBottom = q.maxY.toInt(),
                    confidence = lines[i].confidence
                )
            }
            OcrResult.Success(
                text = groupIntoLines(ordered, kept, lines),
                blocks = blocks,
                confidence = kept.map { lines[it].confidence }.average().toFloat(),
                source = OcrSource.PADDLE,
                markdown = markdown
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Paddle recognition failed", e)
            OcrResult.Error(e.message ?: "Recognition failed")
        } finally {
            // Never recycles [bitmap] itself: the caller owns it.
            crops.forEach { it.recycle() }
            derived.forEach { it.recycle() }
        }
    }

    suspend fun recognizeFromPdf(pdfUri: Uri, pageIndex: Int? = null): OcrResult =
        withContext(Dispatchers.IO) {
            val text = StringBuilder()
            val markdown = StringBuilder()
            val blocks = mutableListOf<TextBlockData>()
            var confidence = 0f
            var pages = 0

            suspend fun process(index: Int, bitmap: Bitmap) {
                when (val result = recognize(bitmap)) {
                    is OcrResult.Success -> {
                        if (text.isNotEmpty()) text.append("\n\n--- Page ${index + 1} ---\n\n")
                        text.append(result.text)
                        result.markdown?.let { page ->
                            if (markdown.isNotEmpty()) markdown.append("\n\n---\n\n")
                            markdown.append(page.trimEnd())
                        }
                        blocks.addAll(result.blocks.map { it.copy(page = index + 1) })
                        confidence += result.confidence
                        pages++
                    }
                    is OcrResult.Error -> Log.w(TAG, "Page $index: ${result.message}")
                    OcrResult.Empty -> Unit
                }
                bitmap.recycle()
            }

            if (pageIndex != null) {
                val bitmap = pdfRendererHelper.renderPage(pdfUri, pageIndex)
                    ?: return@withContext OcrResult.Error("Failed to render PDF")
                process(pageIndex, bitmap)
            } else {
                val opened = pdfRendererHelper.forEachPage(pdfUri) { i, bmp -> process(i, bmp) }
                if (!opened) return@withContext OcrResult.Error("Failed to render PDF")
            }

            if (text.isEmpty()) {
                OcrResult.Empty
            } else {
                OcrResult.Success(
                    text = text.toString(),
                    blocks = blocks,
                    confidence = if (pages > 0) confidence / pages else 0f,
                    source = OcrSource.PADDLE,
                    markdown = markdown.toString().ifBlank { null }
                )
            }
        }

    private fun cropLines(page: Bitmap, ordered: List<Quad>): List<Bitmap> {
        val pixels = ImageOps.Page(page)
        return ordered.map { ImageOps.cropQuad(pixels, it) }
    }

    /**
     * Whether the page reads better turned over. Recognition decides it, not the orientation
     * classifier: only the recogniser knows what text is supposed to look like, and it scores an
     * upside-down page roughly a third of an upright one. Costs two batches on a sample of the
     * widest lines, and needs a clear margin to act, so an ambiguous page stays as it was shot.
     */
    @VisibleForTesting
    internal fun isUpsideDown(crops: List<Bitmap>, pack: ScriptPack): Boolean {
        val sample = crops
            .sortedByDescending { it.width.toFloat() / it.height }
            .take(ORIENTATION_SAMPLE)
        if (sample.isEmpty()) return false

        val flipped = sample.map { ImageOps.rotate180(it) }
        try {
            // The selected pack may be the wrong script for this page, which would leave both
            // orientations scoring nothing; the other bundled packs then get to cast the vote.
            val candidates = listOf(pack) + ScriptPack.entries.filter { it.isBundled && it != pack }
            var upright = 0f
            var upsideDown = 0f
            for (candidate in candidates) {
                upright = maxOf(upright, orientationScore(engine.recognize(sample, candidate)))
                upsideDown = maxOf(upsideDown, orientationScore(engine.recognize(flipped, candidate)))
                if (maxOf(upright, upsideDown) >= ORIENTATION_MIN_SCORE) break
            }
            if (maxOf(upright, upsideDown) < ORIENTATION_MIN_SCORE) return false

            val flip = upsideDown > upright * FLIP_MARGIN
            if (flip) Log.d(TAG, "Page is upside down ($upsideDown vs $upright); rotating 180")
            return flip
        } finally {
            flipped.forEach { it.recycle() }
        }
    }

    /** Confident characters per line: a garbled read is both shorter and less certain. */
    private fun orientationScore(lines: List<RecLine>): Float {
        if (lines.isEmpty()) return 0f
        val score = lines.sumOf { (it.confidence * it.text.trim().length).toDouble() }
        return (score / lines.size).toFloat()
    }

    /**
     * The chosen pack can misread a whole page in the wrong alphabet: the universal model turns an
     * Arabic scan into confident Latin or CJK nonsense that a line count cannot tell from a real
     * Latin page. So unless the primary is already a dense Latin read, the other bundled packs get
     * a turn, and the strongest right-to-left read wins — recovering the Arabic page while a real
     * Latin page keeps its own read, since no alternative clears the RTL bar. Both bundled packs
     * are on-device, so this costs a second pass, never a download.
     */
    private fun preferBundledScript(
        crops: List<Bitmap>,
        used: ScriptPack,
        primary: List<RecLine>
    ): List<RecLine> {
        if (crops.isEmpty() || isDenseLatinRead(primary, crops.size)) return primary
        var best = primary
        for (pack in ScriptPack.entries.filter { it.isBundled && it != used }) {
            val candidate = engine.recognizeWithLatin(crops, pack)
            if (isBetterScriptRead(best, candidate)) {
                best = candidate
                Log.d(TAG, "Script switch: ${used.id} -> ${pack.id}")
            }
        }
        return best
    }

    /**
     * The PP-StructureV3 subset that fits on a phone: layout regions from PP-DocLayout-S,
     * table grids from SLANet when it's installed. A failure here only costs the Markdown,
     * never the scan, so it degrades to null rather than throwing.
     */
    private fun structuredMarkdown(
        page: Bitmap,
        quads: List<Quad>,
        kept: List<Int>,
        lines: List<RecLine>
    ): String? = runCatching {
        val regions = engine.detectLayout(page)
        if (regions.isEmpty()) return null

        val positioned = kept.map { i ->
            PositionedLine(text = lines[i].text, quad = quads[i], confidence = lines[i].confidence)
        }

        val tables = if (engine.canRecognizeTables()) {
            regions.filter { it.label == LayoutLabel.TABLE }.associateWith { region ->
                cropRegion(page, region)?.let { crop ->
                    try {
                        // Cell boxes come back in crop space; matching runs in page space,
                        // offset by the clamped crop origin, not the raw region box.
                        val structure = engine.recognizeTable(crop.bitmap)
                        structure.copy(
                            cells = structure.cells.map { cell ->
                                cell.copy(
                                    left = cell.left + crop.left,
                                    top = cell.top + crop.top,
                                    right = cell.right + crop.left,
                                    bottom = cell.bottom + crop.top
                                )
                            }
                        )
                    } finally {
                        // createBitmap returns the page itself for a full-page region.
                        if (crop.bitmap !== page) crop.bitmap.recycle()
                    }
                }
            }
        } else {
            emptyMap()
        }

        DocumentStructure.markdown(regions, positioned) { tables[it] }
            .takeIf { it.isNotBlank() }
    }.onFailure { Log.w(TAG, "Structure analysis failed; keeping plain text", it) }.getOrNull()

    private class RegionCrop(val bitmap: Bitmap, val left: Float, val top: Float)

    private fun cropRegion(page: Bitmap, region: LayoutRegion): RegionCrop? {
        val left = region.left.toInt().coerceIn(0, page.width - 1)
        val top = region.top.toInt().coerceIn(0, page.height - 1)
        val right = region.right.toInt().coerceIn(left + 1, page.width)
        val bottom = region.bottom.toInt().coerceIn(top + 1, page.height)
        val width = right - left
        val height = bottom - top
        if (width < MIN_TABLE_SIDE || height < MIN_TABLE_SIDE) return null
        val crop = Bitmap.createBitmap(page, left, top, width, height)
        return RegionCrop(crop, left.toFloat(), top.toFloat())
    }

    /** Group quads into visual lines, top-down, each read left-to-right for now. */
    private fun groupLines(quads: List<Quad>): List<List<Quad>> {
        val sorted = quads.sortedBy { it.minY }
        val lines = mutableListOf<MutableList<Quad>>()

        for (q in sorted) {
            val centerY = (q.minY + q.maxY) / 2f
            val height = q.maxY - q.minY
            val line = lines.lastOrNull()?.takeIf { current ->
                val ref = current.first()
                val refCenter = (ref.minY + ref.maxY) / 2f
                abs(centerY - refCenter) < maxOf(height, ref.maxY - ref.minY) * 0.6f
            }
            if (line == null) lines.add(mutableListOf(q)) else line.add(q)
        }

        return lines.map { line -> line.sortedBy { it.minX } }
    }

    /**
     * Flat indices into the scanned quads, with the boxes of any line whose recognized text
     * is predominantly RTL reversed. Direction has to be decided here, after recognition:
     * geometry alone cannot tell an Arabic line from an English one.
     */
    private fun rtlAwareOrder(
        visualLines: List<List<Quad>>,
        recognized: List<RecLine>
    ): List<Int> {
        val order = mutableListOf<Int>()
        var start = 0
        for (line in visualLines) {
            val indices = (start until start + line.size).toList()
            val text = indices.joinToString("") { recognized[it].text }
            order.addAll(if (RtlText.isRtlDominant(text)) indices.reversed() else indices)
            start += line.size
        }
        return order
    }

    private fun groupIntoLines(
        quads: List<Quad>,
        kept: List<Int>,
        lines: List<RecLine>
    ): String {
        val sb = StringBuilder()
        var previous: Quad? = null
        for (i in kept) {
            val q = quads[i]
            val prev = previous
            if (prev != null) {
                val sameLine = abs(
                    (q.minY + q.maxY) / 2f - (prev.minY + prev.maxY) / 2f
                ) < (prev.maxY - prev.minY) * 0.6f
                sb.append(if (sameLine) " " else "\n")
            }
            sb.append(lines[i].text)
            previous = q
        }
        return sb.toString()
    }
}

/** Confident characters in a read: a garbled or wrong-script line is both shorter and less certain. */
internal fun recognizedChars(lines: List<RecLine>): Int =
    lines.sumOf { if (it.confidence >= MIN_CONFIDENCE) it.text.trim().length else 0 }

/** A dense confident read is a genuine Latin page; the universal pack has nothing better to offer. */
internal fun isDenseLatinRead(primary: List<RecLine>, detectedLines: Int): Boolean =
    recognizedChars(primary) >= DENSE_CHARS_PER_LINE * detectedLines

/**
 * Whether [candidate] should replace [current]: it must recover more confident text *and* be
 * substantially right-to-left. The extra-text test alone would flip a bilingual card onto the
 * Arabic pack; the RTL test is the guard a real Latin page can never clear.
 */
internal fun isBetterScriptRead(current: List<RecLine>, candidate: List<RecLine>): Boolean =
    recognizedChars(candidate) > recognizedChars(current) &&
        RtlText.rtlFraction(candidate.joinToString("") { it.text }) >= MIN_RTL_FRACTION
