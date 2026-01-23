package com.skeler.scanely.core.actions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * WiFi encryption types for ConnectWifi action.
 */
enum class WifiType {
    WPA,
    WEP,
    OPEN
}

/**
 * Sealed class hierarchy representing smart actions detected from scanned content.
 * 
 * Each action type provides:
 * - Display label for UI buttons/chips
 * - Icon for visual identification
 * - Relevant data for intent launching
 */
sealed class ScanAction {
    
    abstract val label: String
    abstract val icon: ImageVector
    
    /**
     * Open a URL in the browser.
     * Supports http://, https://, and auto-prefixed www. URLs.
     */
    data class OpenUrl(val url: String) : ScanAction() {
        override val label: String = "Open Link"
        override val icon: ImageVector = Icons.Default.Link
    }
    
    /**
     * Copy text to clipboard.
     * Used as both a primary action and fallback.
     */
    data class CopyText(val text: String, val displayLabel: String = "Copy") : ScanAction() {
        override val label: String = displayLabel
        override val icon: ImageVector = Icons.Default.ContentCopy
    }
    
    /**
     * Open phone dialer with number pre-filled.
     * Supports E.123 international format.
     */
    data class CallPhone(val number: String) : ScanAction() {
        override val label: String = "Call"
        override val icon: ImageVector = Icons.Default.Call
    }
    
    /**
     * Open email composer with recipient pre-filled.
     */
    data class SendEmail(
        val email: String,
        val subject: String? = null,
        val body: String? = null
    ) : ScanAction() {
        override val label: String = "Send Email"
        override val icon: ImageVector = Icons.Default.Email
    }
    
    /**
     * Connect to a WiFi network.
     * Parsed from WIFI: QR code format.
     */
    data class ConnectWifi(
        val ssid: String,
        val password: String?,
        val type: WifiType
    ) : ScanAction() {
        override val label: String = "Connect to WiFi"
        override val icon: ImageVector = Icons.Default.Wifi
    }
    
    /**
     * Open SMS composer with number pre-filled.
     */
    data class SendSms(
        val number: String,
        val message: String? = null
    ) : ScanAction() {
        override val label: String = "Send SMS"
        override val icon: ImageVector = Icons.Default.Sms
    }
    
    /**
     * Add a contact to the address book.
     */
    data class AddContact(
        val name: String?,
        val phone: String?,
        val email: String?,
        val organization: String? = null
    ) : ScanAction() {
        override val label: String = "Add Contact"
        override val icon: ImageVector = Icons.Default.Person
    }
    
    /**
     * Fallback action for unrecognized content.
     * Shows raw text with copy-only option.
     */
    data class ShowRaw(val text: String) : ScanAction() {
        override val label: String = "View Text"
        override val icon: ImageVector = Icons.AutoMirrored.Filled.TextSnippet
    }
    
    /**
     * Lookup product information from barcode.
     * Uses Open Food Facts API.
     */
    data class LookupProduct(val barcode: String) : ScanAction() {
        override val label: String = "Product Info"
        override val icon: ImageVector = Icons.Default.RestaurantMenu
    }
}
