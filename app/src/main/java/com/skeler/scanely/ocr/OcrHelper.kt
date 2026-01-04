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
 * OCR result containing extracted text and metadata.
 */
data class OcrResult(
    val text: String,
    val confidence: Int,
    val languages: List<String>,
    val processingTimeMs: Long
)

/**
 * Predefined OCR modes.
 */
enum class OcrMode(val label: String, val languages: List<String>) {
    ENGLISH_ARABIC("English + Arabic", listOf("eng", "ara")),
    ENGLISH("English Only", listOf("eng")),
    ARABIC("Arabic Only", listOf("ara")),
    FRENCH("French", listOf("fra"))
}

/**
 * Thread-safe Tesseract OCR wrapper with language-specific optimization.
 *
 * ARABIC OPTIMIZATION (2025 Best Practices):
 * - OEM_LSTM_ONLY: LSTM handles cursive scripts correctly
 * - textord_force_make_prop_words: Preserves Arabic ligatures
 * - preserve_interword_spaces: Prevents RTL word merging
 * - PSM_AUTO/SINGLE_BLOCK: Never SPARSE_TEXT for Arabic (breaks connections)
 * - 450 DPI scaling for Arabic diacritics preservation
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

        private const val MIN_CONFIDENCE = 30
        private const val RETRY_CONFIDENCE = 25

        private val GARBAGE_REGEX = Regex("[|\\[\\]{}\\\\<>^`~©®™•§¶]")
        private const val RTL_MARK = '\u200F'
        
        // Arabic-specific constants
        private const val ARABIC_LANG_CODE = "ara"
    }

    private val mutex = Mutex()
    private var tessApi: TessBaseAPI? = null
    private var currentLanguages: List<String> = emptyList()
    private var isInitialized = false

    // Quality/density state
    private var detectedQuality: ImageQuality = ImageQuality.MEDIUM
    private var detectedEdgeDensity: Float = 0.15f

    /**
     * Check if current languages include Arabic.
     */
    fun hasArabic(): Boolean = currentLanguages.any { it.startsWith(ARABIC_LANG_CODE) }
    
    /**
     * Check if a language code is Arabic.
     */
    private fun isArabicLanguage(langCode: String): Boolean = langCode.startsWith(ARABIC_LANG_CODE)

    /**
     * Set detected quality and edge density from preprocessor.
     */
    fun setDetectedQuality(quality: ImageQuality, edgeDensity: Float) {
        detectedQuality = quality
        detectedEdgeDensity = edgeDensity
        Log.d(TAG, "Quality: $quality, density: ${"%.3f".format(edgeDensity)}")
    }

    /**
     * Initialize Tesseract with languages.
     */
    suspend fun initialize(languages: List<String>): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                if (languages.isEmpty()) {
                    Log.e(TAG, "Empty language list")
                    return@withContext false
                }

                val ready = LanguageLoader.ensureLanguagesAvailable(context, languages)
                if (!ready) {
                    Log.e(TAG, "Language files not ready: $languages")
                    return@withContext false
                }

                val api = TessBaseAPI()
                val dataPath = LanguageLoader.getDataPath(context)
                val langString = LanguageLoader.getLanguageString(languages)

                Log.d(TAG, "Initializing Tesseract: '$langString' at '$dataPath'")

                // CRITICAL: Use OEM_LSTM_ONLY for Arabic cursive script support
                // Legacy engine (OEM_TESSERACT_ONLY) fragments Arabic ligatures
                val oem = TessBaseAPI.OEM_LSTM_ONLY
                
                if (!api.init(dataPath, langString, oem)) {
                    Log.e(TAG, "Tesseract init failed")
                    api.recycle()
                    return@withContext false
                }

                // Apply language-specific configuration
                initializeTesseractForLanguages(api, languages)

                tessApi = api
                currentLanguages = languages
                isInitialized = true

                Log.d(TAG, "Tesseract ready: $langString (OEM_LSTM_ONLY)")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Init error", e)
                false
            }
        }
    }

    /**
     * Apply language-specific Tesseract configuration.
     * 
     * Arabic requires special handling:
     * - PSM_AUTO or PSM_SINGLE_BLOCK (not SPARSE_TEXT which breaks cursive connections)
     * - textord_force_make_prop_words: Preserves ligature grouping
     * - preserve_interword_spaces: Critical for RTL layout
     * - Dictionary loading: Helps disambiguate similar shapes (ه vs ة)
     */
    private fun initializeTesseractForLanguages(api: TessBaseAPI, languages: List<String>) {
        val hasArabicLang = languages.any { isArabicLanguage(it) }
        val hasLatinLang = languages.any { !isArabicLanguage(it) }
        
        try {
            if (hasArabicLang) {
                Log.d(TAG, "Applying Arabic-optimized configuration")
                
                // PSM_AUTO (3) or PSM_SINGLE_BLOCK (6) for Arabic
                // NEVER use PSM_SPARSE_TEXT - it breaks cursive connections
                api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
                
                // CRITICAL: Force proportional word detection for Arabic ligatures
                // Without this, connected Arabic letters are split incorrectly
                api.setVariable("textord_force_make_prop_words", "1")
                
                // Preserve spaces between Arabic words (RTL layout critical)
                api.setVariable("preserve_interword_spaces", "1")
                
                // Enable dictionaries for Arabic word recognition
                // Helps distinguish similar shapes: ه vs ة, ع vs غ
                api.setVariable("load_system_dawg", "1")
                api.setVariable("load_freq_dawg", "1")
                
                // Penalize non-dictionary words to reduce garbage output
                api.setVariable("language_model_penalty_non_freq_dict_word", "0.15")
                api.setVariable("language_model_penalty_non_dict_word", "0.25")
                
                // Disable noise rejection that can remove diacritics
                api.setVariable("textord_noise_rejwords", "0")
                
                // Character blacklist - common OCR garbage
                api.setVariable("tessedit_char_blacklist", "|{}[]\\<>^`~@#\$%")
                
            } else if (hasLatinLang) {
                Log.d(TAG, "Applying Latin-optimized configuration")
                
                // Standard configuration for Latin scripts
                applyLatinConfiguration(api)
            }
            
            // Mixed Arabic + Latin (common in documents)
            if (hasArabicLang && hasLatinLang) {
                Log.d(TAG, "Mixed Arabic+Latin mode: prioritizing Arabic settings")
                // Keep Arabic settings but enable Latin dictionary too
                api.setVariable("load_system_dawg", "1")
                api.setVariable("load_freq_dawg", "1")
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Config error: ${e.message}")
        }
    }

    /**
     * Apply Latin-specific configuration.
     */
    private fun applyLatinConfiguration(api: TessBaseAPI) {
        // Dynamic PSM based on quality/density
        val psm = when {
            detectedQuality == ImageQuality.HIGH && detectedEdgeDensity > 0.25f -> {
                TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK
            }
            detectedQuality == ImageQuality.LOW && detectedEdgeDensity < 0.05f -> {
                TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT
            }
            else -> TessBaseAPI.PageSegMode.PSM_AUTO
        }
        api.pageSegMode = psm
        Log.d(TAG, "Latin PSM: $psm")

        // Enable dictionaries for clean images
        if (detectedQuality != ImageQuality.LOW) {
            api.setVariable("load_system_dawg", "1")
            api.setVariable("load_freq_dawg", "1")
        } else {
            // Disable for noisy images to prevent hallucinations
            api.setVariable("load_system_dawg", "0")
            api.setVariable("load_freq_dawg", "0")
            api.setVariable("textord_noise_rejwords", "1")
        }
        
        api.setVariable("tessedit_char_blacklist", "|{}[]\\<>^`~")
    }

    /**
     * Update configuration for current quality/density.
     * Called before recognition with latest image analysis.
     */
    private fun updateConfigurationForRecognition() {
        tessApi?.let { api ->
            try {
                // For Arabic, never switch to SPARSE_TEXT
                if (hasArabic()) {
                    // Keep PSM_AUTO for Arabic regardless of density
                    api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
                } else {
                    // Dynamic PSM for Latin
                    applyLatinConfiguration(api)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Config update error: ${e.message}")
            }
        }
    }

    /**
     * Recognize text from URI.
     */
    suspend fun recognizeText(imageUri: Uri): OcrResult? = withContext(Dispatchers.IO) {
        if (!isInitialized || tessApi == null) {
            Log.e(TAG, "Not initialized")
            return@withContext null
        }

        // Use language-aware preprocessing (450 DPI for Arabic)
        val preprocessed = ImagePreprocessor.preprocess(
            context, 
            imageUri, 
            currentLanguages.firstOrNull() ?: "eng"
        )
        if (preprocessed == null) {
            Log.e(TAG, "Preprocessing failed")
            return@withContext null
        }

        // NOTE: Do NOT call isLikelyEmpty here!
        // The image is already preprocessed and binarized.
        // Binarized images have very low variance (all black/white) = false positive empty detection.
        
        // Log preprocessed image info for debugging
        Log.d(TAG, "Preprocessed image: ${preprocessed.width}x${preprocessed.height}")

        // Update quality/density
        val quality = ImagePreprocessor.detectQuality(preprocessed)
        val density = ImagePreprocessor.calculateEdgeDensity(preprocessed)
        setDetectedQuality(quality, density)

        mutex.withLock {
            updateConfigurationForRecognition()
        }

        val result = recognizeInternal(preprocessed)
        preprocessed.recycle()
        result
    }

    /**
     * Recognize text from bitmap.
     */
    suspend fun recognizeText(bitmap: Bitmap): OcrResult? = withContext(Dispatchers.IO) {
        if (!isInitialized || tessApi == null) {
            Log.e(TAG, "Not initialized")
            return@withContext null
        }

        // NOTE: Do NOT call isLikelyEmpty on already-preprocessed bitmaps!
        // Binarized images have low variance = false positive.
        Log.d(TAG, "Input bitmap: ${bitmap.width}x${bitmap.height}")

        // Update quality/density
        val quality = ImagePreprocessor.detectQuality(bitmap)
        val density = ImagePreprocessor.calculateEdgeDensity(bitmap)
        setDetectedQuality(quality, density)

        mutex.withLock {
            updateConfigurationForRecognition()
        }

        recognizeInternal(bitmap)
    }

    /**
     * Internal recognition with retry logic.
     */
    private suspend fun recognizeInternal(bitmap: Bitmap): OcrResult? = mutex.withLock {
        if (tessApi == null) return@withLock null

        try {
            val t0 = System.currentTimeMillis()

            tessApi?.setImage(bitmap)
            var text = tessApi?.utF8Text ?: ""
            var conf = tessApi?.meanConfidence() ?: 0

            Log.d(TAG, "=== OCR RESULT ===")
            Log.d(TAG, "Pass 1: confidence=$conf%, length=${text.length}")
            Log.d(TAG, "Raw text (first 100): ${text.take(100)}")

            // Retry with different PSM if poor result
            // For Arabic, only retry with PSM_SINGLE_BLOCK (never SPARSE_TEXT)
            if (conf < RETRY_CONFIDENCE && text.length < 20) {
                Log.d(TAG, "Low confidence, retrying...")

                val retryModes = if (hasArabic()) {
                    // Arabic-safe retry modes (no SPARSE_TEXT)
                    listOf(
                        TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK,
                        TessBaseAPI.PageSegMode.PSM_AUTO_OSD
                    )
                } else {
                    // Latin can try SPARSE_TEXT
                    listOf(
                        TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT,
                        TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK,
                        TessBaseAPI.PageSegMode.PSM_AUTO_OSD
                    )
                }

                for (psm in retryModes) {
                    try {
                        tessApi?.pageSegMode = psm
                        tessApi?.setImage(bitmap)
                        val rt = tessApi?.utF8Text ?: ""
                        val rc = tessApi?.meanConfidence() ?: 0

                        Log.d(TAG, "PSM $psm: conf=$rc%, len=${rt.length}")

                        if (rc > conf + 5 || (rt.length > text.length * 1.5 && rc >= conf - 5)) {
                            text = rt
                            conf = rc
                            Log.d(TAG, "Using PSM $psm result")
                            break
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Retry failed: ${e.message}")
                    }
                }

                // Restore original configuration
                updateConfigurationForRecognition()
            }

            val elapsed = System.currentTimeMillis() - t0

            if (conf < MIN_CONFIDENCE && text.length < 10) {
                Log.w(TAG, "Confidence too low ($conf%)")
                return@withLock OcrResult("", conf, currentLanguages, elapsed)
            }

            val cleaned = postProcess(text)
            Log.d(TAG, "OCR done: ${cleaned.length} chars, ${elapsed}ms")

            OcrResult(cleaned, conf, currentLanguages, elapsed)

        } catch (e: Exception) {
            Log.e(TAG, "Recognition error", e)
            null
        }
    }

    /**
     * Clean up OCR output.
     */
    private fun postProcess(text: String): String {
        if (text.isBlank()) return ""

        var result = text
            .replace(GARBAGE_REGEX, "")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")

        result = result.lines()
            .map { it.trim() }
            .filter { line ->
                line.isNotEmpty() &&
                !(line.length == 1 && !line[0].isLetterOrDigit()) &&
                !line.matches(Regex("^[^\\p{L}\\p{N}]+$"))
            }
            .joinToString("\n")

        if (hasArabic() && containsArabic(result)) {
            result = handleArabic(result)
        }

        return result.trim()
    }

    private fun containsArabic(text: String): Boolean {
        return text.any { it.code in 0x0600..0x06FF || it.code in 0x0750..0x077F }
    }

    private fun handleArabic(text: String): String {
        return text.lines().joinToString("\n") { line ->
            if (containsArabic(line)) "$RTL_MARK$line" else line
        }
    }

    fun isReady(): Boolean = isInitialized && tessApi != null

    fun getCurrentLanguages(): List<String> = currentLanguages

    fun release() {
        try {
            tessApi?.recycle()
            tessApi = null
            isInitialized = false
            currentLanguages = emptyList()
            Log.d(TAG, "Released")
        } catch (e: Exception) {
            Log.e(TAG, "Release error", e)
        }
    }

    suspend fun reinitialize(languages: List<String>): Boolean {
        release()
        return initialize(languages)
    }
}

// --- END OF FILE: OCRHELPER.KT ---
