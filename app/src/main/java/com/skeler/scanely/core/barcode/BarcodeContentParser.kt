package com.skeler.scanely.core.barcode

import android.net.Uri
import com.skeler.scanely.core.actions.ScanAction
import com.skeler.scanely.core.actions.WifiType

// Mirrors ML Kit's structured-value parsing so both engines yield identical actions.
object BarcodeContentParser {

    fun parse(barcode: DecodedBarcode): List<ScanAction> {
        val text = barcode.text
        val actions = mutableListOf<ScanAction>()

        when {
            text.startsWith("WIFI:", ignoreCase = true) -> parseWifi(text, actions)

            text.startsWith("http://", ignoreCase = true) ||
                text.startsWith("https://", ignoreCase = true) ->
                actions.add(ScanAction.OpenUrl(text))

            text.startsWith("www.", ignoreCase = true) ->
                actions.add(ScanAction.OpenUrl("https://$text"))

            text.startsWith("mailto:", ignoreCase = true) -> parseMailto(text, actions)

            text.startsWith("MATMSG:", ignoreCase = true) -> parseMatmsg(text, actions)

            text.startsWith("tel:", ignoreCase = true) ->
                actions.add(ScanAction.CallPhone(text.substring(4).trim()))

            text.startsWith("smsto:", ignoreCase = true) ||
                text.startsWith("sms:", ignoreCase = true) -> parseSms(text, actions)

            text.startsWith("BEGIN:VCARD", ignoreCase = true) -> parseVCard(text, actions)

            text.startsWith("MECARD:", ignoreCase = true) -> parseMeCard(text, actions)

            text.startsWith("geo:", ignoreCase = true) ->
                actions.add(ScanAction.OpenUrl(text))

            text.contains("BEGIN:VEVENT", ignoreCase = true) -> parseVEvent(text, actions)

            else -> {
                if (barcode.isProductCode || isIsbn10(text)) {
                    actions.add(ScanAction.LookupProduct(text))
                }
                actions.add(ScanAction.ShowRaw(text))
            }
        }

        if (text.isNotBlank() && actions.none { it is ScanAction.CopyText }) {
            actions.add(ScanAction.CopyText(text, "Copy Barcode"))
        }
        return actions
    }

    private fun parseWifi(text: String, actions: MutableList<ScanAction>) {
        val fields = splitQrFields(text.substring(5))
        val ssid = fields["S"]
        if (ssid.isNullOrEmpty()) {
            actions.add(ScanAction.ShowRaw(text))
            return
        }
        val wifiType = when (fields["T"]?.uppercase()) {
            "WPA", "WPA2", "WPA3", "SAE" -> WifiType.WPA
            "WEP" -> WifiType.WEP
            else -> WifiType.OPEN
        }
        actions.add(
            ScanAction.ConnectWifi(
                ssid = ssid,
                password = fields["P"]?.takeIf { it.isNotEmpty() },
                type = wifiType
            )
        )
        actions.add(ScanAction.CopyText(ssid, "Copy SSID"))
    }

    private fun parseMailto(text: String, actions: MutableList<ScanAction>) {
        val address = Uri.decode(text.substring(7).substringBefore('?'))
        if (address.isBlank()) {
            actions.add(ScanAction.ShowRaw(text))
            return
        }
        var subject: String? = null
        var body: String? = null
        text.substringAfter('?', "").split('&').forEach { param ->
            val value = Uri.decode(param.substringAfter('=', ""))
            when (param.substringBefore('=').lowercase()) {
                "subject" -> subject = value
                "body" -> body = value
            }
        }
        actions.add(ScanAction.SendEmail(address, subject, body))
    }

    private fun parseMatmsg(text: String, actions: MutableList<ScanAction>) {
        val fields = splitQrFields(text.substring(7))
        val to = fields["TO"]
        if (to.isNullOrBlank()) {
            actions.add(ScanAction.ShowRaw(text))
            return
        }
        actions.add(ScanAction.SendEmail(to, fields["SUB"], fields["BODY"]))
    }

    private fun parseSms(text: String, actions: MutableList<ScanAction>) {
        val rest = text.substringAfter(':')
        val number: String
        val message: String?
        if (text.startsWith("smsto:", ignoreCase = true)) {
            number = rest.substringBefore(':').trim()
            message = rest.substringAfter(':', "").takeIf { it.isNotEmpty() }
        } else {
            number = rest.substringBefore('?').trim()
            message = text.substringAfter("body=", "")
                .substringBefore('&')
                .takeIf { it.isNotEmpty() }
                ?.let { Uri.decode(it) }
        }
        if (number.isBlank()) {
            actions.add(ScanAction.ShowRaw(text))
            return
        }
        actions.add(ScanAction.SendSms(number, message))
    }

    private fun parseVCard(text: String, actions: MutableList<ScanAction>) {
        var name: String? = null
        var phone: String? = null
        var email: String? = null
        var org: String? = null
        text.lineSequence().forEach { line ->
            val idx = line.indexOf(':')
            if (idx <= 0) return@forEach
            val key = line.take(idx).substringBefore(';').uppercase().trim()
            val value = line.substring(idx + 1).trim()
            if (value.isEmpty()) return@forEach
            when (key) {
                "FN" -> name = value
                "N" -> if (name == null) {
                    name = value.split(';').filter { it.isNotBlank() }.reversed().joinToString(" ")
                }
                "TEL" -> if (phone == null) phone = value
                "EMAIL" -> if (email == null) email = value
                "ORG" -> org = value.substringBefore(';')
            }
        }
        if (name == null && phone == null && email == null) {
            actions.add(ScanAction.ShowRaw(text))
            return
        }
        actions.add(ScanAction.AddContact(name, phone, email, org))
    }

    private fun parseMeCard(text: String, actions: MutableList<ScanAction>) {
        val fields = splitQrFields(text.substring(7))
        // MECARD N is "last,first"
        val name = fields["N"]?.split(',')?.filter { it.isNotBlank() }?.let { parts ->
            if (parts.size >= 2) "${parts[1].trim()} ${parts[0].trim()}" else parts.joinToString(" ")
        }
        val phone = fields["TEL"]
        val email = fields["EMAIL"]
        if (name == null && phone == null && email == null) {
            actions.add(ScanAction.ShowRaw(text))
            return
        }
        actions.add(ScanAction.AddContact(name, phone, email, fields["ORG"]))
    }

    private fun parseVEvent(text: String, actions: MutableList<ScanAction>) {
        var title: String? = null
        var location: String? = null
        var description: String? = null
        var start: String? = null
        var end: String? = null
        text.lineSequence().forEach { line ->
            val idx = line.indexOf(':')
            if (idx <= 0) return@forEach
            val key = line.take(idx).substringBefore(';').uppercase().trim()
            val value = line.substring(idx + 1).trim()
            if (value.isEmpty()) return@forEach
            when (key) {
                "SUMMARY" -> title = value
                "LOCATION" -> location = value
                "DESCRIPTION" -> description = value
                "DTSTART" -> start = value
                "DTEND" -> end = value
            }
        }
        actions.add(ScanAction.AddEvent(title, location, description, start, end))
    }

    // Splits "K:V;K:V;;" QR payloads honoring backslash escapes; first occurrence wins.
    private fun splitQrFields(body: String): Map<String, String> {
        val parts = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < body.length) {
            val c = body[i]
            when {
                c == '\\' && i + 1 < body.length -> {
                    sb.append(body[i + 1])
                    i++
                }
                c == ';' -> {
                    parts.add(sb.toString())
                    sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        if (sb.isNotEmpty()) parts.add(sb.toString())

        val fields = mutableMapOf<String, String>()
        for (part in parts) {
            val idx = part.indexOf(':')
            if (idx <= 0) continue
            val key = part.take(idx).uppercase().trim()
            if (key !in fields) fields[key] = part.substring(idx + 1)
        }
        return fields
    }

    private fun isIsbn10(value: String): Boolean {
        if (value.length != 10) return false
        for (i in 0 until 9) {
            if (!value[i].isDigit()) return false
        }
        val last = value[9]
        return last.isDigit() || last == 'X' || last == 'x'
    }
}
