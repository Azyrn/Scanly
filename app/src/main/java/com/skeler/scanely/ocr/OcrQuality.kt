package com.skeler.scanely.ocr

/**
 * OCR Quality modes that control which OCR engine is used.
 * 
 * @property label Human-readable label for UI
 * @property description Description of the quality mode
 */
enum class OcrQuality(val label: String, val description: String) {
    /**
     * Fast mode using Tesseract OCR.
     * - Faster processing
     * - Supports more languages
     * - Lower accuracy
     */
    FAST("Fast", "Tesseract OCR - faster, supports more languages"),
    
    /**
     * Best mode using Google ML Kit Text Recognition.
     * - Higher accuracy
     * - Better for printed text
     * - Limited to Latin script
     */
    BEST("Best", "ML Kit - higher accuracy for Latin text")
}
