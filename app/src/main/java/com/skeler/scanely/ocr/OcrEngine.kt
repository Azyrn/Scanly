package com.skeler.scanely.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log

private const val TAG = "OcrEngine"
private const val LANG_ARABIC = "ara"

/**
 * Unified OCR engine that can switch between different OCR backends.
 * 
 * - FAST mode: Uses Tesseract OCR (supports more languages including Arabic)
 * - BEST mode: Uses ML Kit Text Recognition (higher accuracy for Latin text)
 * 
 * Usage:
 * ```
 * val engine = OcrEngine(context)
 * engine.initialize(OcrQuality.BEST, listOf("eng"))
 * val result = engine.recognizeText(bitmap)
 * engine.release()
 * ```
 */
class OcrEngine(private val context: Context) {
    
    private val tesseractHelper: OcrHelper = OcrHelper(context)
    private val mlKitHelper: MlKitOcrHelper = MlKitOcrHelper(context)
    
    private var currentQuality: OcrQuality = OcrQuality.FAST
    private var currentLanguages: List<String> = emptyList()
    private var isInitialized = false
    
    /**
     * Initialize the OCR engine with specified quality and languages.
     * 
     * @param quality OCR quality mode (FAST or BEST)
     * @param languages Language codes for Tesseract (ignored for ML Kit)
     * @return true if initialization succeeded
     */
    suspend fun initialize(quality: OcrQuality, languages: List<String>): Boolean {
        Log.d(TAG, "Initializing OCR engine with quality=$quality, languages=$languages")
        
        currentQuality = quality
        currentLanguages = languages
        
        val success = when (quality) {
            OcrQuality.FAST -> tesseractHelper.initialize(languages)
            OcrQuality.BEST -> mlKitHelper.initialize(languages)
        }
        
        isInitialized = success
        return success
    }
    
    /**
     * Recognize text from an image URI.
     */
    suspend fun recognizeText(imageUri: Uri): OcrResult? {
        if (!isInitialized) {
            Log.e(TAG, "OcrEngine not initialized")
            return null
        }
        
        return when (currentQuality) {
            OcrQuality.FAST -> tesseractHelper.recognizeText(imageUri)
            OcrQuality.BEST -> mlKitHelper.recognizeText(imageUri)
        }
    }
    
    /**
     * Recognize text from a bitmap.
     */
    suspend fun recognizeText(bitmap: Bitmap): OcrResult? {
        if (!isInitialized) {
            Log.e(TAG, "OcrEngine not initialized")
            return null
        }
        
        return when (currentQuality) {
            OcrQuality.FAST -> tesseractHelper.recognizeText(bitmap)
            OcrQuality.BEST -> mlKitHelper.recognizeText(bitmap)
        }
    }
    
    /**
     * Check if the engine is ready for OCR.
     */
    fun isReady(): Boolean {
        return when (currentQuality) {
            OcrQuality.FAST -> tesseractHelper.isReady()
            OcrQuality.BEST -> mlKitHelper.isReady()
        }
    }
    
    /**
     * Get the current quality mode.
     */
    fun getCurrentQuality(): OcrQuality = currentQuality
    
    /**
     * Get the current languages (for Tesseract).
     */
    fun getCurrentLanguages(): List<String> = currentLanguages
    
    /**
     * Check if current mode has Arabic support.
     */
    fun hasArabic(): Boolean {
        return currentQuality == OcrQuality.FAST && currentLanguages.contains(LANG_ARABIC)
    }
    
    /**
     * Reinitialize with new quality/languages.
     */
    suspend fun reinitialize(quality: OcrQuality, languages: List<String>): Boolean {
        release()
        return initialize(quality, languages)
    }
    
    /**
     * Release all OCR resources.
     */
    fun release() {
        tesseractHelper.release()
        mlKitHelper.release()
        isInitialized = false
        Log.d(TAG, "OcrEngine resources released")
    }
    
    /**
     * Get the underlying Tesseract helper for PDF processing.
     * PDF processing uses Tesseract directly for multi-language support.
     */
    fun getTesseractHelper(): OcrHelper = tesseractHelper
}
