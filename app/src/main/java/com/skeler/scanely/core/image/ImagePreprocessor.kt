package com.skeler.scanely.core.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log

private const val TAG = "ImagePreprocessor"

object ImagePreprocessor {

    fun enhanceContrast(bitmap: Bitmap, contrast: Float = 1.4f): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        
        val scale = contrast
        val translate = (1.0f - scale) / 2.0f * 255f
        
        val colorMatrix = ColorMatrix(floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        Log.d(TAG, "Applied contrast enhancement: $contrast")
        return result
    }

    fun toGrayscale(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        
        val colorMatrix = ColorMatrix().apply {
            setSaturation(0f)
        }
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        return result
    }

    fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        
        val matrix = Matrix().apply {
            postRotate(degrees, bitmap.width / 2f, bitmap.height / 2f)
        }
        
        return Bitmap.createBitmap(
            bitmap, 0, 0, 
            bitmap.width, bitmap.height, 
            matrix, true
        )
    }

    fun scaleToMinDimension(bitmap: Bitmap, minDimension: Int = 1024): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width >= minDimension && height >= minDimension) {
            return bitmap
        }
        
        val scale = minDimension.toFloat() / minOf(width, height)
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        
        Log.d(TAG, "Scaling image from ${width}x${height} to ${newWidth}x${newHeight}")
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    fun adjustBrightness(bitmap: Bitmap, brightness: Float = 20f): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        
        val colorMatrix = ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, brightness,
            0f, 1f, 0f, 0f, brightness,
            0f, 0f, 1f, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        ))
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        return result
    }

    fun preprocessForOcr(bitmap: Bitmap): Bitmap {
        var processed = scaleToMinDimension(bitmap, 1024)
        processed = enhanceContrast(processed, 1.4f)
        processed = adjustBrightness(processed, 15f)
        return processed
    }

    fun preprocessForBarcode(bitmap: Bitmap): Bitmap {
        var processed = toGrayscale(bitmap)
        processed = enhanceContrast(processed, 1.6f)
        return processed
    }

    /** GMS-less fallback: contrast/scale only (no edge crop). */
    fun preprocessForDocument(bitmap: Bitmap): Bitmap {
        var processed = scaleToMinDimension(bitmap, 1400)
        processed = enhanceContrast(processed, 1.25f)
        processed = adjustBrightness(processed, 10f)
        return processed
    }
}
