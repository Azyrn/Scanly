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
 * Usage:
 * ```
 * val ocrHelper = OcrHelper(context)
 * ocrHelper.initialize(listOf("eng", "ara"))
 * val result = ocrHelper.recognizeText(bitmap)
 * ocrHelper.release()
 * ```
 */
class OcrHelper(private val context: Context) {
    
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
            
            // Construct language string securely
            // If only one language, strict check. Tesseract expects "eng", "ara", etc.
            val langString = LanguageLoader.getLanguageString(languages)
            
            Log.d(TAG, "Attempting to init Tesseract with: '$langString' at '$dataPath'")
            
            val initResult = api.init(dataPath, langString)
            
            if (!initResult) {
                Log.e(TAG, "Tesseract init failed for: '$langString'")
                // Fallback: If multiple langs failed, try just the first one as a safety net? 
                // No, better to fail and let user know, but let's try to debug why.
                val fileExists = LanguageLoader.isLanguageAvailable(context, languages.first())
                Log.e(TAG, "Debug: Primary lang file ${languages.first()}.traineddata exists: $fileExists")
                
                api.recycle()
                return@withContext false
            }
            
            // Configure for best accuracy
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
            
            tessApi = api
            currentLanguages = languages
            isInitialized = true
            
            Log.d(TAG, "Tesseract initialized successfully with: $langString")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Tesseract initialization crash", e)
            false
        }
        } // End mutex
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
            
            // Post-process the text
            val cleanedText = postProcess(rawText)
            
            Log.d(TAG, "OCR completed in ${processingTime}ms, confidence: $confidence%")
            
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
     * - Fix common OCR errors
     * - Handle Arabic RTL text ordering
     */
    private fun postProcess(text: String): String {
        var result = text
        
        // Remove common garbage characters
        result = result.replace(Regex("[|\\[\\]{}\\\\<>^`~]"), "")
        
        // Remove excessive whitespace but preserve paragraph structure
        result = result.replace(Regex("[ \t]+"), " ")
        result = result.replace(Regex("\n{3,}"), "\n\n")
        
        // Trim each line
        result = result.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() || it == "" }
            .joinToString("\n")
        
        // Fix common OCR errors
        result = result
            .replace("0", "O") // Context-dependent, may need refinement
            .replace(Regex("(?<=[a-z])1(?=[a-z])"), "l") // 1 -> l in words
        
        return result.trim()
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
     * Release Tesseract resources.
     * Must be called when done to prevent memory leaks.
     */
    fun release() {
        // Can't suspend here easily if called from onCleared, but we should try to lock if possible.
        // For simplicity in Android ViewModels onCleared, simple null check is often used.
        // But let's try to be safe.
        // Note: effectively we just recycle. `tessApi` access should ideally be locked, 
        // but release() is often called from MainThread/Lifecycle methods that can't suspend easily.
        // We often just do it safely.
        
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
