package com.skeler.scanely.core.ocr.paddle

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.roundToInt

private val IMAGENET_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
private val IMAGENET_STD = floatArrayOf(0.229f, 0.224f, 0.225f)

// Channel values are 0..255 ints, so each normalization is a 256-entry lookup.
// Entries hold the exact float the inline expression produced — bit-identical tensors.
private fun normLut(mean: Float, std: Float) = FloatArray(256) { ((it / 255f) - mean) / std }

private val IMAGENET_LUT = Array(3) { normLut(IMAGENET_MEAN[it], IMAGENET_STD[it]) }
private val REC_LUT = FloatArray(256) { (it / 127.5f) - 1f }

// Direct buffer lets ORT read the tensor in place instead of copying the heap
// array into native memory. Filled with one bulk put: per-element puts on a
// direct buffer are ~40ns each on ART and cost more than they save.
private fun toDirect(out: FloatArray): FloatBuffer =
    ByteBuffer.allocateDirect(out.size * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .put(out)
        .apply { rewind() }

object ImageOps {

    /**
     * PaddleOCR trains with img_mode BGR (OpenCV reads) while declaring RGB-ordered
     * means. On-device A/B (PaddleChannelOrderTest, colored + low-contrast text):
     * both orders 6/6 exact, RGB meanConf 0.991 vs BGR 0.988 — no BGR win, RGB stays.
     * The var exists so that test can toggle both orders.
     */
    @Volatile
    var bgrInput = false

    /** Longest side capped, both sides snapped to /32 as DBNet requires. */
    fun detResize(src: Bitmap, limit: Int): Triple<Bitmap, Float, Float> {
        val ratio = minOf(limit.toFloat() / maxOf(src.width, src.height), 1f)
        val w = (src.width * ratio / 32f).roundToInt().coerceAtLeast(1) * 32
        val h = (src.height * ratio / 32f).roundToInt().coerceAtLeast(1) * 32
        val resized = if (w == src.width && h == src.height) src else src.scale(w, h)
        return Triple(resized, src.width.toFloat() / w, src.height.toFloat() / h)
    }

    fun imagenetTensor(bmp: Bitmap): FloatBuffer =
        chwTensor(bmp, IMAGENET_MEAN, IMAGENET_STD)

    /** NCHW float tensor, RGB, (x/255 - mean) / std. */
    fun chwTensor(bmp: Bitmap, mean: FloatArray, std: FloatArray): FloatBuffer {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val plane = w * h
        val out = FloatArray(3 * plane)
        val bgr = bgrInput
        val lut0 = normLut(mean[0], std[0])
        val lut1 = normLut(mean[1], std[1])
        val lut2 = normLut(mean[2], std[2])
        for (i in 0 until plane) {
            val p = px[i]
            val c0 = if (bgr) p and 0xFF else p shr 16 and 0xFF
            val c2 = if (bgr) p shr 16 and 0xFF else p and 0xFF
            out[i] = lut0[c0]
            out[plane + i] = lut1[p shr 8 and 0xFF]
            out[2 * plane + i] = lut2[c2]
        }
        return toDirect(out)
    }

    /**
     * SLANet tensor: [bmp] in the top-left of a [side]×[side] canvas. PaddleOCR pads
     * after normalizing, so the padding is zero in normalized space — padding the
     * bitmap with black first would feed the model (0 - mean) / std instead.
     */
    fun paddedImagenetTensor(bmp: Bitmap, side: Int): FloatBuffer {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)

        val plane = side * side
        val out = FloatArray(3 * plane)
        val bgr = bgrInput
        for (y in 0 until h) {
            for (x in 0 until w) {
                val p = px[y * w + x]
                val i = y * side + x
                val c0 = if (bgr) p and 0xFF else p shr 16 and 0xFF
                val c2 = if (bgr) p shr 16 and 0xFF else p and 0xFF
                out[i] = IMAGENET_LUT[0][c0]
                out[plane + i] = IMAGENET_LUT[1][p shr 8 and 0xFF]
                out[2 * plane + i] = IMAGENET_LUT[2][c2]
            }
        }
        return toDirect(out)
    }

    /**
     * Rec batch tensor: every crop resized to [height] keeping aspect, zero-padded
     * to the batch's widest. Normalized (x/127.5 - 1).
     */
    fun recBatchTensor(crops: List<Bitmap>, height: Int, maxWidth: Int): Pair<FloatBuffer, Int> {
        val widths = crops.map { c ->
            val w = ceil(c.width.toDouble() * height / c.height).toInt()
            w.coerceIn(16, maxWidth)
        }
        val batchW = widths.max()
        val plane = height * batchW
        val out = FloatArray(crops.size * 3 * plane)

        crops.forEachIndexed { n, crop ->
            val w = widths[n]
            val scaled = crop.scale(w, height)
            val px = IntArray(w * height)
            scaled.getPixels(px, 0, w, 0, 0, w, height)
            if (scaled !== crop) scaled.recycle()
            val base = n * 3 * plane
            val bgr = bgrInput
            for (y in 0 until height) {
                for (x in 0 until w) {
                    val p = px[y * w + x]
                    val o = y * batchW + x
                    val c0 = if (bgr) p and 0xFF else p shr 16 and 0xFF
                    val c2 = if (bgr) p shr 16 and 0xFF else p and 0xFF
                    out[base + o] = REC_LUT[c0]
                    out[base + plane + o] = REC_LUT[p shr 8 and 0xFF]
                    out[base + 2 * plane + o] = REC_LUT[c2]
                }
            }
        }
        return toDirect(out) to batchW
    }

    /** Perspective-warp a detected quad to an upright crop; rotates tall lines upright. */
    fun cropQuad(src: Bitmap, q: Quad): Bitmap {
        val wTop = hypot(q.x(1) - q.x(0), q.y(1) - q.y(0))
        val wBot = hypot(q.x(2) - q.x(3), q.y(2) - q.y(3))
        val hLeft = hypot(q.x(3) - q.x(0), q.y(3) - q.y(0))
        val hRight = hypot(q.x(2) - q.x(1), q.y(2) - q.y(1))
        val w = maxOf(wTop, wBot).roundToInt().coerceAtLeast(1)
        val h = maxOf(hLeft, hRight).roundToInt().coerceAtLeast(1)

        axisAlignedCrop(src, q, w, h)?.let { return it }

        val m = perspectiveInverse(q, w.toFloat(), h.toFloat())
        val srcW = src.width
        val srcH = src.height
        val srcPx = IntArray(srcW * srcH)
        src.getPixels(srcPx, 0, srcW, 0, 0, srcW, srcH)

        val dst = IntArray(w * h)
        for (y in 0 until h) {
            val fy = y + 0.5f
            for (x in 0 until w) {
                val fx = x + 0.5f
                val den = m[6] * fx + m[7] * fy + m[8]
                if (den == 0f) continue
                val sx = (m[0] * fx + m[1] * fy + m[2]) / den
                val sy = (m[3] * fx + m[4] * fy + m[5]) / den
                dst[y * w + x] = sampleBilinear(srcPx, srcW, srcH, sx, sy)
            }
        }

        val out = createBitmap(w, h)
        out.setPixels(dst, 0, w, 0, 0, w, h)
        if (h <= w * 1.5f) return out
        return rotate90(out).also { out.recycle() }
    }

    /** Most document lines are square-on: a native subrect copy beats per-pixel warping. */
    private fun axisAlignedCrop(src: Bitmap, q: Quad, w: Int, h: Int): Bitmap? {
        val skew = maxOf(
            abs(q.y(0) - q.y(1)), abs(q.y(3) - q.y(2)),
            abs(q.x(0) - q.x(3)), abs(q.x(1) - q.x(2))
        )
        if (skew > maxOf(1f, h * 0.04f)) return null

        val left = q.minX.roundToInt().coerceIn(0, src.width - 1)
        val top = q.minY.roundToInt().coerceIn(0, src.height - 1)
        val right = (left + w).coerceAtMost(src.width)
        val bottom = (top + h).coerceAtMost(src.height)
        if (right - left < 2 || bottom - top < 2) return null

        // createBitmap returns src itself for a full-bitmap rect; callers recycle crops.
        val sub = Bitmap.createBitmap(src, left, top, right - left, bottom - top)
        val crop = if (sub === src) {
            src.copy(src.config ?: Bitmap.Config.ARGB_8888, false)
        } else {
            sub
        }
        if (crop.height <= crop.width * 1.5f) return crop
        return rotate90(crop).also { crop.recycle() }
    }

    /** PaddleClas eval transform: scale short side to [shortSide], centre-crop [crop] square. */
    /**
     * Fits [src] into [w]x[h] keeping its aspect ratio, centred on white. Squashing a
     * crop to a fixed box instead skews narrow ones so badly the orientation classifier
     * reads them as noise.
     */
    fun letterbox(src: Bitmap, w: Int, h: Int): Bitmap {
        val ratio = minOf(w.toFloat() / src.width, h.toFloat() / src.height)
        val fw = (src.width * ratio).roundToInt().coerceIn(1, w)
        val fh = (src.height * ratio).roundToInt().coerceIn(1, h)
        val fitted = if (fw == src.width && fh == src.height) src else src.scale(fw, fh)

        val out = createBitmap(w, h)
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(fitted, ((w - fw) / 2f), ((h - fh) / 2f), null)
        }
        if (fitted !== src) fitted.recycle()
        return out
    }

    fun resizeShortCenterCrop(src: Bitmap, shortSide: Int, crop: Int): Bitmap {
        val scale = shortSide.toFloat() / minOf(src.width, src.height)
        val w = (src.width * scale).roundToInt().coerceAtLeast(shortSide)
        val h = (src.height * scale).roundToInt().coerceAtLeast(shortSide)
        val scaled = src.scale(w, h)
        val out = Bitmap.createBitmap(scaled, (w - crop) / 2, (h - crop) / 2, crop, crop)
        if (scaled !== src && scaled !== out) scaled.recycle()
        return if (out === src) src.copy(src.config ?: Bitmap.Config.ARGB_8888, false) else out
    }

    fun rotate90(bmp: Bitmap): Bitmap {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                out[(w - 1 - x) * h + y] = px[y * w + x]
            }
        }
        return createBitmap(h, w).apply { setPixels(out, 0, h, 0, 0, h, w) }
    }

    fun rotate180(bmp: Bitmap): Bitmap {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        px.reverse()
        return createBitmap(w, h).apply { setPixels(px, 0, w, 0, 0, w, h) }
    }

    /** Never recycles its input; callers own both bitmaps. */
    fun rotate(bmp: Bitmap, degrees: Int): Bitmap = when (((degrees % 360) + 360) % 360) {
        90 -> rotate90(bmp)
        180 -> rotate180(bmp)
        270 -> rotate180(bmp).let { flipped -> rotate90(flipped).also { flipped.recycle() } }
        else -> bmp
    }

    private fun sampleBilinear(px: IntArray, w: Int, h: Int, x: Float, y: Float): Int {
        val cx = x.coerceIn(0f, (w - 1).toFloat())
        val cy = y.coerceIn(0f, (h - 1).toFloat())
        val x0 = cx.toInt(); val y0 = cy.toInt()
        val x1 = minOf(x0 + 1, w - 1); val y1 = minOf(y0 + 1, h - 1)
        val fx = cx - x0; val fy = cy - y0

        val p00 = px[y0 * w + x0]; val p01 = px[y0 * w + x1]
        val p10 = px[y1 * w + x0]; val p11 = px[y1 * w + x1]

        fun ch(shift: Int): Int {
            val a = (p00 shr shift and 0xFF) * (1 - fx) + (p01 shr shift and 0xFF) * fx
            val b = (p10 shr shift and 0xFF) * (1 - fx) + (p11 shr shift and 0xFF) * fx
            return (a * (1 - fy) + b * fy).roundToInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Homography mapping dst rect -> src quad (used to inverse-sample). */
    private fun perspectiveInverse(q: Quad, w: Float, h: Float): FloatArray {
        val dst = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)
        val a = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val dx = dst[i * 2].toDouble(); val dy = dst[i * 2 + 1].toDouble()
            val sx = q.x(i).toDouble(); val sy = q.y(i).toDouble()
            a[i * 2] = doubleArrayOf(dx, dy, 1.0, 0.0, 0.0, 0.0, -dx * sx, -dy * sx, sx)
            a[i * 2 + 1] = doubleArrayOf(0.0, 0.0, 0.0, dx, dy, 1.0, -dx * sy, -dy * sy, sy)
        }
        val sol = solve8(a)
        return floatArrayOf(
            sol[0].toFloat(), sol[1].toFloat(), sol[2].toFloat(),
            sol[3].toFloat(), sol[4].toFloat(), sol[5].toFloat(),
            sol[6].toFloat(), sol[7].toFloat(), 1f
        )
    }

    /** Gauss-Jordan with partial pivoting; rows are [8][9] augmented. */
    private fun solve8(a: Array<DoubleArray>): DoubleArray {
        for (col in 0 until 8) {
            var pivot = col
            for (r in col + 1 until 8) {
                if (kotlin.math.abs(a[r][col]) > kotlin.math.abs(a[pivot][col])) pivot = r
            }
            val tmp = a[col]; a[col] = a[pivot]; a[pivot] = tmp
            val p = a[col][col]
            if (kotlin.math.abs(p) < 1e-12) continue
            for (c in col until 9) a[col][c] /= p
            for (r in 0 until 8) {
                if (r == col) continue
                val f = a[r][col]
                if (f == 0.0) continue
                for (c in col until 9) a[r][c] -= f * a[col][c]
            }
        }
        return DoubleArray(8) { a[it][8] }
    }
}
