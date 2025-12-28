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
     * Check if a specific language is available.
     */
    fun isLanguageAvailable(context: Context, langCode: String): Boolean {
        val tessdataDir = File(context.filesDir, TESSDATA_DIR)
        val trainedDataFile = File(tessdataDir, "$langCode.traineddata")
        return trainedDataFile.exists() && trainedDataFile.length() > 0
    }
    
    /**
     * Get list of available (already extracted) languages.
     */
    fun getAvailableLanguages(context: Context): List<String> {
        val tessdataDir = File(context.filesDir, TESSDATA_DIR)
        if (!tessdataDir.exists()) return emptyList()
        
        return tessdataDir.listFiles()
            ?.filter { it.name.endsWith(".traineddata") }
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
        
        var allSuccess = true
        
        for (langCode in languages) {
            val isAvailable = isLanguageAvailable(context, langCode)
            Log.d(TAG, "Checking language '$langCode': available=$isAvailable")
            
            if (!isAvailable) {
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
            
            // Extract
            assetManager.open(assetFileName).use { inputStream ->
                FileOutputStream(outputFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
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
     */
    fun getLanguageString(languages: List<String>): String {
        return languages.joinToString("+")
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
