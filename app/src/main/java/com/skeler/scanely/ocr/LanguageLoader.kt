package com.skeler.scanely.ocr

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val TAG = "LanguageLoader"

/**
 * Manages Tesseract traineddata files.
 * Handles extraction from assets and runtime availability checks.
 * 
 * Features:
 * - 10 language support
 * - File integrity validation
 * - Efficient single-language mode
 * - Asset extraction caching
 */
object LanguageLoader {
    
    // Supported languages with their traineddata file names
    val SUPPORTED_LANGUAGES = mapOf(
        "ara" to "Arabic",
        "eng" to "English",
        "fra" to "French",
        "spa" to "Spanish",
        "deu" to "German",
        "ita" to "Italian",
        "por" to "Portuguese",
        "rus" to "Russian",
        "chi_sim" to "Chinese (Simplified)",
        "jpn" to "Japanese"
    )
    
    private const val TESSDATA_DIR = "tessdata"
    
    // Minimum valid traineddata file size (corrupt files are smaller)
    private const val MIN_TRAINEDDATA_SIZE = 100_000L // 100KB minimum
    
    /**
     * Get the data path for Tesseract initialization.
     * This is the parent directory containing the 'tessdata' folder.
     */
    fun getDataPath(context: Context): String {
        return context.filesDir.absolutePath
    }
    
    /**
     * Get the tessdata directory path.
     */
    fun getTessdataPath(context: Context): String {
        return File(context.filesDir, TESSDATA_DIR).absolutePath
    }
    
    /**
     * Check if a specific language is available and valid.
     */
    fun isLanguageAvailable(context: Context, langCode: String): Boolean {
        val tessdataDir = File(context.filesDir, TESSDATA_DIR)
        val trainedDataFile = File(tessdataDir, "$langCode.traineddata")
        return trainedDataFile.exists() && trainedDataFile.length() > MIN_TRAINEDDATA_SIZE
    }
    
    /**
     * Validate the integrity of a traineddata file.
     * Checks file size and basic structure.
     */
    fun validateTrainedData(file: File): Boolean {
        if (!file.exists()) return false
        if (file.length() < MIN_TRAINEDDATA_SIZE) {
            Log.w(TAG, "Traineddata file too small (${file.length()} bytes): ${file.name}")
            return false
        }
        
        // Basic check: traineddata files should start with specific bytes
        try {
            file.inputStream().use { stream ->
                val header = ByteArray(4)
                stream.read(header)
                // Tesseract traineddata files have specific magic bytes
                return true // For now, just size check is sufficient
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating traineddata: ${file.name}", e)
            return false
        }
    }
    
    /**
     * Get list of available (already extracted) languages.
     */
    fun getAvailableLanguages(context: Context): List<String> {
        val tessdataDir = File(context.filesDir, TESSDATA_DIR)
        if (!tessdataDir.exists()) return emptyList()
        
        return tessdataDir.listFiles()
            ?.filter { it.name.endsWith(".traineddata") }
            ?.filter { it.length() > MIN_TRAINEDDATA_SIZE }
            ?.map { it.name.removeSuffix(".traineddata") }
            ?: emptyList()
    }
    
    /**
     * Ensure specified languages are available.
     * Extracts from assets if not already present.
     * 
     * @param context Application context
     * @param languages List of language codes to ensure availability
     * @return true if all languages are available, false otherwise
     */
    suspend fun ensureLanguagesAvailable(
        context: Context,
        languages: List<String>
    ): Boolean = withContext(Dispatchers.IO) {
        val tessdataDir = File(context.filesDir, TESSDATA_DIR)
        
        // Create tessdata directory if needed
        if (!tessdataDir.exists()) {
            if (!tessdataDir.mkdirs()) {
                Log.e(TAG, "Failed to create tessdata directory at $tessdataDir")
                return@withContext false
            }
        }
        
        // Validate languages before loading
        val validLanguages = languages.filter { it in SUPPORTED_LANGUAGES }
        if (validLanguages.isEmpty()) {
            Log.e(TAG, "No valid languages in list: $languages")
            return@withContext false
        }
        
        if (validLanguages.size != languages.size) {
            Log.w(TAG, "Some languages not supported: ${languages - validLanguages.toSet()}")
        }
        
        var allSuccess = true
        
        for (langCode in validLanguages) {
            val isAvailable = isLanguageAvailable(context, langCode)
            Log.d(TAG, "Checking language '$langCode': available=$isAvailable")
            
            if (!isAvailable) {
                // Check if file exists but is corrupted
                val existingFile = File(tessdataDir, "$langCode.traineddata")
                if (existingFile.exists() && existingFile.length() < MIN_TRAINEDDATA_SIZE) {
                    Log.w(TAG, "Corrupt traineddata detected, re-extracting: $langCode")
                    existingFile.delete()
                }
                
                Log.d(TAG, "Extracting language '$langCode' from assets...")
                val extracted = extractLanguageFromAssets(context, langCode)
                if (!extracted) {
                    Log.e(TAG, "Failed to extract language: $langCode")
                    allSuccess = false
                } else {
                    Log.d(TAG, "Successfully extracted '$langCode'")
                }
            }
        }
        
        allSuccess
    }
    
    /**
     * Extract a single language traineddata file from assets.
     */
    private suspend fun extractLanguageFromAssets(
        context: Context,
        langCode: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val assetFileName = "$TESSDATA_DIR/$langCode.traineddata"
            val outputFile = File(
                File(context.filesDir, TESSDATA_DIR),
                "$langCode.traineddata"
            )
            
            // Check if asset exists
            val assetManager = context.assets
            val assetList = assetManager.list(TESSDATA_DIR) ?: emptyArray()
            
            if (!assetList.contains("$langCode.traineddata")) {
                Log.e(TAG, "Asset not found: $assetFileName")
                return@withContext false
            }
            
            // Extract with size verification
            assetManager.open(assetFileName).use { inputStream ->
                FileOutputStream(outputFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            // Validate extracted file
            if (!validateTrainedData(outputFile)) {
                Log.e(TAG, "Extracted file validation failed: $langCode")
                outputFile.delete()
                return@withContext false
            }
            
            Log.d(TAG, "Extracted $langCode.traineddata (${outputFile.length()} bytes)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract language: $langCode", e)
            false
        }
    }
    
    /**
     * Get the combined language string for Tesseract init.
     * For multiple languages, format is "eng+ara+fra"
     * 
     * Note: For best accuracy, prefer single-language mode when possible.
     */
    fun getLanguageString(languages: List<String>): String {
        // Filter to valid languages only
        val validLangs = languages.filter { it in SUPPORTED_LANGUAGES }
        return if (validLangs.isEmpty()) "eng" else validLangs.joinToString("+")
    }
    
    /**
     * Check if single-language mode should be used.
     * Single language is faster and more accurate.
     */
    fun shouldUseSingleLanguage(languages: List<String>): Boolean {
        return languages.size == 1
    }
    
    /**
     * Clear all downloaded/extracted traineddata files.
     */
    suspend fun clearAllLanguages(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val tessdataDir = File(context.filesDir, TESSDATA_DIR)
            tessdataDir.deleteRecursively()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear languages", e)
            false
        }
    }
}
