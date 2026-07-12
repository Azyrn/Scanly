package com.skeler.scanely.core.actions

import android.util.Log

private const val TAG = "ScanActionDetector"

object ScanActionDetector {
    
    private const val TAG = "ScanActionDetector"

    private val URL_PATTERN = Regex(
        """(?i)\b(https?://[^\s<>"{}|\\^`\[\]]+|www\.[^\s<>"{}|\\^`\[\]]+\.[a-z]{2,})""",
        RegexOption.IGNORE_CASE
    )
    
    private val EMAIL_PATTERN = Regex(
        """[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}"""
    )
    
    private val PHONE_PATTERN = Regex(
        """(?:\+|00)?[1-9]\d{0,2}[-.\s]?\(?\d{1,4}\)?[-.\s]?\d{1,4}[-.\s]?\d{1,9}"""
    )
    
    // WIFI:T:WPA;S:NetworkName;P:password;;
    private val WIFI_PATTERN = Regex(
        """WIFI:(?:T:([^;]*);)?S:([^;]+);(?:P:([^;]*);)?(?:H:([^;]*);)?;?""",
        RegexOption.IGNORE_CASE
    )
    
    fun detectActions(text: String): List<ScanAction> {
        val actions = mutableListOf<ScanAction>()
        
        Log.d(TAG, "Detecting actions for text: ${text.take(50)}...")
        
        actions.addAll(detectFromPatterns(text))
        
        if (text.isNotBlank() && actions.none { it is ScanAction.CopyText }) {
            actions.add(ScanAction.CopyText(text, "Copy Text"))
        }
        
        Log.d(TAG, "Detected ${actions.size} actions")
        return actions.distinctBy { it.javaClass.simpleName + getActionKey(it) }
    }
    
    private fun detectFromPatterns(text: String): List<ScanAction> {
        val actions = mutableListOf<ScanAction>()
        
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
        
        val urls = URL_PATTERN.findAll(text).map { it.value }.toList()
        urls.take(5).forEach { url -> // Limit to avoid UI overflow
            val normalized = normalizeUrl(url)
            if (isValidUrl(normalized)) {
                actions.add(ScanAction.OpenUrl(normalized))
            }
        }
        
        val emails = EMAIL_PATTERN.findAll(text).map { it.value }.toList()
        emails.take(3).forEach { email ->
            actions.add(ScanAction.SendEmail(email))
        }
        
                val phones = PHONE_PATTERN.findAll(text).map { it.value }.toList()
        phones.take(2).forEach { phone ->
            if (isValidPhoneNumber(phone)) {
                actions.add(ScanAction.CallPhone(phone.trim()))
            }
        }
        
        return actions
    }
    
    // Phones: +/00 + 10–15 digits (avoid barcode FPs).
    private fun isValidPhoneNumber(phone: String): Boolean {
        val cleaned = phone.trim()
        
        if (!cleaned.startsWith("+") && !cleaned.startsWith("00")) {
            return false
        }
        
        val digitsOnly = cleaned.filter { it.isDigit() }
        
        if (digitsOnly.length < 10 || digitsOnly.length > 15) {
            return false
        }
        
        val hasProperFormat = cleaned.contains(" ") || 
                              cleaned.contains("-") || 
                              cleaned.contains("(") ||
                              cleaned.startsWith("+")
        
        return hasProperFormat
    }
    
    private fun normalizeUrl(url: String): String {
        return when {
            url.startsWith("http://", ignoreCase = true) -> url
            url.startsWith("https://", ignoreCase = true) -> url
            url.startsWith("www.", ignoreCase = true) -> "https://$url"
            else -> url
        }
    }
    
    private fun isValidUrl(url: String): Boolean {
        if (!url.startsWith("http://", ignoreCase = true) && 
            !url.startsWith("https://", ignoreCase = true)) {
            return false
        }
        
        val withoutProtocol = url.substringAfter("://")
        val host = withoutProtocol.substringBefore("/").substringBefore("?")
        val parts = host.split(".")
        
        if (parts.size < 2) return false
        
        val tld = parts.last().lowercase()
        return tld.length >= 2
    }
    
    private fun getActionKey(action: ScanAction): String {
        return when (action) {
            is ScanAction.OpenUrl -> action.url
            is ScanAction.CopyText -> action.text.take(50)
            is ScanAction.CallPhone -> action.number
            is ScanAction.SendEmail -> action.email
            is ScanAction.ConnectWifi -> action.ssid
            is ScanAction.SendSms -> action.number
            is ScanAction.AddContact -> "${action.name}${action.phone}${action.email}"
            is ScanAction.AddEvent -> "${action.title}${action.startRaw}"
            is ScanAction.ShowRaw -> action.text.take(50)
            is ScanAction.LookupProduct -> action.barcode
        }
    }
    
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
