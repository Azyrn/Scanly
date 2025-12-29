package com.skeler.scanely.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "OcrHelper"

/**
 * OCR result data class containing extracted text and metadata.
 */
data class OcrResult(
    val text: String,
    val confidence: Int,
    val languages: List<String>,
    val processingTimeMs: Long
)

enum class OcrMode(val label: String, val languages: List<String>) {
    ENGLISH_ARABIC("English + Arabic", listOf("eng", "ara")),
    ENGLISH("English Only", listOf("eng")),
    ARABIC("Arabic Only", listOf("ara")),
    FRENCH("French", listOf("fra"))
}

/**
 * Tesseract OCR wrapper providing thread-safe text extraction
 * with preprocessing and post-processing.
 *
 * Features:
 * - Thread-safe with Mutex locking
 * - Multi-language support (up to 10 languages)
 * - Arabic RTL text handling
 * - Line and paragraph preservation
 * - Confidence-based filtering
 *
 * Usage:
 * ```
 * val ocrHelper = OcrHelper(context)
 * ocrHelper.initialize(listOf("eng", "ara"))
 * val result = ocrHelper.recognizeText(bitmap)
 * ocrHelper.release()
 * ```
 */
class OcrHelper(private val context: Context) {
    
    companion object {
        val SUPPORTED_LANGUAGES_MAP = mapOf(
            "eng" to "English",
            "ara" to "Arabic",
            "fra" to "French",
            "spa" to "Spanish",
            "deu" to "German",
            "ita" to "Italian",
            "por" to "Portuguese",
            "rus" to "Russian",
            "jpn" to "Japanese",
            "chi_sim" to "Chinese (Simplified)"
        )
        
        // Minimum confidence threshold to include text
        private const val MIN_CONFIDENCE_THRESHOLD = 30
        
        // Common garbage characters from OCR noise
        private val GARBAGE_PATTERN = Regex("[|\\[\\]{}\\\\<>^`~©®™•§¶]")
        
        // RTL Unicode markers
        private const val RTL_MARK = '\u200F'
        private const val LTR_MARK = '\u200E'
    }
    
    private val mutex = Mutex()
    private var tessApi: TessBaseAPI? = null
    private var currentLanguages: List<String> = emptyList()
    private var isInitialized = false
    
    /**
     * Initialize Tesseract with specified languages.
     * Must be called before recognizeText().
     *
     * @param languages List of language codes (e.g., ["eng", "ara"])
     * @return true if initialization succeeded
     */
    suspend fun initialize(languages: List<String>): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                if (languages.isEmpty()) {
                    Log.e(TAG, "Initialize called with empty language list")
                    return@withContext false
                }

                // Ensure language files are available
                val languagesReady = LanguageLoader.ensureLanguagesAvailable(context, languages)
                if (!languagesReady) {
                    Log.e(TAG, "Failed to prepare language files for: $languages")
                    return@withContext false
                }
                
                // Create and init TessBaseAPI
                val api = TessBaseAPI()
                val dataPath = LanguageLoader.getDataPath(context)
                
                // Construct language string: "eng+ara" for multi-language
                val langString = LanguageLoader.getLanguageString(languages)
                
                Log.d(TAG, "Initializing Tesseract with: '$langString' at '$dataPath'")
                
                val initResult = api.init(dataPath, langString)
                
                if (!initResult) {
                    Log.e(TAG, "Tesseract init failed for: '$langString'")
                    val fileExists = LanguageLoader.isLanguageAvailable(context, languages.first())
                    Log.e(TAG, "Debug: Primary lang file ${languages.first()}.traineddata exists: $fileExists")
                    api.recycle()
                    return@withContext false
                }
                
                // Configure for best accuracy
                api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
                
                // Tesseract 5.x optimizations
                try {
                    api.setVariable("tessedit_pageseg_mode", "1") // Auto with OSD
                    api.setVariable("textord_heavy_nr", "1") // Enable heavy noise reduction
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set Tesseract variables: ${e.message}")
                }
                
                tessApi = api
                currentLanguages = languages
                isInitialized = true
                
                Log.d(TAG, "Tesseract initialized successfully with: $langString")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Tesseract initialization crash", e)
                false
            }
        }
    }
    
    /**
     * Recognize text from an image URI.
     * Applies preprocessing before OCR.
     */
    suspend fun recognizeText(imageUri: Uri): OcrResult? = withContext(Dispatchers.IO) {
        if (!isInitialized || tessApi == null) {
            Log.e(TAG, "OcrHelper not initialized")
            return@withContext null
        }
        
        val preprocessed = ImagePreprocessor.preprocess(context, imageUri)
        if (preprocessed == null) {
            Log.e(TAG, "Image preprocessing failed")
            return@withContext null
        }
        
        val result = recognizeText(preprocessed)
        preprocessed.recycle()
        result
    }
    
    /**
     * Recognize text from a bitmap.
     * The bitmap should already be preprocessed for best results.
     */
    suspend fun recognizeText(bitmap: Bitmap): OcrResult? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!isInitialized || tessApi == null) {
                Log.e(TAG, "OcrHelper not initialized")
                return@withLock null
            }
         
            try {
                val startTime = System.currentTimeMillis()
                
                tessApi?.setImage(bitmap)
                
                val rawText = tessApi?.utF8Text ?: ""
                val confidence = tessApi?.meanConfidence() ?: 0
                
                val processingTime = System.currentTimeMillis() - startTime
                
                Log.d(TAG, "Raw OCR completed in ${processingTime}ms, confidence: $confidence%")
                
                // Skip post-processing if confidence is very low
                if (confidence < MIN_CONFIDENCE_THRESHOLD && rawText.length < 10) {
                    Log.w(TAG, "OCR confidence too low ($confidence%), likely garbage")
                    return@withLock OcrResult(
                        text = "",
                        confidence = confidence,
                        languages = currentLanguages,
                        processingTimeMs = processingTime
                    )
                }
                
                // Post-process the text
                val cleanedText = postProcess(rawText)
                
                Log.d(TAG, "OCR completed. Cleaned text length: ${cleanedText.length}")
                
                OcrResult(
                    text = cleanedText,
                    confidence = confidence,
                    languages = currentLanguages,
                    processingTimeMs = processingTime
                )
            } catch (e: Exception) {
                Log.e(TAG, "OCR recognition failed", e)
                null
            }
        }
    }
    
    /**
     * Post-process extracted text:
     * - Remove garbage characters
     * - Preserve paragraph structure
     * - Handle Arabic RTL text properly
     * - Preserve numbers exactly (no 0→O conversion)
     */
    private fun postProcess(text: String): String {
        if (text.isBlank()) return ""
        
        var result = text
        
        // Remove common OCR garbage characters (but NOT numbers!)
        result = result.replace(GARBAGE_PATTERN, "")
        
        // Normalize whitespace while preserving paragraph structure
        result = result.replace(Regex("[ \\t]+"), " ")    // Multiple spaces to single
        result = result.replace(Regex("\\n{3,}"), "\n\n") // Max 2 newlines
        
        // Process line by line to preserve structure
        result = result.lines()
            .map { line -> 
                var cleanLine = line.trim()
                
                // Remove lines that are likely noise (single chars, only punctuation)
                if (cleanLine.length == 1 && !cleanLine[0].isLetterOrDigit()) {
                    ""
                } else if (cleanLine.matches(Regex("^[^\\p{L}\\p{N}]+$"))) {
                    "" // Line contains no letters or numbers
                } else {
                    cleanLine
                }
            }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
        
        // Handle Arabic text: ensure proper RTL markers if Arabic is detected
        if (hasArabic() && containsArabic(result)) {
            result = handleArabicText(result)
        }
        
        return result.trim()
    }
    
    /**
     * Check if text contains Arabic characters.
     */
    private fun containsArabic(text: String): Boolean {
        return text.any { char ->
            char.code in 0x0600..0x06FF || // Arabic
            char.code in 0x0750..0x077F    // Arabic Supplement
        }
    }
    
    /**
     * Handle Arabic RTL text: add proper Unicode markers for display.
     */
    private fun handleArabicText(text: String): String {
        return text.lines().map { line ->
            if (containsArabic(line)) {
                // Add RTL mark at start of Arabic lines
                "$RTL_MARK$line"
            } else {
                line
            }
        }.joinToString("\n")
    }
    
    /**
     * Check if the helper contains Arabic language.
     */
    fun hasArabic(): Boolean = currentLanguages.contains("ara")
    
    /**
     * Get current initialization status.
     */
    fun isReady(): Boolean = isInitialized && tessApi != null
    
    /**
     * Get current languages.
     */
    fun getCurrentLanguages(): List<String> = currentLanguages
    
    /**
     * Release Tesseract resources.
     * Must be called when done to prevent memory leaks.
     */
    fun release() {
        try {
            tessApi?.let {
                it.recycle()
            }
            tessApi = null
            isInitialized = false
            currentLanguages = emptyList()
            Log.d(TAG, "Tesseract resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing Tesseract", e)
        }
    }
    
    /**
     * Reinitialize with new languages.
     */
    suspend fun reinitialize(languages: List<String>): Boolean {
        release()
        return initialize(languages)
    }
}
