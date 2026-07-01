package com.skeler.scanely.core.barcode

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.skeler.scanely.core.actions.ScanAction
import com.skeler.scanely.core.actions.WifiType

private const val TAG = "BarcodeAnalyzer"

// Frames a barcode must repeat before it's accepted (stability gate).
private const val REQUIRED_CONSECUTIVE_MATCHES = 2

/**
 * CameraX ImageAnalysis.Analyzer for real-time barcode detection using ML Kit.
 *
 * Features:
 * - Processes camera frames in real-time
 * - Detects QR codes and 1D/2D barcodes
 * - Maps detected barcodes to ScanAction types
 * - Throttles callbacks to prevent UI flooding
 */
class BarcodeAnalyzer(
    private val onBarcodeDetected: (List<ScanAction>) -> Unit
) : ImageAnalysis.Analyzer {

    /**
     * Applies an ML Kit zoom suggestion to the camera. Wired by the camera preview
     * once a [androidx.camera.core.CameraControl] is available. Returns true if the
     * zoom was applied. Auto-zoom helps decode small or distant codes.
     */
    var onZoomSuggested: ((Float) -> Boolean)? = null

    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            // Only the formats the app actually acts on — narrower list = faster detection.
            Barcode.FORMAT_QR_CODE,
            Barcode.FORMAT_AZTEC,
            Barcode.FORMAT_DATA_MATRIX,
            Barcode.FORMAT_PDF417,
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_CODE_39
        )
        .setZoomSuggestionOptions(
            ZoomSuggestionOptions.Builder { zoomRatio ->
                onZoomSuggested?.invoke(zoomRatio) ?: false
            }.build()
        )
        .build()

    private val scanner: BarcodeScanner = BarcodeScanning.getClient(options)

    // Throttle: minimum time between callbacks (ms)
    private var lastProcessedTime = 0L
    private val throttleInterval = 100L

    // Stability gate: only accept a result after it repeats across consecutive frames,
    // to avoid flickering / one-off misreads.
    private var lastRawValues: Set<String> = emptySet()
    private var consecutiveMatches = 0

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastProcessedTime < throttleInterval) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val rawValues = barcodes.mapNotNull { it.rawValue }.toSet()
                if (rawValues.isEmpty()) {
                    // Nothing decodable this frame — reset the confirmation streak.
                    lastRawValues = emptySet()
                    consecutiveMatches = 0
                    return@addOnSuccessListener
                }

                if (rawValues == lastRawValues) {
                    consecutiveMatches++
                } else {
                    lastRawValues = rawValues
                    consecutiveMatches = 1
                }

                if (consecutiveMatches >= REQUIRED_CONSECUTIVE_MATCHES) {
                    lastProcessedTime = currentTime
                    val actions = barcodes.flatMap { barcode ->
                        mapBarcodeToActions(barcode)
                    }.distinctBy { it.label + getActionKey(it) }

                    if (actions.isNotEmpty()) {
                        Log.d(TAG, "Confirmed ${actions.size} actions from ${barcodes.size} barcodes")
                        onBarcodeDetected(actions)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Barcode scanning failed", e)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    /**
     * Map ML Kit Barcode to ScanAction list.
     */
    private fun mapBarcodeToActions(barcode: Barcode): List<ScanAction> {
        val actions = mutableListOf<ScanAction>()
        val rawValue = barcode.rawValue ?: return actions

        when (barcode.valueType) {
            Barcode.TYPE_URL -> {
                barcode.url?.let { url ->
                    actions.add(ScanAction.OpenUrl(url.url ?: rawValue))
                }
            }

            Barcode.TYPE_WIFI -> {
                barcode.wifi?.let { wifi ->
                    val wifiType = when (wifi.encryptionType) {
                        Barcode.WiFi.TYPE_WPA -> WifiType.WPA
                        Barcode.WiFi.TYPE_WEP -> WifiType.WEP
                        else -> WifiType.OPEN
                    }
                    actions.add(ScanAction.ConnectWifi(
                        ssid = wifi.ssid ?: "",
                        password = wifi.password,
                        type = wifiType
                    ))
                    wifi.ssid?.let { ssid ->
                        actions.add(ScanAction.CopyText(ssid, "Copy SSID"))
                    }
                }
            }

            Barcode.TYPE_EMAIL -> {
                barcode.email?.let { email ->
                    actions.add(ScanAction.SendEmail(
                        email = email.address ?: rawValue,
                        subject = email.subject,
                        body = email.body
                    ))
                }
            }

            Barcode.TYPE_PHONE -> {
                barcode.phone?.let { phone ->
                    actions.add(ScanAction.CallPhone(phone.number ?: rawValue))
                }
            }

            Barcode.TYPE_SMS -> {
                barcode.sms?.let { sms ->
                    actions.add(ScanAction.SendSms(
                        number = sms.phoneNumber ?: "",
                        message = sms.message
                    ))
                }
            }

            Barcode.TYPE_CONTACT_INFO -> {
                barcode.contactInfo?.let { contact ->
                    val name = contact.name?.formattedName
                    val phone = contact.phones.firstOrNull()?.number
                    val email = contact.emails.firstOrNull()?.address
                    val org = contact.organization

                    actions.add(ScanAction.AddContact(
                        name = name,
                        phone = phone,
                        email = email,
                        organization = org
                    ))
                }
            }

            Barcode.TYPE_GEO -> {
                // Geo links can be opened as URLs
                actions.add(ScanAction.OpenUrl("geo:${barcode.geoPoint?.lat},${barcode.geoPoint?.lng}"))
            }

            Barcode.TYPE_CALENDAR_EVENT -> {
                barcode.calendarEvent?.let { event ->
                    actions.add(ScanAction.AddEvent(
                        title = event.summary,
                        location = event.location,
                        description = event.description,
                        startRaw = event.start?.rawValue,
                        endRaw = event.end?.rawValue
                    ))
                }
            }

            else -> {
                // Offer product lookup for retail/ISBN symbologies (see helper).
                if (shouldOfferProductLookup(barcode, rawValue)) {
                    actions.add(ScanAction.LookupProduct(rawValue))
                }

                // Also add raw text option
                actions.add(ScanAction.ShowRaw(rawValue))
            }
        }

        // Always add copy option for raw value
        if (rawValue.isNotBlank() && actions.none { it is ScanAction.CopyText }) {
            actions.add(ScanAction.CopyText(rawValue, "Copy Barcode"))
        }

        return actions
    }

    /**
     * Decide whether a barcode should offer product lookup.
     *
     * Fast path (O(1)): trust ML Kit's detected symbology/type for retail codes —
     * EAN-8/13, UPC-A/E, and anything classified as ISBN. Only when the payload is
     * otherwise unclassified do we run a cheap ISBN-10 shape check. No regexes and no
     * allocations, so the per-frame hot path stays constant-time. Broad numeric strings
     * are intentionally NOT treated as products.
     */
    private fun shouldOfferProductLookup(barcode: Barcode, rawValue: String): Boolean {
        when (barcode.format) {
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E -> return true
        }
        if (barcode.valueType == Barcode.TYPE_ISBN) return true
        // Fallback only for unclassified payloads that strongly match ISBN-10.
        return when (barcode.valueType) {
            Barcode.TYPE_TEXT, Barcode.TYPE_UNKNOWN -> isIsbn10(rawValue)
            else -> false
        }
    }

    /** ISBN-10 shape: 10 chars, first 9 digits, last a digit or 'X'. O(1), no allocation. */
    private fun isIsbn10(value: String): Boolean {
        if (value.length != 10) return false
        for (i in 0 until 9) {
            if (!value[i].isDigit()) return false
        }
        val last = value[9]
        return last.isDigit() || last == 'X' || last == 'x'
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

    fun close() {
        scanner.close()
    }
}
