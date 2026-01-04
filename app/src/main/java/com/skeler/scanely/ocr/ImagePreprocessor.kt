package com.skeler.scanely.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

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
 * Ultra-Performance Image Preprocessing Pipeline for Tesseract OCR.
 * 
 * PERFORMANCE OPTIMIZATIONS:
 * 1. Buffer-based processing - IntArray instead of getPixel/setPixel (100x faster)
 * 2. Integral Images (Summed Area Tables) - O(1) window queries for Sauvola (225x faster)
 * 3. Language-aware DPI scaling - 450 DPI for Arabic (diacritics), 300 DPI for Latin
 * 4. Quality-adaptive pipeline - Skip unnecessary stages for clean images
 * 
 * Pipeline Stages:
 * 1. Empty Detection - Skip blank images
 * 2. DPI Scaling - 450 for Arabic, 300 for Latin
 * 3. Grayscale Conversion
 * 4. Quality Detection
 * 5. Gaussian Blur 3x3 (MEDIUM/LOW)
 * 6. Skew Correction (LOW)
 * 7. Contrast Enhancement (CLAHE for LOW, linear for others)
 * 8. Binarization (Sauvola for LOW, Otsu for MEDIUM)
 * 9. Morphological Closing (LOW)
 * 10. Median Denoising (MEDIUM/LOW)
 */
object ImagePreprocessor {

    // Language-aware DPI scaling
    private const val TARGET_DPI_LATIN = 300
    private const val TARGET_DPI_ARABIC = 450  // Higher DPI preserves Arabic diacritics (dots/nuqta)
    private const val SOURCE_DPI = 72
    private const val MIN_DIMENSION = 600
    private const val MAX_DIMENSION = 2500
    private const val ARABIC_LANG_CODE = "ara"

    // Quality detection thresholds
    private const val HIGH_CONTRAST_THRESHOLD = 0.65f
    private const val MEDIUM_CONTRAST_THRESHOLD = 0.35f
    private const val LOW_NOISE_THRESHOLD = 0.08f
    private const val HIGH_NOISE_THRESHOLD = 0.20f

    // Empty image detection - lowered to reduce false positives
    private const val EMPTY_VARIANCE_THRESHOLD = 15f

    // CLAHE parameters
    private const val CLAHE_CLIP_LIMIT = 2.5f
    private const val CLAHE_TILE_COUNT = 8

    // Sauvola parameters (uses Integral Images for O(1) queries)
    private const val SAUVOLA_WINDOW = 15
    private const val SAUVOLA_K_LATIN = 0.2f      // Standard for Latin scripts
    private const val SAUVOLA_K_ARABIC = 0.08f    // Softer threshold preserves Arabic ligatures
    private const val SAUVOLA_R = 128f

    // Skew detection
    private const val MAX_SKEW_DEGREES = 15f
    private const val SKEW_STEP = 0.5f

    /**
     * Check if image is likely empty/blank using center sample variance.
     * O(sampleSize²) - very fast for small samples.
     */
    fun isLikelyEmpty(bitmap: Bitmap): Boolean {
        val sampleSize = min(80, min(bitmap.width, bitmap.height) / 3)
        if (sampleSize < 16) return false

        val startX = (bitmap.width - sampleSize) / 2
        val startY = (bitmap.height - sampleSize) / 2

        val pixels = IntArray(sampleSize * sampleSize)
        bitmap.getPixels(pixels, 0, sampleSize, startX, startY, sampleSize, sampleSize)

        var sum = 0L
        var sumSq = 0L

        for (i in pixels.indices) {
            val p = pixels[i]
            val gray = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
            sum += gray
            sumSq += gray.toLong() * gray
        }

        val n = pixels.size
        val mean = sum.toFloat() / n
        val variance = (sumSq.toFloat() / n) - (mean * mean)

        // More lenient threshold - only truly blank images should be skipped
        val isEmpty = variance < EMPTY_VARIANCE_THRESHOLD
        Log.d(TAG, "Empty check: variance=${"%.1f".format(variance)}, mean=${"%.1f".format(mean)}, isEmpty=$isEmpty")
        return isEmpty
    }

    /**
     * Calculate edge density for PSM selection.
     * Returns 0.0 (no edges) to 1.0 (all edges).
     */
    fun calculateEdgeDensity(bitmap: Bitmap): Float {
        val w = min(150, bitmap.width)
        val h = min(150, bitmap.height)
        val x0 = (bitmap.width - w) / 2
        val y0 = (bitmap.height - h) / 2

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, x0, y0, w, h)

        var edges = 0
        val threshold = 25

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                val c = Color.red(pixels[idx])
                val r = Color.red(pixels[idx + 1])
                val d = Color.red(pixels[idx + w])
                if (abs(c - r) > threshold || abs(c - d) > threshold) {
                    edges++
                }
            }
        }

        return edges.toFloat() / ((w - 2) * (h - 2))
    }

    /**
     * Preprocess from URI (default Latin DPI).
     */
    suspend fun preprocess(context: Context, imageUri: Uri): Bitmap? {
        return preprocess(context, imageUri, "eng")
    }

    /**
     * Preprocess from URI with language-aware DPI scaling.
     * Arabic uses 450 DPI to preserve diacritics (dots/nuqta).
     * Runs on Dispatchers.IO for thread safety.
     */
    suspend fun preprocess(context: Context, imageUri: Uri, languageCode: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val stream = context.contentResolver.openInputStream(imageUri)
                val original = BitmapFactory.decodeStream(stream)
                stream?.close()

                if (original == null) {
                    Log.e(TAG, "Failed to decode: $imageUri")
                    return@withContext null
                }

                preprocessBitmap(original, recycleInput = true, languageCode = languageCode)
            } catch (e: Exception) {
                Log.e(TAG, "Preprocessing failed", e)
                null
            }
        }
    }

    /**
     * Preprocess from existing bitmap (default Latin DPI).
     */
    fun preprocess(bitmap: Bitmap, recycleInput: Boolean = false): Bitmap {
        return preprocessBitmap(bitmap, recycleInput, "eng")
    }

    /**
     * Preprocess from existing bitmap with language-aware DPI scaling.
     */
    fun preprocess(bitmap: Bitmap, recycleInput: Boolean = false, languageCode: String): Bitmap {
        return preprocessBitmap(bitmap, recycleInput, languageCode)
    }

    /**
     * Main preprocessing pipeline with quality-adaptive stages.
     * 
     * ARABIC SPECIAL HANDLING:
     * For Arabic with complex backgrounds (photos), aggressive preprocessing destroys
     * the text (colored text on photos). We use minimal preprocessing for Arabic LOW quality.
     */
    private fun preprocessBitmap(input: Bitmap, recycleInput: Boolean, languageCode: String = "eng"): Bitmap {
        val t0 = System.currentTimeMillis()
        val isArabic = languageCode.startsWith(ARABIC_LANG_CODE)
        if (isArabic) Log.d(TAG, "Arabic mode: using 450 DPI")

        // Step 0: Early empty check
        if (isLikelyEmpty(input)) {
            Log.d(TAG, "Image empty, minimal processing")
            val gray = toGrayscaleBuffered(input)
            if (recycleInput) input.recycle()
            return gray
        }

        // Step 1: DPI scaling (450 for Arabic, 300 for Latin)
        var current = resizeForDpi(input, isArabic)
        if (recycleInput && current !== input) input.recycle()

        // Step 2: Grayscale
        var next = toGrayscaleBuffered(current)
        if (next !== current) current.recycle()
        current = next

        // Step 3: Detect quality
        val quality = detectQuality(current)
        Log.d(TAG, "Quality: $quality")

        // =====================================================================
        // ARABIC MINIMAL PREPROCESSING PATH
        // For Arabic on photo backgrounds (LOW quality detection), skip all
        // destructive operations that destroy colored text overlays.
        // Only apply: DPI scaling + grayscale + light contrast
        // =====================================================================
        if (isArabic && quality == ImageQuality.LOW) {
            Log.d(TAG, "Arabic on photo background: MINIMAL preprocessing (no binarization)")
            
            // Only apply light contrast enhancement
            next = enhanceContrastLinear(current, 1.2f)
            if (next !== current) current.recycle()
            current = next
            
            Log.d(TAG, "Preprocessing done in ${System.currentTimeMillis() - t0}ms (ARABIC MINIMAL)")
            return current
        }
        // =====================================================================

        // Step 4: Gaussian blur (MEDIUM/LOW only)
        if (quality != ImageQuality.HIGH) {
            next = applyGaussianBlurBuffered(current)
            if (next !== current) current.recycle()
            current = next
        }

        // Step 5: Skew correction (LOW only, but NOT for Arabic)
        if (quality == ImageQuality.LOW && !isArabic) {
            next = correctSkew(current)
            if (next !== current) current.recycle()
            current = next
        }

        // Step 6: Contrast enhancement
        next = when (quality) {
            ImageQuality.HIGH -> enhanceContrastLinear(current, 1.15f)
            ImageQuality.MEDIUM -> enhanceContrastLinear(current, 1.35f)
            ImageQuality.LOW -> applyClaheBuffered(current)
        }
        if (next !== current) current.recycle()
        current = next

        // Step 7: Binarization (with inversion detection)
        val needsInversion = detectInversion(current)
        if (needsInversion) {
            Log.d(TAG, "Inverted image detected (white on dark), inverting...")
            next = invertImage(current)
            if (next !== current) current.recycle()
            current = next
        }
        
        // For Arabic MEDIUM quality, use Otsu (global) instead of Sauvola (local adaptive)
        // Sauvola can break ligatures in medium quality images
        next = when (quality) {
            ImageQuality.HIGH -> current // Skip binarization for clean docs
            ImageQuality.MEDIUM -> applyOtsuThresholdBuffered(current)
            ImageQuality.LOW -> applySauvolaIntegralImage(current, isArabic) // Softer K for Arabic
        }
        if (next !== current) current.recycle()
        current = next

        // Step 8: Morphological closing (LOW only, skip for Arabic)
        if (quality == ImageQuality.LOW && !isArabic) {
            next = applyMorphologicalClosingBuffered(current)
            if (next !== current) current.recycle()
            current = next
        }

        // Step 9: Median denoise (MEDIUM/LOW only)
        // *** SKIP FOR ARABIC *** - Median filter destroys diacritics (dots/nuqta)
        if (quality != ImageQuality.HIGH && !isArabic) {
            next = applyMedianFilterBuffered(current)
            if (next !== current) current.recycle()
            current = next
        } else if (isArabic) {
            Log.d(TAG, "Skipping median filter for Arabic (preserves diacritics)")
        }

        Log.d(TAG, "Preprocessing done in ${System.currentTimeMillis() - t0}ms (quality: $quality)")
        return current
    }

    /**
     * Detect image quality based on contrast, noise, and brightness.
     */
    fun detectQuality(bitmap: Bitmap): ImageQuality {
        val size = min(100, min(bitmap.width, bitmap.height) / 2)
        if (size < 16) return ImageQuality.MEDIUM

        val x0 = (bitmap.width - size) / 2
        val y0 = (bitmap.height - size) / 2

        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, x0, y0, size, size)

        var minVal = 255
        var maxVal = 0
        var sum = 0L
        var sumSq = 0L
        var diffs = 0
        var prev = -1

        for (i in pixels.indices) {
            val gray = Color.red(pixels[i])
            minVal = minOf(minVal, gray)
            maxVal = maxOf(maxVal, gray)
            sum += gray
            sumSq += gray.toLong() * gray
            if (prev >= 0) diffs += abs(gray - prev)
            prev = gray
        }

        val n = pixels.size
        val contrast = (maxVal - minVal) / 255f
        val noise = diffs / n.toFloat() / 255f
        val mean = sum.toFloat() / n
        val variance = (sumSq.toFloat() / n) - (mean * mean)
        val stdDev = sqrt(max(0f, variance))

        Log.d(TAG, "Quality: contrast=${"%.2f".format(contrast)}, noise=${"%.3f".format(noise)}, stdDev=${"%.1f".format(stdDev)}")

        return when {
            contrast >= HIGH_CONTRAST_THRESHOLD &&
            noise <= LOW_NOISE_THRESHOLD &&
            mean in 40f..220f -> ImageQuality.HIGH

            contrast >= MEDIUM_CONTRAST_THRESHOLD &&
            noise <= HIGH_NOISE_THRESHOLD -> ImageQuality.MEDIUM

            else -> ImageQuality.LOW
        }
    }

    /**
     * Scale to target DPI equivalent, clamped to [600, 2500] px.
     * Arabic uses 450 DPI to preserve diacritics (dots/nuqta).
     * Latin uses 300 DPI.
     */
    private fun resizeForDpi(bitmap: Bitmap, isArabic: Boolean = false): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val targetDpi = if (isArabic) TARGET_DPI_ARABIC else TARGET_DPI_LATIN
        val scale = targetDpi.toFloat() / SOURCE_DPI

        var nw = (w * scale).toInt()
        var nh = (h * scale).toInt()

        // Cap to max
        if (nw > MAX_DIMENSION || nh > MAX_DIMENSION) {
            val capScale = MAX_DIMENSION.toFloat() / max(nw, nh)
            nw = (nw * capScale).toInt()
            nh = (nh * capScale).toInt()
        }

        // Ensure min
        if (nw < MIN_DIMENSION && nh < MIN_DIMENSION) {
            val minScale = MIN_DIMENSION.toFloat() / min(nw, nh)
            nw = (nw * minScale).toInt()
            nh = (nh * minScale).toInt()
        }

        if (nw == w && nh == h) return bitmap

        Log.d(TAG, "Resize: ${w}x${h} -> ${nw}x${nh} (DPI: $targetDpi)")
        return Bitmap.createScaledBitmap(bitmap, nw, nh, true)
    }

    /**
     * Buffer-based grayscale using luminance formula.
     * ITU-R BT.601: Y = 0.299R + 0.587G + 0.114B
     */
    private fun toGrayscaleBuffered(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val p = pixels[i]
            val gray = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
            pixels[i] = Color.rgb(gray, gray, gray)
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * 3x3 Gaussian blur with kernel [1,2,1; 2,4,2; 1,2,1] / 16.
     * Buffer-based for maximum performance.
     */
    private fun applyGaussianBlurBuffered(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val src = IntArray(w * h)
        val dst = IntArray(w * h)
        bitmap.getPixels(src, 0, w, 0, 0, w, h)

        // Copy edges first
        System.arraycopy(src, 0, dst, 0, src.size)

        val k = intArrayOf(1, 2, 1, 2, 4, 2, 1, 2, 1)

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                var sum = 0
                var ki = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        sum += Color.red(src[(y + dy) * w + (x + dx)]) * k[ki++]
                    }
                }
                val g = (sum / 16).coerceIn(0, 255)
                dst[y * w + x] = Color.rgb(g, g, g)
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(dst, 0, w, 0, 0, w, h)
        Log.d(TAG, "Applied Gaussian blur")
        return result
    }

    /**
     * Correct skew using projection profile analysis.
     */
    private fun correctSkew(bitmap: Bitmap): Bitmap {
        val angle = detectSkewAngle(bitmap)
        if (abs(angle) < 0.5f) {
            Log.d(TAG, "Skew ${angle}° too small, skipping")
            return bitmap
        }
        Log.d(TAG, "Correcting skew: ${angle}°")
        return rotateImage(bitmap, -angle)
    }

    /**
     * Detect skew angle via horizontal projection profile variance maximization.
     */
    private fun detectSkewAngle(bitmap: Bitmap): Float {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // Binarize to boolean array (dark = true)
        val binary = BooleanArray(w * h)
        for (i in pixels.indices) {
            binary[i] = Color.red(pixels[i]) < 128
        }

        var bestAngle = 0f
        var bestVar = 0f

        var angle = -MAX_SKEW_DEGREES
        while (angle <= MAX_SKEW_DEGREES) {
            val variance = projectionVariance(binary, w, h, angle)
            if (variance > bestVar) {
                bestVar = variance
                bestAngle = angle
            }
            angle += SKEW_STEP
        }

        return bestAngle
    }

    /**
     * Calculate variance of horizontal projection at given angle.
     */
    private fun projectionVariance(binary: BooleanArray, w: Int, h: Int, angleDeg: Float): Float {
        val rad = Math.toRadians(angleDeg.toDouble())
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()
        val cx = w / 2f
        val cy = h / 2f

        val proj = IntArray(h)

        for (y in 0 until h) {
            for (x in 0 until w) {
                if (binary[y * w + x]) {
                    val dx = x - cx
                    val dy = y - cy
                    val ny = (dy * cosA - dx * sinA + cy).roundToInt()
                    if (ny in 0 until h) {
                        proj[ny]++
                    }
                }
            }
        }

        var sum = 0L
        var sumSq = 0L
        for (p in proj) {
            sum += p
            sumSq += p.toLong() * p
        }

        val mean = sum.toFloat() / h
        return (sumSq.toFloat() / h) - (mean * mean)
    }

    /**
     * Rotate image by angle in degrees.
     */
    private fun rotateImage(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Linear contrast enhancement.
     */
    private fun enhanceContrastLinear(bitmap: Bitmap, factor: Float): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val offset = (-.5f * factor + .5f) * 255f

        val cm = ColorMatrix(floatArrayOf(
            factor, 0f, 0f, 0f, offset,
            0f, factor, 0f, 0f, offset,
            0f, 0f, factor, 0f, offset,
            0f, 0f, 0f, 1f, 0f
        ))

        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    /**
     * CLAHE - Contrast Limited Adaptive Histogram Equalization.
     * Tile-based histogram equalization with clipping.
     */
    private fun applyClaheBuffered(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val src = IntArray(w * h)
        val dst = IntArray(w * h)
        bitmap.getPixels(src, 0, w, 0, 0, w, h)

        val tw = max(1, w / CLAHE_TILE_COUNT)
        val th = max(1, h / CLAHE_TILE_COUNT)

        for (ty in 0 until CLAHE_TILE_COUNT) {
            for (tx in 0 until CLAHE_TILE_COUNT) {
                val x1 = tx * tw
                val y1 = ty * th
                val x2 = min(x1 + tw, w)
                val y2 = min(y1 + th, h)

                if (x2 <= x1 || y2 <= y1) continue

                val hist = IntArray(256)
                for (y in y1 until y2) {
                    for (x in x1 until x2) {
                        hist[Color.red(src[y * w + x])]++
                    }
                }

                val area = (x2 - x1) * (y2 - y1)
                val clip = (CLAHE_CLIP_LIMIT * area / 256).toInt()

                var excess = 0
                for (i in 0 until 256) {
                    if (hist[i] > clip) {
                        excess += hist[i] - clip
                        hist[i] = clip
                    }
                }
                val incr = excess / 256
                for (i in 0 until 256) hist[i] += incr

                val cdf = IntArray(256)
                cdf[0] = hist[0]
                for (i in 1 until 256) cdf[i] = cdf[i - 1] + hist[i]
                val cdfMin = cdf.first { it > 0 }

                for (y in y1 until y2) {
                    for (x in x1 until x2) {
                        val idx = y * w + x
                        val g = Color.red(src[idx])
                        val ng = ((cdf[g] - cdfMin) * 255f / (area - cdfMin)).toInt().coerceIn(0, 255)
                        dst[idx] = Color.rgb(ng, ng, ng)
                    }
                }
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(dst, 0, w, 0, 0, w, h)
        Log.d(TAG, "Applied CLAHE")
        return result
    }

    /**
     * Otsu's global binarization.
     * Finds optimal threshold by maximizing inter-class variance.
     */
    private fun applyOtsuThresholdBuffered(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val hist = IntArray(256)
        for (p in pixels) hist[Color.red(p)]++

        val total = pixels.size
        var sum = 0f
        for (i in 0 until 256) sum += i * hist[i]

        var sumB = 0f
        var wB = 0
        var maxVar = 0f
        var thresh = 128

        for (t in 0 until 256) {
            wB += hist[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break

            sumB += t * hist[t]
            val mB = sumB / wB
            val mF = (sum - sumB) / wF
            val variance = wB.toFloat() * wF * (mB - mF) * (mB - mF)

            if (variance > maxVar) {
                maxVar = variance
                thresh = t
            }
        }

        for (i in pixels.indices) {
            pixels[i] = if (Color.red(pixels[i]) > thresh) Color.WHITE else Color.BLACK
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        Log.d(TAG, "Applied Otsu: threshold=$thresh")
        return result
    }

    /**
     * ULTRA-PERFORMANCE Sauvola binarization using Integral Images (Summed Area Tables).
     * 
     * Complexity: O(W*H) instead of O(W*H*window²)
     * For a 2000x2000 image with 15x15 window: 225x speedup!
     * 
     * Arabic mode uses softer K (0.08 vs 0.2) to preserve ligature connections.
     * 
     * Sauvola formula: T(x,y) = mean * (1 + k * (stdDev / R - 1))
     */
    private fun applySauvolaIntegralImage(bitmap: Bitmap, isArabic: Boolean = false): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val src = IntArray(w * h)
        val dst = IntArray(w * h)
        bitmap.getPixels(src, 0, w, 0, 0, w, h)

        val half = SAUVOLA_WINDOW / 2
        val k = if (isArabic) SAUVOLA_K_ARABIC else SAUVOLA_K_LATIN
        Log.d(TAG, "Sauvola K=${k} (isArabic=$isArabic)")
        
        // Integral image dimensions: (h+1) x (w+1) for simpler boundary handling
        val iw = w + 1
        val ih = h + 1

        // Step 1: Build integral image and integral of squares
        // integral[y][x] = sum of all pixels from (0,0) to (x-1,y-1)
        val integral = LongArray(iw * ih)
        val integralSq = LongArray(iw * ih)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val g = Color.red(src[y * w + x]).toLong()
                val idx = (y + 1) * iw + (x + 1)
                val up = y * iw + (x + 1)
                val left = (y + 1) * iw + x
                val upLeft = y * iw + x
                
                integral[idx] = g + integral[up] + integral[left] - integral[upLeft]
                integralSq[idx] = g * g + integralSq[up] + integralSq[left] - integralSq[upLeft]
            }
        }

        // Step 2: Apply Sauvola threshold using O(1) window queries
        for (y in 0 until h) {
            for (x in 0 until w) {
                // Window boundaries (clamped)
                val x1 = max(0, x - half)
                val y1 = max(0, y - half)
                val x2 = min(w - 1, x + half)
                val y2 = min(h - 1, y + half)

                val area = (x2 - x1 + 1) * (y2 - y1 + 1)

                // O(1) sum query using integral image
                // Corners in integral image coordinates (offset by 1)
                val br = (y2 + 1) * iw + (x2 + 1)  // bottom-right
                val bl = (y2 + 1) * iw + x1        // bottom-left
                val tr = y1 * iw + (x2 + 1)        // top-right
                val tl = y1 * iw + x1              // top-left

                val sumVal = integral[br] - integral[bl] - integral[tr] + integral[tl]
                val sumSqVal = integralSq[br] - integralSq[bl] - integralSq[tr] + integralSq[tl]

                // Calculate mean and standard deviation
                val mean = sumVal.toFloat() / area
                val variance = (sumSqVal.toFloat() / area) - (mean * mean)
                val stdDev = sqrt(max(0f, variance))

                // Sauvola threshold: T = mean * (1 + k * (stdDev / R - 1))
                val thresh = mean * (1 + k * (stdDev / SAUVOLA_R - 1))

                val g = Color.red(src[y * w + x])
                dst[y * w + x] = if (g > thresh) Color.WHITE else Color.BLACK
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(dst, 0, w, 0, 0, w, h)
        Log.d(TAG, "Applied Sauvola (Integral Image O(1))")
        return result
    }

    /**
     * Morphological closing: dilate then erode.
     * Connects broken character strokes.
     */
    private fun applyMorphologicalClosingBuffered(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val src = IntArray(w * h)
        val dilated = IntArray(w * h)
        val eroded = IntArray(w * h)
        bitmap.getPixels(src, 0, w, 0, 0, w, h)

        System.arraycopy(src, 0, dilated, 0, src.size)

        // Dilation (expand dark regions)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                var dark = false
                outer@ for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (Color.red(src[(y + dy) * w + (x + dx)]) < 128) {
                            dark = true
                            break@outer
                        }
                    }
                }
                dilated[y * w + x] = if (dark) Color.BLACK else Color.WHITE
            }
        }

        System.arraycopy(dilated, 0, eroded, 0, dilated.size)

        // Erosion (shrink dark regions)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                var allDark = true
                outer@ for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (Color.red(dilated[(y + dy) * w + (x + dx)]) >= 128) {
                            allDark = false
                            break@outer
                        }
                    }
                }
                eroded[y * w + x] = if (allDark) Color.BLACK else Color.WHITE
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(eroded, 0, w, 0, 0, w, h)
        Log.d(TAG, "Applied morphological closing")
        return result
    }

    /**
     * 3x3 Median filter for salt-and-pepper noise removal.
     * Non-linear filter that preserves edges.
     */
    private fun applyMedianFilterBuffered(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val src = IntArray(w * h)
        val dst = IntArray(w * h)
        bitmap.getPixels(src, 0, w, 0, 0, w, h)

        System.arraycopy(src, 0, dst, 0, src.size)

        val neighbors = IntArray(9)

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                var ni = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        neighbors[ni++] = Color.red(src[(y + dy) * w + (x + dx)])
                    }
                }
                neighbors.sort()
                val m = neighbors[4]
                dst[y * w + x] = Color.rgb(m, m, m)
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(dst, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * Detect if image has inverted colors (white text on dark background).
     * Uses center sample mean brightness - if dark background, mean will be low.
     */
    private fun detectInversion(bitmap: Bitmap): Boolean {
        val sampleSize = min(100, min(bitmap.width, bitmap.height) / 3)
        if (sampleSize < 16) return false

        val startX = (bitmap.width - sampleSize) / 2
        val startY = (bitmap.height - sampleSize) / 2

        val pixels = IntArray(sampleSize * sampleSize)
        bitmap.getPixels(pixels, 0, sampleSize, startX, startY, sampleSize, sampleSize)

        var sum = 0L
        for (p in pixels) {
            sum += Color.red(p)
        }

        val mean = sum.toFloat() / pixels.size
        
        // If mean brightness < 100, likely dark background (inverted)
        val isInverted = mean < 100f
        Log.d(TAG, "Inversion check: mean=${"%.1f".format(mean)}, inverted=$isInverted")
        return isInverted
    }

    /**
     * Invert image colors (255 - pixel value).
     * Used to fix white-on-dark text before binarization.
     */
    private fun invertImage(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val g = Color.red(pixels[i])
            val inverted = 255 - g
            pixels[i] = Color.rgb(inverted, inverted, inverted)
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        Log.d(TAG, "Inverted image colors")
        return result
    }
}
