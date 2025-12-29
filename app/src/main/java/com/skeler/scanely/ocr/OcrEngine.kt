package com.skeler.scanely.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log

private const val TAG = "OcrEngine"
private const val LANG_ARABIC = "ara"

/**
 * Unified OCR engine.
 * 
 * - TEXT OCR: Always uses Tesseract (supports multi-language + offline)
 * - BARCODE: Always uses ML Kit
 */
class OcrEngine(private val context: Context) {
    
    private val tesseractHelper: OcrHelper = OcrHelper(context)
    private val mlKitHelper: MlKitOcrHelper = MlKitOcrHelper(context)
    
    // Tesseract state
    private var currentLanguages: List<String> = emptyList()
    private var isInitialized = false
    
    /**
     * Initialize the Tesseract engine with specified languages.
     * ML Kit is initialized lazily or separately.
     */
    suspend fun initialize(languages: List<String>): Boolean {
        Log.d(TAG, "Initializing OCR engine with languages=$languages")
        
        currentLanguages = languages
        
        // Always initialize Tesseract for text
        val success = tesseractHelper.initialize(languages)
        
        // Initialize ML Kit for Barcode scanning (if needed upfront, though it is usually lazy)
        mlKitHelper.initialize()
        
        isInitialized = success
        return success
    }
    
    /**
     * Recognize text from an image URI (Uses Tesseract).
     */
    suspend fun recognizeText(imageUri: Uri): OcrResult? {
        if (!isInitialized) {
            Log.e(TAG, "OcrEngine not initialized for text recognition")
            return null
        }
        return tesseractHelper.recognizeText(imageUri)
    }
    
    /**
     * Recognize text from a bitmap (Uses Tesseract).
     */
    suspend fun recognizeText(bitmap: Bitmap): OcrResult? {
         if (!isInitialized) {
            Log.e(TAG, "OcrEngine not initialized for text recognition")
            return null
        }
        return tesseractHelper.recognizeText(bitmap)
    }

    /**
     * Scan Barcode/QR from an Image URI (Uses ML Kit).
     */
    suspend fun scanBarcode(imageUri: Uri): OcrResult? {
        return mlKitHelper.scanBarcode(imageUri)
    }

    /**
     * Scan Barcode/QR from a Bitmap (Uses ML Kit).
     */
    suspend fun scanBarcode(bitmap: Bitmap): OcrResult? {
        return mlKitHelper.scanBarcode(bitmap)
    }
    
    /**
     * Check if the engine is ready for OCR.
     */
    fun isReady(): Boolean {
        return tesseractHelper.isReady()
    }
    
    /**
     * Get the current languages.
     */
    fun getCurrentLanguages(): List<String> = currentLanguages
    
    /**
     * Check if current mode has Arabic support.
     */
    fun hasArabic(): Boolean {
        return currentLanguages.contains(LANG_ARABIC)
    }
    
    /**
     * Reinitialize with new languages.
     */
    suspend fun reinitialize(languages: List<String>): Boolean {
        release()
        return initialize(languages)
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
     */
    fun getTesseractHelper(): OcrHelper = tesseractHelper
}
