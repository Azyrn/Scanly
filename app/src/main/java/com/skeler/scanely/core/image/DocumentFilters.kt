package com.skeler.scanely.core.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

/**
 * Quick enhancement filters applied on top of the ML Kit scanned pages so the
 * final scan "looks like it came from a professional scanner". These run on a
 * background dispatcher (see DocumentScanViewModel) and never mutate the source
 * bitmap.
 */
enum class ScanFilter(val label: String) {
    /** The scanner's own cleaned output, untouched. */
    ORIGINAL("Original"),

    /** Balanced auto contrast + brightness lift for everyday readability. */
    AUTO("Auto"),

    /** Vivid, punchy colour — great for coloured forms and receipts. */
    MAGIC_COLOR("Magic Color"),

    /** High-contrast pure black & white, ideal for text & printing. */
    BLACK_WHITE("Black & White"),

    /** Neutral greyscale. */
    GRAYSCALE("Grayscale")
}

/**
 * Stateless filter engine. Colour operations are done with [ColorMatrix] for
 * speed; Black & White additionally binarises via a global luminance threshold
 * derived from the image itself (a lightweight Otsu-style mean).
 */
object DocumentFilters {

    fun apply(source: Bitmap, filter: ScanFilter): Bitmap = when (filter) {
        ScanFilter.ORIGINAL -> source.copy(source.config ?: Bitmap.Config.ARGB_8888, false)
        ScanFilter.AUTO -> applyMatrix(source, autoMatrix())
        ScanFilter.MAGIC_COLOR -> applyMatrix(source, magicColorMatrix())
        ScanFilter.GRAYSCALE -> applyMatrix(source, grayscaleMatrix())
        ScanFilter.BLACK_WHITE -> binarize(source)
    }

    private fun applyMatrix(source: Bitmap, matrix: ColorMatrix): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return result
    }

    /** Gentle contrast (1.18x) with a small brightness lift. */
    private fun autoMatrix(): ColorMatrix {
        val scale = 1.18f
        val translate = (1f - scale) / 2f * 255f + 8f
        return ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    /** Saturation boost stacked with a firm contrast curve for vivid output. */
    private fun magicColorMatrix(): ColorMatrix {
        val saturation = ColorMatrix().apply { setSaturation(1.35f) }
        val scale = 1.28f
        val translate = (1f - scale) / 2f * 255f + 6f
        val contrast = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        return ColorMatrix().apply {
            postConcat(saturation)
            postConcat(contrast)
        }
    }

    private fun grayscaleMatrix(): ColorMatrix = ColorMatrix().apply { setSaturation(0f) }

    /**
     * Convert to pure black & white using a global luminance threshold. The
     * threshold is the mean luminance of a down-sampled scan of the pixels, which
     * adapts to lighting without the cost of a full per-pixel adaptive pass.
     */
    private fun binarize(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        // Estimate threshold from a sparse sample (every ~16th pixel).
        var sum = 0L
        var count = 0
        val step = maxOf(1, pixels.size / 4096)
        var i = 0
        while (i < pixels.size) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            sum += (r * 30 + g * 59 + b * 11) / 100
            count++
            i += step
        }
        val threshold = if (count == 0) 128 else (sum / count).toInt()

        for (j in pixels.indices) {
            val p = pixels[j]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val lum = (r * 30 + g * 59 + b * 11) / 100
            val v = if (lum >= threshold) 0xFF else 0x00
            pixels[j] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }
}
