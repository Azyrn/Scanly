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

// Stability gate: frames a barcode must repeat before accept.
private const val REQUIRED_CONSECUTIVE_MATCHES = 2

class BarcodeAnalyzer(
    private val engine: BarcodeEngine = BarcodeEngine.ML_KIT,
    private val onBarcodeDetected: (List<ScanAction>) -> Unit
) : ImageAnalysis.Analyzer {

    // Set once CameraControl exists; true if zoom applied. ML Kit only — ZXing has no zoom hints.
    var onZoomSuggested: ((Float) -> Boolean)? = null

    private val scanner: BarcodeScanner? = if (engine == BarcodeEngine.ML_KIT) {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
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
                    Barcode.FORMAT_CODE_39
                )
                .setZoomSuggestionOptions(
                    ZoomSuggestionOptions.Builder { zoomRatio ->
                        onZoomSuggested?.invoke(zoomRatio) ?: false
                    }.build()
                )
                .build()
        )
    } else {
        null
    }

    private val zxingDecoder by lazy { ZxingBarcodeDecoder() }

    private var lastProcessedTime = 0L
    private val throttleInterval = 100L

    private var lastRawValues: Set<String> = emptySet()
    private var consecutiveMatches = 0

    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastProcessedTime < throttleInterval) {
            imageProxy.close()
            return
        }

        when (engine) {
            BarcodeEngine.ZXING_CPP -> analyzeWithZxing(imageProxy, currentTime)
            BarcodeEngine.ML_KIT -> analyzeWithMlKit(imageProxy, currentTime)
        }
    }

    private fun analyzeWithZxing(imageProxy: ImageProxy, currentTime: Long) {
        val decoded = try {
            zxingDecoder.decode(imageProxy)
        } catch (e: Exception) {
            Log.e(TAG, "ZXing decode failed", e)
            emptyList()
        } finally {
            imageProxy.close()
        }

        handleStableDetection(decoded.map { it.text }.toSet(), currentTime) {
            decoded.flatMap { it.toActions() }.distinctBy { it.label + getActionKey(it) }
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun analyzeWithMlKit(imageProxy: ImageProxy, currentTime: Long) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        scanner?.process(inputImage)
            ?.addOnSuccessListener { barcodes ->
                handleStableDetection(barcodes.mapNotNull { it.rawValue }.toSet(), currentTime) {
                    barcodes.flatMap { barcode ->
                        mapBarcodeToActions(barcode)
                    }.distinctBy { it.label + getActionKey(it) }
                }
            }
            ?.addOnFailureListener { e ->
                Log.e(TAG, "Barcode scanning failed", e)
            }
            ?.addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun handleStableDetection(
        rawValues: Set<String>,
        currentTime: Long,
        buildActions: () -> List<ScanAction>
    ) {
        if (rawValues.isEmpty()) {
            lastRawValues = emptySet()
            consecutiveMatches = 0
            return
        }

        if (rawValues == lastRawValues) {
            consecutiveMatches++
        } else {
            lastRawValues = rawValues
            consecutiveMatches = 1
        }

        if (consecutiveMatches >= REQUIRED_CONSECUTIVE_MATCHES) {
            lastProcessedTime = currentTime
            val actions = buildActions()
            if (actions.isNotEmpty()) {
                Log.d(TAG, "Confirmed ${actions.size} actions from ${rawValues.size} barcodes")
                onBarcodeDetected(actions)
            }
        }
    }

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
                    actions.add(
                        ScanAction.ConnectWifi(
                            ssid = wifi.ssid ?: "",
                            password = wifi.password,
                            type = wifiType
                        )
                    )
                    wifi.ssid?.let { ssid ->
                        actions.add(ScanAction.CopyText(ssid, "Copy SSID"))
                    }
                }
            }

            Barcode.TYPE_EMAIL -> {
                barcode.email?.let { email ->
                    actions.add(
                        ScanAction.SendEmail(
                            email = email.address ?: rawValue,
                            subject = email.subject,
                            body = email.body
                        )
                    )
                }
            }

            Barcode.TYPE_PHONE -> {
                barcode.phone?.let { phone ->
                    actions.add(ScanAction.CallPhone(phone.number ?: rawValue))
                }
            }

            Barcode.TYPE_SMS -> {
                barcode.sms?.let { sms ->
                    actions.add(
                        ScanAction.SendSms(
                            number = sms.phoneNumber ?: "",
                            message = sms.message
                        )
                    )
                }
            }

            Barcode.TYPE_CONTACT_INFO -> {
                barcode.contactInfo?.let { contact ->
                    val name = contact.name?.formattedName
                    val phone = contact.phones.firstOrNull()?.number
                    val email = contact.emails.firstOrNull()?.address
                    val org = contact.organization

                    actions.add(
                        ScanAction.AddContact(
                            name = name,
                            phone = phone,
                            email = email,
                            organization = org
                        )
                    )
                }
            }

            Barcode.TYPE_GEO -> {
                barcode.geoPoint?.let { geo ->
                    actions.add(ScanAction.OpenUrl("geo:${geo.lat},${geo.lng}"))
                }
            }

            Barcode.TYPE_CALENDAR_EVENT -> {
                barcode.calendarEvent?.let { event ->
                    actions.add(
                        ScanAction.AddEvent(
                            title = event.summary,
                            location = event.location,
                            description = event.description,
                            startRaw = event.start?.rawValue,
                            endRaw = event.end?.rawValue
                        )
                    )
                }
            }

            else -> {
                if (shouldOfferProductLookup(barcode, rawValue)) {
                    actions.add(ScanAction.LookupProduct(rawValue))
                }

                actions.add(ScanAction.ShowRaw(rawValue))
            }
        }

        if (rawValue.isNotBlank() && actions.none { it is ScanAction.CopyText }) {
            actions.add(ScanAction.CopyText(rawValue, "Copy Barcode"))
        }

        return actions
    }

    private fun shouldOfferProductLookup(barcode: Barcode, rawValue: String): Boolean {
        when (barcode.format) {
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E -> return true
        }
        if (barcode.valueType == Barcode.TYPE_ISBN) return true
        return when (barcode.valueType) {
            Barcode.TYPE_TEXT, Barcode.TYPE_UNKNOWN -> isIsbn10(rawValue)
            else -> false
        }
    }

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
        scanner?.close()
    }
}
