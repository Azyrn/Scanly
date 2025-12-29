package com.skeler.scanely.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Canvas
import android.net.Uri
import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val TAG = "ImagePreprocessor"

/**
 * Image quality levels for adaptive preprocessing.
 */
enum class ImageQuality {
    HIGH,      // Clean scanned document
    MEDIUM,    // Photo with good lighting
    LOW        // Photo with poor lighting/noise
}

/**
 * Image preprocessing pipeline for OCR optimization.
 * 
 * Pipeline stages:
 * 1. Resize - Optimal dimensions for OCR (800-2048px)
 * 2. Grayscale - Remove color information
 * 3. Contrast Enhancement - Improve text visibility
 * 4. Adaptive Threshold - Binarization for text/background separation
 * 5. Denoise - Remove small artifacts
 * 
 * Each stage is applied based on detected image quality.
 */
object ImagePreprocessor {
    
    // Maximum dimension for OCR processing (balances quality vs performance)
    private const val MAX_DIMENSION = 2048
    private const val MIN_DIMENSION = 800
    
    // Thresholds for quality detection
    private const val HIGH_CONTRAST_THRESHOLD = 0.7f
    private const val LOW_NOISE_THRESHOLD = 0.1f
    
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
            
            preprocessBitmap(original, recycleInput = true)
        } catch (e: Exception) {
            Log.e(TAG, "Preprocessing failed", e)
            null
        }
    }
    
    /**
     * Preprocess from existing bitmap.
     * 
     * @param bitmap Input bitmap
     * @param recycleInput Whether to recycle the input bitmap after processing
     * @return Preprocessed bitmap
     */
    fun preprocess(bitmap: Bitmap, recycleInput: Boolean = false): Bitmap {
        return preprocessBitmap(bitmap, recycleInput)
    }
    
    /**
     * Core preprocessing pipeline.
     */
    private fun preprocessBitmap(bitmap: Bitmap, recycleInput: Boolean): Bitmap {
        val startTime = System.currentTimeMillis()
        
        // Step 1: Resize to optimal dimensions
        val resized = resizeForOcr(bitmap)
        if (recycleInput && resized !== bitmap) bitmap.recycle()
        
        // Step 2: Convert to grayscale
        val grayscale = toGrayscale(resized)
        if (grayscale !== resized) resized.recycle()
        
        // Step 3: Detect quality to determine pipeline intensity
        val quality = detectQuality(grayscale)
        Log.d(TAG, "Detected image quality: $quality")
        
        // Step 4: Enhance contrast
        val contrasted = enhanceContrast(grayscale, quality)
        if (contrasted !== grayscale) grayscale.recycle()
        
        // Step 5: Apply adaptive threshold for binarization (optional based on quality)
        val thresholded = if (quality == ImageQuality.LOW) {
            adaptiveThreshold(contrasted)
        } else {
            contrasted
        }
        if (thresholded !== contrasted) contrasted.recycle()
        
        // Step 6: Denoise if needed
        val denoised = if (quality == ImageQuality.LOW) {
            denoise(thresholded)
        } else {
            thresholded
        }
        if (denoised !== thresholded) thresholded.recycle()
        
        val processingTime = System.currentTimeMillis() - startTime
        Log.d(TAG, "Preprocessing completed in ${processingTime}ms")
        
        return denoised
    }
    
    /**
     * Detect image quality based on contrast and noise levels.
     */
    fun detectQuality(bitmap: Bitmap): ImageQuality {
        // Sample pixels to estimate quality (use center region)
        val sampleSize = 100
        val startX = (bitmap.width - sampleSize) / 2
        val startY = (bitmap.height - sampleSize) / 2
        
        if (startX < 0 || startY < 0) {
            return ImageQuality.MEDIUM // Too small to analyze
        }
        
        var minBrightness = 255
        var maxBrightness = 0
        var brightnessDiffs = 0
        var lastBrightness = -1
        
        for (y in startY until minOf(startY + sampleSize, bitmap.height)) {
            for (x in startX until minOf(startX + sampleSize, bitmap.width)) {
                val pixel = bitmap.getPixel(x, y)
                val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                
                minBrightness = minOf(minBrightness, brightness)
                maxBrightness = maxOf(maxBrightness, brightness)
                
                if (lastBrightness >= 0) {
                    brightnessDiffs += abs(brightness - lastBrightness)
                }
                lastBrightness = brightness
            }
        }
        
        val contrastRatio = (maxBrightness - minBrightness) / 255f
        val noiseLevel = brightnessDiffs / (sampleSize * sampleSize).toFloat() / 255f
        
        return when {
            contrastRatio >= HIGH_CONTRAST_THRESHOLD && noiseLevel <= LOW_NOISE_THRESHOLD -> ImageQuality.HIGH
            contrastRatio >= 0.4f -> ImageQuality.MEDIUM
            else -> ImageQuality.LOW
        }
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
     * Intensity varies based on detected quality.
     */
    private fun enhanceContrast(bitmap: Bitmap, quality: ImageQuality): Bitmap {
        val enhanced = Bitmap.createBitmap(
            bitmap.width,
            bitmap.height,
            Bitmap.Config.ARGB_8888
        )
        
        val canvas = Canvas(enhanced)
        
        // Contrast level based on quality
        val contrast = when (quality) {
            ImageQuality.HIGH -> 1.1f   // Minimal enhancement
            ImageQuality.MEDIUM -> 1.3f // Standard enhancement
            ImageQuality.LOW -> 1.5f    // Strong enhancement
        }
        
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
    
    /**
     * Apply adaptive thresholding (Otsu-style binarization).
     * Creates pure black text on white background.
     */
    private fun adaptiveThreshold(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        // Calculate global threshold using Otsu's method
        val histogram = IntArray(256)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val gray = Color.red(pixel) // Already grayscale
                histogram[gray]++
            }
        }
        
        val totalPixels = width * height
        var sum = 0f
        for (i in 0 until 256) {
            sum += i * histogram[i]
        }
        
        var sumB = 0f
        var wB = 0
        var maxVariance = 0f
        var threshold = 128
        
        for (t in 0 until 256) {
            wB += histogram[t]
            if (wB == 0) continue
            
            val wF = totalPixels - wB
            if (wF == 0) break
            
            sumB += t * histogram[t]
            
            val mB = sumB / wB
            val mF = (sum - sumB) / wF
            
            val variance = wB.toFloat() * wF.toFloat() * (mB - mF) * (mB - mF)
            
            if (variance > maxVariance) {
                maxVariance = variance
                threshold = t
            }
        }
        
        Log.d(TAG, "Adaptive threshold calculated: $threshold")
        
        // Apply threshold
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val gray = Color.red(pixel)
                val newColor = if (gray > threshold) Color.WHITE else Color.BLACK
                result.setPixel(x, y, newColor)
            }
        }
        
        return result
    }
    
    /**
     * Apply denoising to remove small artifacts.
     * Uses simple morphological closing operation.
     */
    private fun denoise(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        // Simple median filter for noise reduction
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val neighbors = mutableListOf<Int>()
                
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val pixel = bitmap.getPixel(x + dx, y + dy)
                        neighbors.add(Color.red(pixel))
                    }
                }
                
                neighbors.sort()
                val median = neighbors[4] // Middle value of 9 pixels
                result.setPixel(x, y, Color.rgb(median, median, median))
            }
        }
        
        // Copy edges unchanged
        for (x in 0 until width) {
            result.setPixel(x, 0, bitmap.getPixel(x, 0))
            result.setPixel(x, height - 1, bitmap.getPixel(x, height - 1))
        }
        for (y in 0 until height) {
            result.setPixel(0, y, bitmap.getPixel(0, y))
            result.setPixel(width - 1, y, bitmap.getPixel(width - 1, y))
        }
        
        return result
    }
}
