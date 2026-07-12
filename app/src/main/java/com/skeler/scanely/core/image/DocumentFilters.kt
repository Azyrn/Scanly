package com.skeler.scanely.core.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

enum class ScanFilter(val label: String) {
    ORIGINAL("Original"),
    AUTO("Auto"),
    MAGIC_COLOR("Magic Color"),
    BLACK_WHITE("Black & White"),
    GRAYSCALE("Grayscale")
}

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

    // Local adaptive threshold: a global one turns uneven lighting/edge shadows into
    // black bands; a local mean tracks lighting while preserving text and fine lines.
    private fun binarize(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        // Pages max 2200px/side keeps this luminance table under Int.MAX_VALUE; O(1) local-mean lookups.
        val stride = width + 1
        val integral = IntArray(stride * (height + 1))
        for (y in 0 until height) {
            var rowSum = 0
            val integralRow = (y + 1) * stride
            val previousRow = y * stride
            val pixelRow = y * width
            for (x in 0 until width) {
                val p = pixels[pixelRow + x]
                val luminance = ((p shr 16 and 0xFF) * 30 +
                    (p shr 8 and 0xFF) * 59 +
                    (p and 0xFF) * 11) / 100
                rowSum += luminance
                integral[integralRow + x + 1] = integral[previousRow + x + 1] + rowSum
            }
        }

        val radius = (minOf(width, height) / 18).coerceIn(24, 96)
        for (y in 0 until height) {
            val top = (y - radius).coerceAtLeast(0)
            val bottom = (y + radius + 1).coerceAtMost(height)
            for (x in 0 until width) {
                val left = (x - radius).coerceAtLeast(0)
                val right = (x + radius + 1).coerceAtMost(width)
                val area = (right - left) * (bottom - top)
                val localMean = (
                    integral[bottom * stride + right] -
                        integral[top * stride + right] -
                        integral[bottom * stride + left] +
                        integral[top * stride + left]
                    ) / area
                val p = pixels[y * width + x]
                val luminance = ((p shr 16 and 0xFF) * 30 +
                    (p shr 8 and 0xFF) * 59 +
                    (p and 0xFF) * 11) / 100
                val value = if (luminance >= localMean - BLACK_WHITE_BIAS) 0xFF else 0x00
                pixels[y * width + x] = (0xFF shl 24) or (value shl 16) or (value shl 8) or value
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    private const val BLACK_WHITE_BIAS = 16
}
