package com.skeler.scanely.core.actions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TextSnippet
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

enum class WifiType {
    WPA,
    WEP,
    OPEN
}

sealed class ScanAction {

    abstract val label: String
    abstract val icon: ImageVector

    data class OpenUrl(val url: String) : ScanAction() {
        override val label: String = "Open Link"
        override val icon: ImageVector = Icons.Rounded.Link
    }

    data class CopyText(val text: String, val displayLabel: String = "Copy") : ScanAction() {
        override val label: String = displayLabel
        override val icon: ImageVector = Icons.Rounded.ContentCopy
    }

    data class CallPhone(val number: String) : ScanAction() {
        override val label: String = "Call"
        override val icon: ImageVector = Icons.Rounded.Call
    }

    data class SendEmail(
        val email: String,
        val subject: String? = null,
        val body: String? = null
    ) : ScanAction() {
        override val label: String = "Send Email"
        override val icon: ImageVector = Icons.Rounded.Email
    }

    data class ConnectWifi(
        val ssid: String,
        val password: String?,
        val type: WifiType
    ) : ScanAction() {
        override val label: String = "Connect to WiFi"
        override val icon: ImageVector = Icons.Rounded.Wifi
    }

    data class SendSms(
        val number: String,
        val message: String? = null
    ) : ScanAction() {
        override val label: String = "Send SMS"
        override val icon: ImageVector = Icons.Rounded.Sms
    }

    data class AddContact(
        val name: String?,
        val phone: String?,
        val email: String?,
        val organization: String? = null
    ) : ScanAction() {
        override val label: String = "Add Contact"
        override val icon: ImageVector = Icons.Rounded.Person
    }

    data class ShowRaw(val text: String) : ScanAction() {
        override val label: String = "View Text"
        override val icon: ImageVector = Icons.AutoMirrored.Rounded.TextSnippet
    }

    /** startRaw/endRaw: ML Kit CalendarDateTime, e.g. "20240604T090000Z". */
    data class AddEvent(
        val title: String?,
        val location: String? = null,
        val description: String? = null,
        val startRaw: String? = null,
        val endRaw: String? = null
    ) : ScanAction() {
        override val label: String = "Add to Calendar"
        override val icon: ImageVector = Icons.Rounded.Event
    }

    data class LookupProduct(val barcode: String) : ScanAction() {
        override val label: String = "Product Info"
        override val icon: ImageVector = Icons.Rounded.RestaurantMenu
    }
}
