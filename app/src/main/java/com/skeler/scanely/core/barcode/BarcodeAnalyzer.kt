package com.skeler.scanely.core.barcode

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.skeler.scanely.core.actions.ScanAction
import com.skeler.scanely.core.actions.WifiType

private const val TAG = "BarcodeAnalyzer"

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

    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_QR_CODE,
            Barcode.FORMAT_AZTEC,
            Barcode.FORMAT_DATA_MATRIX,
            Barcode.FORMAT_PDF417,
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_CODE_39,
            Barcode.FORMAT_CODE_93,
            Barcode.FORMAT_CODABAR,
            Barcode.FORMAT_ITF
        )
        .build()

    private val scanner: BarcodeScanner = BarcodeScanning.getClient(options)

    // Throttle: minimum time between callbacks (ms)
    private var lastProcessedTime = 0L
    private val throttleInterval = 100L

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
                if (barcodes.isNotEmpty()) {
                    lastProcessedTime = currentTime
                    val actions = barcodes.flatMap { barcode ->
                        mapBarcodeToActions(barcode)
                    }.distinctBy { it.label + getActionKey(it) }
                    
                    if (actions.isNotEmpty()) {
                        Log.d(TAG, "Detected ${actions.size} actions from ${barcodes.size} barcodes")
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

            else -> {
                // Fallback: treat as raw text
                actions.add(ScanAction.ShowRaw(rawValue))
            }
        }

        // Always add copy option for raw value
        if (rawValue.isNotBlank() && actions.none { it is ScanAction.CopyText }) {
            actions.add(ScanAction.CopyText(rawValue, "Copy Barcode"))
        }

        return actions
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
            is ScanAction.ShowRaw -> action.text.take(50)
        }
    }

    fun close() {
        scanner.close()
    }
}
