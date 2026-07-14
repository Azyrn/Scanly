package com.skeler.scanely.core.ocr.paddle

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import androidx.annotation.VisibleForTesting
import androidx.core.graphics.scale
import java.nio.FloatBuffer
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.read
import kotlin.math.abs
import kotlin.math.exp

private const val REC_HEIGHT = 48

// Wide enough that a dense full-width line at height 48 keeps its aspect ratio;
// clamping below that squashes long lines and misreads them.
private const val REC_MAX_WIDTH = 3200
private const val REC_BATCH = 6
private const val UNWARP_MAX_SIDE = 1024
private const val LAYOUT_SIDE = 480
private const val LAYOUT_MIN_SCORE = 0.4f
private const val TABLE_SIDE = 488
private const val ORI_WIDTH = 160
private const val ORI_HEIGHT = 80
private const val ORI_MIN_ASPECT = 2.5f
private const val ORI_MIN_CONFIDENCE = 0.8f

// A script line holding a character this unsure, or a Latin one at all, may hide a Latin word.
private const val LATIN_SUSPECT_CONFIDENCE = 0.9f

/** ONNX sessions for the PP-OCR graphs. Sessions are lazy and reusable across scans. */
@Singleton
class PaddleOcrEngine @Inject constructor(
    private val store: PaddleModelStore
) {

    private val env: OrtEnvironment get() = OrtEnvironment.getEnvironment()

    // XNNPACK and NNAPI both abort natively on these graphs; plain CPU is the safe EP.
    // Above 4 threads the little cores drag the batch out (~2x slower on big.LITTLE).
    private val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)

    private val options: OrtSession.SessionOptions
        get() = OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setIntraOpNumThreads(threads)
        }

    private val sessions = HashMap<String, OrtSession>()

    // Inference holds the read lock; close() needs the write lock, so it can never
    // free a session while native code is running on it. If close() arrives mid-run
    // it defers, and the last finishing inference performs it.
    private val sessionLock = ReentrantReadWriteLock()

    @Volatile
    private var closePending = false

    private val recCharsets = HashMap<ScriptPack, List<String>>()

    private val dbPostProcessor = DbPostProcessor()

    private val detector get() = session("ocr/det_v6_small.onnx")
    private val textLineCls get() = session("ocr/cls_textline.onnx")
    private val docOriCls get() = session("ocr/cls_docori.onnx")
    private val layoutDet get() = session("ocr/layout_s.onnx")

    /** Downloaded, not bundled. */
    private val unwarper: OrtSession
        @Synchronized get() = sessions.getOrPut("uvdoc") {
            env.createSession(store.uvdocModel(), options)
        }

    /** Downloaded, not bundled. */
    private val tableRec: OrtSession
        @Synchronized get() = sessions.getOrPut("table") {
            env.createSession(store.tableModel(), options)
        }

    private val tableVocab: List<String> by lazy {
        TableDecoder.vocabulary(
            store.asset("ocr/dict_table.txt")
                .decodeToString()
                .split("\n")
                .dropLastWhile { it.isEmpty() }
        )
    }

    fun canUnwarp(): Boolean = store.hasUvdoc()

    fun canRecognizeTables(): Boolean = store.hasTable()

    @Synchronized
    private fun session(asset: String): OrtSession = sessions.getOrPut(asset) {
        env.createSession(store.asset(asset), options)
    }

    // One session per pack, kept until close(). Closing the old session on a pack switch
    // would free native memory another thread may still be running inference on: the
    // read lock is shared, so it does not order concurrent recognize() calls.
    @Synchronized
    private fun recognizer(pack: ScriptPack): Pair<OrtSession, List<String>> {
        val effective = if (pack.isBundled || store.isInstalled(pack)) pack else ScriptPack.DEFAULT
        val session = sessions.getOrPut("rec/${effective.id}") {
            val (bytes, charset) = store.recModel(effective)
            recCharsets[effective] = charset
            env.createSession(bytes, options)
        }
        return session to recCharsets.getValue(effective)
    }

    private inline fun <T> withSessions(block: () -> T): T {
        try {
            return sessionLock.read(block)
        } finally {
            if (closePending) close()
        }
    }

    /** DBNet text detection. Returns quads in [src] pixel coordinates. */
    fun detect(src: Bitmap, limitSide: Int): List<Quad> = withSessions {
        val (resized, scaleX, scaleY) = ImageOps.detResize(src, limitSide)
        val tensor = ImageOps.imagenetTensor(resized)
        val w = resized.width
        val h = resized.height
        if (resized !== src) resized.recycle()

        val prob = run(detector, tensor, longArrayOf(1, 3, h.toLong(), w.toLong())) { buf, shape ->
            val oh = shape[2].toInt()
            val ow = shape[3].toInt()
            Triple(FloatArray(oh * ow).also { buf.get(it) }, ow, oh)
        }
        dbPostProcessor.extract(prob.first, prob.second, prob.third, scaleX, scaleY)
    }

    /** CTC recognition, batched by similar width. Returns text + mean per-character confidence. */
    fun recognize(crops: List<Bitmap>, pack: ScriptPack): List<RecLine> = withSessions {
        decode(crops, pack).map { it.toLogical() }
    }

    /**
     * Recognition for a script that is not Latin, with the Latin words inside each line repaired.
     * The same crops are read a second time by the universal model and the two readings merged by
     * position ([ScriptMerge]), so an Arabic line keeps its Arabic *and* its "Original"/"GPT-5".
     * Costs one extra recognition pass over the crops that need it.
     */
    fun recognizeWithLatin(crops: List<Bitmap>, pack: ScriptPack): List<RecLine> = withSessions {
        val primary = decode(crops, pack)
        if (pack == ScriptPack.DEFAULT) return@withSessions primary.map { it.toLogical() }

        // A page of nothing but confident Arabic has no Latin hiding in it, and is spared the pass
        // entirely. But once it is worth running, it runs over *every* crop: a batch is padded to
        // its widest member, and padding changes what the model reads, not just where. Handing the
        // second model a subset re-batches the crops and it comes back with different text.
        if (primary.none { mayHideLatin(it) }) return@withSessions primary.map { it.toLogical() }

        val latin = decode(crops, ScriptPack.DEFAULT)
        primary.mapIndexed { i, line ->
            ScriptMerge.merge(line, latin[i]).toLogical()
        }
    }

    // Keyed off the characters, not the pack: any model can emit an RTL run, and the
    // conversion is a no-op on a string that has none.
    private fun RecLine.toLogical() = copy(text = RtlText.visualToLogical(text))

    /** A confident, wholly-script line has nothing for the Latin model to add. */
    private fun mayHideLatin(line: RecLine): Boolean =
        line.chars.any { it.text.isNotBlank() && (it.confidence < LATIN_SUSPECT_CONFIDENCE || it.text.all { c -> c.code < 0x0590 }) }

    private fun decode(crops: List<Bitmap>, pack: ScriptPack): List<RecLine> {
        if (crops.isEmpty()) return emptyList()
        val (session, charset) = recognizer(pack)

        val order = crops.indices.sortedBy { crops[it].width.toFloat() / crops[it].height }
        val results = arrayOfNulls<RecLine>(crops.size)

        for (chunk in order.chunked(REC_BATCH)) {
            val batch = chunk.map { crops[it] }
            val (tensor, width) = ImageOps.recBatchTensor(batch, REC_HEIGHT, REC_MAX_WIDTH)
            val shape = longArrayOf(batch.size.toLong(), 3, REC_HEIGHT.toLong(), width.toLong())

            // A padded crop fills only part of the batch tensor. Without this the character
            // positions are fractions of the widest crop in the batch, and two models batched
            // differently — as they are whenever only some lines get a second pass — report
            // different positions for the same character, so nothing lines up.
            val fill = ImageOps.recWidths(batch, REC_HEIGHT, REC_MAX_WIDTH)
                .map { it.toFloat() / width }

            val decoded = run(session, tensor, shape) { buf, outShape ->
                val steps = outShape[1].toInt()
                val classes = outShape[2].toInt()
                val flat = FloatArray(batch.size * steps * classes).also { buf.get(it) }
                CtcDecoder.decodeBatch(flat, batch.size, steps, classes, charset, fill)
            }
            chunk.forEachIndexed { i, srcIndex -> results[srcIndex] = decoded[i] }
        }
        return results.map { it ?: RecLine("", 0f) }
    }

    /**
     * True where the line is upside down (PP-LCNet textline orientation).
     *
     * The crop is squashed to 160x80 without preserving aspect, which is how the model is
     * trained. Letterboxing it onto a padded canvas instead put the input off-distribution
     * and the two classes came back near 0.5 — upright Arabic lines were "corrected" into
     * garbage. A short crop (a table cell, a page number) is still left alone: 180-degree
     * rotation is near-symmetric for digits, so the answer there is close to a coin toss.
     * A whole page that is upside down is [detectPageRotation]'s job, not this one.
     */
    fun detectFlippedLines(crops: List<Bitmap>): BooleanArray = withSessions {
        if (crops.isEmpty()) return@withSessions BooleanArray(0)
        val flags = BooleanArray(crops.size)
        crops.chunked(REC_BATCH).forEachIndexed { chunkIndex, chunk ->
            val scaled = chunk.map { it.scale(ORI_WIDTH, ORI_HEIGHT) }
            val tensor = concatTensors(scaled.map { ImageOps.imagenetTensor(it) })
            scaled.forEachIndexed { i, s -> if (s !== chunk[i]) s.recycle() }

            val probs = run(
                textLineCls,
                tensor,
                longArrayOf(chunk.size.toLong(), 3, ORI_HEIGHT.toLong(), ORI_WIDTH.toLong())
            ) { buf, _ -> FloatArray(chunk.size * 2).also { buf.get(it) } }

            for (i in chunk.indices) {
                val crop = chunk[i]
                val slim = crop.width >= crop.height * ORI_MIN_ASPECT
                val flipped = probs[i * 2 + 1]
                val upright = probs[i * 2]
                flags[chunkIndex * REC_BATCH + i] =
                    slim && flipped > upright && flipped >= ORI_MIN_CONFIDENCE
            }
        }
        flags
    }

    /** Page rotation in degrees (0/90/180/270). */
    fun detectPageRotation(src: Bitmap): Int = withSessions {
        val square = ImageOps.resizeShortCenterCrop(src, 256, 224)
        val tensor = ImageOps.imagenetTensor(square)
        square.recycle()
        val probs = run(docOriCls, tensor, longArrayOf(1, 3, 224, 224)) { buf, _ ->
            FloatArray(4).also { buf.get(it) }
        }
        val best = probs.indices.maxBy { probs[it] }
        best * 90
    }

    /** UVDoc dewarping of curved/folded pages. Output matches the input size. */
    fun unwarp(src: Bitmap): Bitmap = withSessions {
        val ratio = minOf(UNWARP_MAX_SIDE.toFloat() / maxOf(src.width, src.height), 1f)
        val w = (src.width * ratio).toInt().coerceAtLeast(64)
        val h = (src.height * ratio).toInt().coerceAtLeast(64)
        val input = if (ratio < 1f) src.scale(w, h) else src

        val px = IntArray(w * h)
        input.getPixels(px, 0, w, 0, 0, w, h)
        if (input !== src) input.recycle()

        val plane = w * h
        val data = FloatArray(3 * plane)
        for (i in 0 until plane) {
            val p = px[i]
            data[i] = (p shr 16 and 0xFF) / 255f
            data[plane + i] = (p shr 8 and 0xFF) / 255f
            data[2 * plane + i] = (p and 0xFF) / 255f
        }

        val out = run(
            unwarper,
            FloatBuffer.wrap(data),
            longArrayOf(1, 3, h.toLong(), w.toLong()),
            "image"
        ) { buf, shape ->
            val oh = shape[2].toInt()
            val ow = shape[3].toInt()
            val flat = FloatArray(3 * oh * ow).also { buf.get(it) }
            val oPlane = oh * ow
            val pixels = IntArray(oPlane)
            for (i in 0 until oPlane) {
                val r = (flat[i] * 255f).toInt().coerceIn(0, 255)
                val g = (flat[oPlane + i] * 255f).toInt().coerceIn(0, 255)
                val b = (flat[2 * oPlane + i] * 255f).toInt().coerceIn(0, 255)
                pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            Bitmap.createBitmap(pixels, ow, oh, Bitmap.Config.ARGB_8888)
        }
        out
    }

    /** PP-DocLayout-S. Returns regions in [src] pixel coordinates. */
    fun detectLayout(src: Bitmap): List<LayoutRegion> = withSessions {
        val square = src.scale(LAYOUT_SIDE, LAYOUT_SIDE)
        val image = ImageOps.imagenetTensor(square)
        // scale() returns src itself when the size already matches; never recycle the caller's page.
        if (square !== src) square.recycle()

        // The graph rescales its own boxes back to source pixels from this factor.
        val scaleFactor = FloatBuffer.wrap(
            floatArrayOf(LAYOUT_SIDE.toFloat() / src.height, LAYOUT_SIDE.toFloat() / src.width)
        )

        OnnxTensor.createTensor(
            env,
            image,
            longArrayOf(1, 3, LAYOUT_SIDE.toLong(), LAYOUT_SIDE.toLong())
        ).use { imageTensor ->
            OnnxTensor.createTensor(env, scaleFactor, longArrayOf(1, 2)).use { scaleTensor ->
                parseLayout(imageTensor, scaleTensor)
            }
        }
    }

    private fun parseLayout(image: OnnxTensor, scale: OnnxTensor): List<LayoutRegion> =
        layoutDet.run(mapOf("image" to image, "scale_factor" to scale)).use { result ->
            val output = result[0] as OnnxTensor
            val rows = output.info.shape[0].toInt()
            val flat = FloatArray(rows * 6).also { output.floatBuffer.get(it) }

            (0 until rows).mapNotNull { i ->
                val base = i * 6
                val score = flat[base + 1]
                val label = LayoutLabel.fromIndex(flat[base].toInt())
                if (label == null || score < LAYOUT_MIN_SCORE) return@mapNotNull null
                LayoutRegion(
                    label = label,
                    score = score,
                    left = flat[base + 2],
                    top = flat[base + 3],
                    right = flat[base + 4],
                    bottom = flat[base + 5]
                )
            }
        }

    /** SLANet_plus. Cell boxes come back in [crop] pixel coordinates. */
    fun recognizeTable(crop: Bitmap): TableStructure = withSessions {
        val longest = maxOf(crop.width, crop.height)
        val ratio = TABLE_SIDE.toFloat() / longest
        val w = (crop.width * ratio).toInt().coerceIn(1, TABLE_SIDE)
        val h = (crop.height * ratio).toInt().coerceIn(1, TABLE_SIDE)

        val resized = crop.scale(w, h)
        val tensor = ImageOps.paddedImagenetTensor(resized, TABLE_SIDE)
        if (resized !== crop) resized.recycle()

        OnnxTensor.createTensor(
            env,
            tensor,
            longArrayOf(1, 3, TABLE_SIDE.toLong(), TABLE_SIDE.toLong())
        ).use { input ->
            tableRec.run(mapOf(tableRec.inputNames.first() to input)).use { result ->
                val boxOut = result[0] as OnnxTensor
                val structOut = result[1] as OnnxTensor
                val steps = structOut.info.shape[1].toInt()
                val classes = structOut.info.shape[2].toInt()

                val boxes = FloatArray(steps * 8).also { boxOut.floatBuffer.get(it) }
                val structure = FloatArray(steps * classes).also { structOut.floatBuffer.get(it) }

                // Boxes are normalised against the padded square, so one factor covers both axes.
                TableDecoder.decode(
                    structure = structure,
                    boxes = boxes,
                    steps = steps,
                    classes = classes,
                    vocab = tableVocab,
                    scale = longest.toFloat()
                )
            }
        }
    }

    private fun <T> run(
        session: OrtSession,
        input: FloatBuffer,
        shape: LongArray,
        inputName: String? = null,
        decode: (FloatBuffer, LongArray) -> T
    ): T {
        val name = inputName ?: session.inputNames.first()
        OnnxTensor.createTensor(env, input, shape).use { tensor ->
            session.run(mapOf(name to tensor)).use { result ->
                val output = result[0] as OnnxTensor
                return decode(output.floatBuffer, output.info.shape)
            }
        }
    }

    private fun concatTensors(buffers: List<FloatBuffer>): FloatBuffer {
        if (buffers.size == 1) return buffers.first()
        val total = buffers.sumOf { it.remaining() }
        val out = FloatArray(total)
        var offset = 0
        for (b in buffers) {
            val n = b.remaining()
            b.get(out, offset, n)
            offset += n
        }
        return FloatBuffer.wrap(out)
    }

    fun close() {
        val write = sessionLock.writeLock()
        if (!write.tryLock()) {
            closePending = true
            return
        }
        try {
            closePending = false
            synchronized(this) {
                sessions.values.forEach { runCatching { it.close() } }
                sessions.clear()
                recCharsets.clear()
            }
        } finally {
            write.unlock()
        }
    }

    @VisibleForTesting
    fun openSessionCount(): Int = synchronized(this) { sessions.size }
}

/**
 * One decoded character, with the position it was read at. CTC timesteps run left to right
 * across the crop, so [x] — the timestep as a fraction of the line width — is where in the line
 * this character sits. That is what lets two models that read the same crop be compared
 * position by position ([ScriptMerge]).
 */
data class RecChar(val text: String, val x: Float, val confidence: Float)

data class RecLine(
    val text: String,
    val confidence: Float,
    val chars: List<RecChar> = emptyList()
)

object CtcDecoder {

    /**
     * [fill] is the share of the batch tensor each crop actually occupies (1.0 when unpadded).
     * Character positions are reported as a fraction of the crop, not of the padded batch.
     */
    fun decodeBatch(
        logits: FloatArray,
        batch: Int,
        steps: Int,
        classes: Int,
        charset: List<String>,
        fill: List<Float> = emptyList()
    ): List<RecLine> {
        val softmaxed = isSoftmaxed(logits, classes)
        return (0 until batch).map { n ->
            val sb = StringBuilder(steps)
            val chars = ArrayList<RecChar>(steps)
            val share = fill.getOrElse(n) { 1f }.coerceIn(0.01f, 1f)
            var confSum = 0f
            var prev = -1

            for (t in 0 until steps) {
                val base = (n * steps + t) * classes
                var best = 0
                var bestScore = logits[base]
                for (c in 1 until classes) {
                    val v = logits[base + c]
                    if (v > bestScore) {
                        bestScore = v;
                        best = c
                    }
                }
                if (best != 0 && best != prev) {
                    val text = charset.getOrElse(best) { "" }
                    val confidence = if (softmaxed) {
                        bestScore.coerceIn(0f, 1f)
                    } else {
                        softmax(logits, base, classes, bestScore)
                    }
                    sb.append(text)
                    val x = ((t + 0.5f) / steps / share).coerceAtMost(1f)
                    chars.add(RecChar(text, x, confidence))
                    confSum += confidence
                }
                prev = best
            }
            RecLine(
                text = sb.toString(),
                confidence = if (chars.isEmpty()) 0f else confSum / chars.size,
                chars = chars
            )
        }
    }

    /**
     * A softmax row sums to 1; raw logit rows don't (a graph property, so one row
     * decides for the whole tensor — unlike the old per-value [0,1] range guess).
     */
    @VisibleForTesting
    fun isSoftmaxed(logits: FloatArray, classes: Int): Boolean {
        var sum = 0f
        for (c in 0 until classes) sum += logits[c]
        return abs(sum - 1f) < 0.01f
    }

    private fun softmax(logits: FloatArray, base: Int, classes: Int, bestScore: Float): Float {
        var sum = 0f
        for (c in 0 until classes) sum += exp(logits[base + c] - bestScore)
        return if (sum <= 0f) 0f else 1f / sum
    }
}
