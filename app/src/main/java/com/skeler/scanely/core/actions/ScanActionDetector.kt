package com.skeler.scanely.core.actions

import android.util.Log
// Barcode import removed


private const val TAG = "ScanActionDetector"

/**
 * Utility object for detecting actionable patterns from scanned text/barcodes.
 * 
 * Detects:
 * - URLs (http, https, www.)
 * - Email addresses
 * - Phone numbers (E.123 format)
 * - WiFi QR codes
 * - SMS messages
 * 
 * Integrates with ML Kit's structured barcode data when available.
 */
object ScanActionDetector {
    
    private const val TAG = "ScanActionDetector"

    // URL pattern: matches http://, https://, and www. prefixes
    private val URL_PATTERN = Regex(
        """(?i)\b(https?://[^\s<>"{}|\\^`\[\]]+|www\.[^\s<>"{}|\\^`\[\]]+\.[a-z]{2,})""",
        RegexOption.IGNORE_CASE
    )
    
    // Email pattern: RFC 5322 simplified
    private val EMAIL_PATTERN = Regex(
        """[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}"""
    )
    
    // Phone pattern: E.123 international format
    private val PHONE_PATTERN = Regex(
        """(?:\+|00)?[1-9]\d{0,2}[-.\s]?\(?\d{1,4}\)?[-.\s]?\d{1,4}[-.\s]?\d{1,9}"""
    )
    
    // WiFi QR pattern: WIFI:T:WPA;S:NetworkName;P:password;;
    private val WIFI_PATTERN = Regex(
        """WIFI:(?:T:([^;]*);)?S:([^;]+);(?:P:([^;]*);)?(?:H:([^;]*);)?;?""",
        RegexOption.IGNORE_CASE
    )
    
    /**
     * Detect actionable patterns from text.
     * ML Kit integration has been removed, so this relies purely on Regex patterns.
     * 
     * @param text Raw text content
     * @return List of detected actions, ordered by relevance
     */
    fun detectActions(text: String): List<ScanAction> {
        val actions = mutableListOf<ScanAction>()
        
        Log.d(TAG, "Detecting actions for text: ${text.take(50)}...")
        
        // Always try pattern detection
        actions.addAll(detectFromPatterns(text))
        
        // Always add a copy option if there's meaningful text
        if (text.isNotBlank() && actions.none { it is ScanAction.CopyText }) {
            actions.add(ScanAction.CopyText(text, "Copy Text"))
        }
        
        Log.d(TAG, "Detected ${actions.size} actions")
        return actions.distinctBy { it.javaClass.simpleName + getActionKey(it) }
    }
    
    /**
     * Detect actions from text patterns.
     */
    private fun detectFromPatterns(text: String): List<ScanAction> {
        val actions = mutableListOf<ScanAction>()
        
        // Check for WiFi QR format first (specific format)
        val wifiMatch = WIFI_PATTERN.find(text)
        if (wifiMatch != null) {
            val type = wifiMatch.groupValues[1].uppercase()
            val ssid = wifiMatch.groupValues[2]
            val password = wifiMatch.groupValues[3].takeIf { it.isNotEmpty() }
            
            val wifiType = when (type) {
                "WPA", "WPA2", "WPA3" -> WifiType.WPA
                "WEP" -> WifiType.WEP
                else -> WifiType.OPEN
            }
            
            actions.add(ScanAction.ConnectWifi(ssid, password, wifiType))
            actions.add(ScanAction.CopyText(ssid, "Copy SSID"))
            return actions // WiFi format is exclusive
        }
        
        // Detect URLs
        val urls = URL_PATTERN.findAll(text).map { it.value }.toList()
        urls.take(5).forEach { url -> // Limit to avoid UI overflow
            val normalized = normalizeUrl(url)
            if (isValidUrl(normalized)) {
                actions.add(ScanAction.OpenUrl(normalized))
            }
        }
        
        // Detect emails
        val emails = EMAIL_PATTERN.findAll(text).map { it.value }.toList()
        emails.take(3).forEach { email ->
            actions.add(ScanAction.SendEmail(email))
        }
        
        // Detect phone numbers - STRICT: require country code (+XX or 00XX)
        val phones = PHONE_PATTERN.findAll(text).map { it.value }.toList()
        phones.take(2).forEach { phone ->
            if (isValidPhoneNumber(phone)) {
                actions.add(ScanAction.CallPhone(phone.trim()))
            }
        }
        
        return actions
    }
    
    /**
     * Strict phone number validation:
     * - Must start with + or 00 (country code indicator)
     * - Must have 10-15 digits total
     * - Must not be just a sequence of numbers (like barcodes)
     */
    private fun isValidPhoneNumber(phone: String): Boolean {
        val cleaned = phone.trim()
        
        // Must start with + or 00 (international format)
        if (!cleaned.startsWith("+") && !cleaned.startsWith("00")) {
            return false
        }
        
        // Count digits
        val digitsOnly = cleaned.filter { it.isDigit() }
        
        // International phone numbers are typically 10-15 digits (including country code)
        if (digitsOnly.length < 10 || digitsOnly.length > 15) {
            return false
        }
        
        // Should have at least one separator or reasonable structure
        // Pure digit strings (like barcodes) are not valid phones
        val hasProperFormat = cleaned.contains(" ") || 
                              cleaned.contains("-") || 
                              cleaned.contains("(") ||
                              cleaned.startsWith("+")
        
        return hasProperFormat
    }
    
    /**
     * Normalize URL: add https:// prefix if missing.
     */
    private fun normalizeUrl(url: String): String {
        return when {
            url.startsWith("http://", ignoreCase = true) -> url
            url.startsWith("https://", ignoreCase = true) -> url
            url.startsWith("www.", ignoreCase = true) -> "https://$url"
            else -> url
        }
    }
    
    /**
     * Validate URL has proper structure and known TLD.
     */
    private fun isValidUrl(url: String): Boolean {
        if (!url.startsWith("http://", ignoreCase = true) && 
            !url.startsWith("https://", ignoreCase = true)) {
            return false
        }
        
        // Extract TLD for validation
        val withoutProtocol = url.substringAfter("://")
        val host = withoutProtocol.substringBefore("/").substringBefore("?")
        val parts = host.split(".")
        
        if (parts.size < 2) return false
        
        val tld = parts.last().lowercase()
        return tld.length >= 2
    }
    
    /**
     * Get a key for deduplication.
     */
    private fun getActionKey(action: ScanAction): String {
        return when (action) {
            is ScanAction.OpenUrl -> action.url
            is ScanAction.CopyText -> action.text.take(50)
            is ScanAction.CallPhone -> action.number
            is ScanAction.SendEmail -> action.email
            is ScanAction.ConnectWifi -> action.ssid
            is ScanAction.SendSms -> action.number
            is ScanAction.AddContact -> "${action.name}${action.phone}${action.email}"
            is ScanAction.ShowRaw -> action.text.take(50)
            is ScanAction.LookupProduct -> action.barcode
        }
    }
    
    /**
     * Get a summary for multiple detected items.
     */
    fun getSummary(actions: List<ScanAction>): Map<String, Int> {
        return actions.groupBy { 
            when (it) {
                is ScanAction.OpenUrl -> "links"
                is ScanAction.SendEmail -> "emails"
                is ScanAction.CallPhone -> "phone numbers"
                else -> "items"
            }
        }.mapValues { it.value.size }
    }
}
