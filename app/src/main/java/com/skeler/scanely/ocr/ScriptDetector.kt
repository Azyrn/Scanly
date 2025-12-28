package com.skeler.scanely.ocr

import android.util.Log

private const val TAG = "ScriptDetector"

/**
 * Detects dominant script from text using Unicode ranges.
 * Used to select optimal single language for OCR when user has multiple languages enabled.
 */
object ScriptDetector {
    
    enum class Script(val languages: List<String>) {
        ARABIC(listOf("ara")),
        LATIN(listOf("eng", "fra", "spa", "deu", "ita", "por")),
        CYRILLIC(listOf("rus")),
        CJK(listOf("chi_sim", "jpn")),
        UNKNOWN(emptyList())
    }
    
    /**
     * Detect dominant script from text.
     * Counts characters in each script range and returns the most frequent.
     */
    fun detectScript(text: String): Script {
        if (text.isBlank()) return Script.UNKNOWN
        
        var arabicCount = 0
        var latinCount = 0
        var cyrillicCount = 0
        var cjkCount = 0
        
        for (char in text) {
            when {
                // Arabic: U+0600 to U+06FF (Arabic), U+0750 to U+077F (Arabic Supplement)
                char.code in 0x0600..0x06FF || char.code in 0x0750..0x077F -> arabicCount++
                
                // Latin: A-Z, a-z, Latin Extended (accented chars)
                char in 'A'..'Z' || char in 'a'..'z' || 
                char.code in 0x00C0..0x00FF || // Latin-1 Supplement
                char.code in 0x0100..0x017F -> latinCount++ // Latin Extended-A
                
                // Cyrillic: U+0400 to U+04FF
                char.code in 0x0400..0x04FF -> cyrillicCount++
                
                // CJK: Chinese, Japanese, Korean
                char.code in 0x4E00..0x9FFF || // CJK Unified
                char.code in 0x3040..0x309F || // Hiragana
                char.code in 0x30A0..0x30FF -> cjkCount++ // Katakana
            }
        }
        
        val counts = mapOf(
            Script.ARABIC to arabicCount,
            Script.LATIN to latinCount,
            Script.CYRILLIC to cyrillicCount,
            Script.CJK to cjkCount
        )
        
        val dominant = counts.maxByOrNull { it.value }
        
        Log.d(TAG, "Script counts: Arabic=$arabicCount, Latin=$latinCount, Cyrillic=$cyrillicCount, CJK=$cjkCount")
        
        return if (dominant != null && dominant.value > 0) {
            Log.d(TAG, "Detected dominant script: ${dominant.key}")
            dominant.key
        } else {
            Script.UNKNOWN
        }
    }
    
    /**
     * Select the best single language from user's enabled languages based on detected script.
     * 
     * @param detectedScript The detected script
     * @param enabledLanguages User's enabled OCR languages
     * @return Best matching single language, or first enabled language as fallback
     */
    fun selectBestLanguage(detectedScript: Script, enabledLanguages: List<String>): String {
        if (enabledLanguages.isEmpty()) return "eng"
        if (enabledLanguages.size == 1) return enabledLanguages.first()
        
        // Find intersection of script's languages with user's enabled languages
        val matchingLanguages = detectedScript.languages.filter { it in enabledLanguages }
        
        return if (matchingLanguages.isNotEmpty()) {
            // Prefer order: eng for Latin, first match otherwise
            if (detectedScript == Script.LATIN && "eng" in matchingLanguages) {
                "eng"
            } else {
                matchingLanguages.first()
            }
        } else {
            // Fallback to first enabled language
            enabledLanguages.first()
        }
    }
    
    /**
     * Convenience: Detect script and select best language in one call.
     */
    fun detectAndSelectLanguage(sampleText: String, enabledLanguages: List<String>): String {
        val script = detectScript(sampleText)
        return selectBestLanguage(script, enabledLanguages)
    }
}
