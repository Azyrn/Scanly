package com.skeler.scanely.core.actions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object ActionExecutor {

    fun execute(context: Context, action: ScanAction) {
        when (action) {
            is ScanAction.OpenUrl -> openUrl(context, action.url)
            is ScanAction.CopyText -> copyText(context, action.text)
            is ScanAction.CallPhone -> callPhone(context, action.number)
            is ScanAction.SendEmail -> sendEmail(context, action.email, action.subject, action.body)
            is ScanAction.SendSms -> sendSms(context, action.number, action.message)
            is ScanAction.ConnectWifi -> connectWifi(context, action.ssid, action.password, action.type)
            is ScanAction.AddContact -> addContact(context, action)
            is ScanAction.AddEvent -> addEvent(context, action)
            is ScanAction.ShowRaw -> copyText(context, action.text)
            is ScanAction.LookupProduct -> { /* Handled in BarcodeScannerScreen */ }
        }
    }

    private fun openUrl(context: Context, url: String) {
        try {
            var finalUrl = url
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                finalUrl = "https://$url"
            }
            val intent = Intent(Intent.ACTION_VIEW, finalUrl.toUri())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open URL", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyText(context: Context, text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Scanned Text", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to copy", Toast.LENGTH_SHORT).show()
        }
    }

    private fun callPhone(context: Context, number: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL, "tel:$number".toUri())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open dialer", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendEmail(context: Context, email: String, subject: String?, body: String?) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = "mailto:".toUri()
                putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
                body?.let { putExtra(Intent.EXTRA_TEXT, it) }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open email app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendSms(context: Context, number: String, message: String?) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO, "smsto:$number".toUri())
            message?.let { intent.putExtra("sms_body", it) }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open SMS app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun connectWifi(context: Context, ssid: String, password: String?, type: WifiType) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val suggestion = WifiNetworkSuggestion.Builder()
                    .setSsid(ssid)
                    .apply {
                        if (password != null && type != WifiType.OPEN) {
                            setWpa2Passphrase(password)
                        }
                    }
                    .build()

                val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
                wifiManager.addNetworkSuggestions(listOf(suggestion))
                
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Toast.makeText(context, "WiFi suggestion added: $ssid", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Toast.makeText(context, "Open WiFi settings to connect to: $ssid", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot connect to WiFi", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addContact(context: Context, action: ScanAction.AddContact) {
        try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                type = ContactsContract.Contacts.CONTENT_TYPE
                action.name?.let { putExtra(ContactsContract.Intents.Insert.NAME, it) }
                action.phone?.let { putExtra(ContactsContract.Intents.Insert.PHONE, it) }
                action.email?.let { putExtra(ContactsContract.Intents.Insert.EMAIL, it) }
                action.organization?.let { putExtra(ContactsContract.Intents.Insert.COMPANY, it) }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open contacts", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addEvent(context: Context, action: ScanAction.AddEvent) {
        try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                action.title?.let { putExtra(CalendarContract.Events.TITLE, it) }
                action.location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
                action.description?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) }
                parseEventMillis(action.startRaw)?.let {
                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it)
                }
                parseEventMillis(action.endRaw)?.let {
                    putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it)
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open calendar", Toast.LENGTH_SHORT).show()
        }
    }

    /** iCal basic: yyyyMMdd['T'HHmmss['Z']]; null if unparseable. */
    private fun parseEventMillis(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val value = raw.trim()
        val (pattern, utc) = when {
            value.endsWith("Z") -> "yyyyMMdd'T'HHmmss'Z'" to true
            value.contains("T") -> "yyyyMMdd'T'HHmmss" to false
            else -> "yyyyMMdd" to false
        }
        return try {
            val format = SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                if (utc) timeZone = TimeZone.getTimeZone("UTC")
            }
            format.parse(value)?.time
        } catch (e: Exception) {
            null
        }
    }
}
