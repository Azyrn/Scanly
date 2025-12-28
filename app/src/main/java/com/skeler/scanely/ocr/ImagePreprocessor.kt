package com.skeler.scanely.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Canvas
import android.net.Uri
import android.util.Log
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

private const val TAG = "ImagePreprocessor"

/**
 * Image preprocessing pipeline for OCR optimization.
 * Applies resizing, grayscale conversion, contrast enhancement,
 * and optional denoising to improve Tesseract recognition accuracy.
 */
object ImagePreprocessor {
    
    // Maximum dimension for OCR processing (balances quality vs performance)
    private const val MAX_DIMENSION = 2048
    private const val MIN_DIMENSION = 800
    
    /**
     * Full preprocessing pipeline for OCR.
     * 
     * @param context Application context
     * @param imageUri Source image URI
     * @return Preprocessed bitmap ready for OCR, or null on failure
     */
    suspend fun preprocess(context: Context, imageUri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(imageUri)
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            if (original == null) {
                Log.e(TAG, "Failed to decode image from URI: $imageUri")
                return null
            }
            
            // Pipeline: Resize -> Grayscale -> Contrast
            val resized = resizeForOcr(original)
            val grayscale = toGrayscale(resized)
            val enhanced = enhanceContrast(grayscale)
            
            // Recycle intermediates if different objects
            if (resized !== original) original.recycle()
            if (grayscale !== resized) resized.recycle()
            
            enhanced
        } catch (e: Exception) {
            Log.e(TAG, "Preprocessing failed", e)
            null
        }
    }
    
    /**
     * Preprocess from existing bitmap.
     */
    fun preprocess(bitmap: Bitmap): Bitmap {
        val resized = resizeForOcr(bitmap)
        val grayscale = toGrayscale(resized)
        val enhanced = enhanceContrast(grayscale)
        
        if (grayscale !== resized && resized !== bitmap) resized.recycle()
        if (enhanced !== grayscale) grayscale.recycle()
        
        return enhanced
    }
    
    /**
     * Resize image to optimal dimensions for OCR.
     * Maintains aspect ratio while ensuring dimensions are within bounds.
     */
    private fun resizeForOcr(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        // Skip if already within bounds
        if (width <= MAX_DIMENSION && height <= MAX_DIMENSION &&
            width >= MIN_DIMENSION && height >= MIN_DIMENSION) {
            return bitmap
        }
        
        val scaleFactor = when {
            // Scale down if too large
            max(width, height) > MAX_DIMENSION -> {
                MAX_DIMENSION.toFloat() / max(width, height)
            }
            // Scale up if too small (improves OCR on tiny text)
            min(width, height) < MIN_DIMENSION -> {
                MIN_DIMENSION.toFloat() / min(width, height)
            }
            else -> 1f
        }
        
        val newWidth = (width * scaleFactor).toInt()
        val newHeight = (height * scaleFactor).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    /**
     * Convert image to grayscale for better OCR accuracy.
     */
    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val grayscale = Bitmap.createBitmap(
            bitmap.width,
            bitmap.height,
            Bitmap.Config.ARGB_8888
        )
        
        val canvas = Canvas(grayscale)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                setSaturation(0f)
            })
        }
        
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return grayscale
    }
    
    /**
     * Enhance contrast to make text more distinguishable from background.
     */
    private fun enhanceContrast(bitmap: Bitmap): Bitmap {
        val enhanced = Bitmap.createBitmap(
            bitmap.width,
            bitmap.height,
            Bitmap.Config.ARGB_8888
        )
        
        val canvas = Canvas(enhanced)
        
        // Contrast enhancement matrix
        // Increases difference between light and dark pixels
        val contrast = 1.3f // 1.0 = no change, >1.0 = more contrast
        val translate = (-.5f * contrast + .5f) * 255f
        
        val colorMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return enhanced
    }
}
