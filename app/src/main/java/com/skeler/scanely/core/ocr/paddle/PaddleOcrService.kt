package com.skeler.scanely.core.ocr.paddle

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
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
                val degrees = engine.detectPageRotation(page)
                if (degrees != 0) {
                    page = ImageOps.rotate(page, degrees)
                    derived.add(page)
                }
            }
            if (useUnwarp && engine.canUnwarp()) {
                page = engine.unwarp(page)
                derived.add(page)
            }

            val quads = engine.detect(page, DET_LIMIT)
            if (quads.isEmpty()) return@withContext OcrResult.Empty

            val visualLines = groupLines(quads)
            val scanned = visualLines.flatten()
            crops = scanned.map { ImageOps.cropQuad(page, it) }

            if (useLineOrientation) {
                val flipped = engine.detectFlippedLines(crops)
                crops = crops.mapIndexed { i, crop ->
                    if (flipped[i]) ImageOps.rotate180(crop).also { crop.recycle() } else crop
                }
            }

            val recognized = engine.recognize(crops, pack)
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
