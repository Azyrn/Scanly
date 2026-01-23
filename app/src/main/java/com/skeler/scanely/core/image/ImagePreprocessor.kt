package com.skeler.scanely.core.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log

private const val TAG = "ImagePreprocessor"

/**
 * Image preprocessing utilities for enhanced OCR and barcode detection.
 * 
 * These operations help ML Kit perform better on:
 * - Low contrast documents
 * - Tilted/rotated images
 * - Partially visible barcodes
 */
object ImagePreprocessor {

    /**
     * Apply contrast enhancement for better OCR on faded documents.
     * 
     * @param bitmap Source bitmap
     * @param contrast Contrast factor (1.0 = no change, 1.5 = 50% more contrast)
     * @return New bitmap with enhanced contrast
     */
    fun enhanceContrast(bitmap: Bitmap, contrast: Float = 1.4f): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        
        // Contrast matrix: scale colors away from 0.5 (middle gray)
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

    /**
     * Convert to grayscale for faster processing and sometimes better OCR.
     */
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

    /**
     * Rotate bitmap by given degrees.
     * Useful for correcting document orientation.
     */
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

    /**
     * Scale bitmap to target size while maintaining aspect ratio.
     * Larger images give better OCR but use more memory.
     */
    fun scaleToMinDimension(bitmap: Bitmap, minDimension: Int = 1024): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width >= minDimension && height >= minDimension) {
            return bitmap // Already large enough
        }
        
        val scale = minDimension.toFloat() / minOf(width, height)
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        
        Log.d(TAG, "Scaling image from ${width}x${height} to ${newWidth}x${newHeight}")
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Apply brightness adjustment.
     * 
     * @param brightness Value from -100 (darker) to 100 (brighter)
     */
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

    /**
     * Apply full preprocessing pipeline optimized for document OCR.
     * 
     * Pipeline:
     * 1. Scale to minimum 1024px on shortest edge
     * 2. Enhance contrast by 40%
     * 3. Slight brightness boost
     */
    fun preprocessForOcr(bitmap: Bitmap): Bitmap {
        var processed = scaleToMinDimension(bitmap, 1024)
        processed = enhanceContrast(processed, 1.4f)
        processed = adjustBrightness(processed, 15f)
        return processed
    }

    /**
     * Apply full preprocessing pipeline optimized for barcode detection.
     * 
     * Pipeline:
     * 1. Convert to grayscale (barcodes are B&W)
     * 2. High contrast enhancement
     */
    fun preprocessForBarcode(bitmap: Bitmap): Bitmap {
        var processed = toGrayscale(bitmap)
        processed = enhanceContrast(processed, 1.6f)
        return processed
    }
}
