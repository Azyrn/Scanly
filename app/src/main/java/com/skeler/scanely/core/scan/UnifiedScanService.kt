package com.skeler.scanely.core.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.skeler.scanely.core.actions.ScanAction
import com.skeler.scanely.core.actions.ScanActionDetector
import com.skeler.scanely.core.actions.WifiType
import com.skeler.scanely.core.ocr.OcrResult
import com.skeler.scanely.core.ocr.TextBlockData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val TAG = "UnifiedScanService"

/**
 * Unified scanning service that performs parallel OCR and barcode detection.
 * 
 * Features:
 * - Runs ML Kit Text Recognition and Barcode Scanning in parallel
 * - Combines results into a single UnifiedScanResult
 * - Supports barcode-only mode for camera screen gallery picker
 * - Detects smart actions (URLs, emails, phones) from OCR text
 */
@Singleton
class UnifiedScanService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val textRecognizer: TextRecognizer = 
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    private val barcodeScanner: BarcodeScanner = BarcodeScanning.getClient(
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
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_CODE_93,
                Barcode.FORMAT_CODABAR,
                Barcode.FORMAT_ITF
            )
            .build()
    )

    /**
     * Perform unified scan: Barcode detection first, OCR only if no barcodes.
     * If both fail, retry with image preprocessing for difficult images.
     */
    suspend fun scanImage(uri: Uri): UnifiedScanResult = withContext(Dispatchers.IO) {
        val bitmap = loadBitmapFromUri(uri)
            ?: return@withContext UnifiedScanResult(
                textResult = OcrResult.Error("Failed to load image"),
                barcodeActions = emptyList(),
                textActions = emptyList()
            )

        try {
            // First attempt: scan original image
            val result = scanBitmap(bitmap)
            
            // If scan found nothing, retry with preprocessing
            if (result.isEmpty) {
                Log.d(TAG, "Initial scan empty, retrying with preprocessing")
                val preprocessed = com.skeler.scanely.core.image.ImagePreprocessor.preprocessForOcr(bitmap)
                try {
                    val retryResult = scanBitmap(preprocessed)
                    if (!retryResult.isEmpty) {
                        Log.d(TAG, "Preprocessing helped - found content on retry")
                        return@withContext retryResult
                    }
                } finally {
                    if (preprocessed != bitmap) preprocessed.recycle()
                }
            }
            
            result
        } finally {
            bitmap.recycle()
        }
    }
    
    /**
     * Internal scan helper for a bitmap.
     */
    private suspend fun scanBitmap(bitmap: Bitmap): UnifiedScanResult {
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        // Run barcode detection first
        val barcodes = detectBarcodes(inputImage)

        // If barcodes found, skip OCR (QR-only mode)
        if (barcodes.isNotEmpty()) {
            return UnifiedScanResult(
                textResult = null,
                barcodeActions = barcodes,
                textActions = emptyList()
            )
        }

        // No barcodes - run OCR
        val ocrResult = recognizeText(inputImage)

        // Detect smart actions from OCR text (stricter validation)
        val textActions = if (ocrResult is OcrResult.Success) {
            ScanActionDetector.detectActions(ocrResult.text)
        } else {
            emptyList()
        }

        return UnifiedScanResult(
            textResult = ocrResult,
            barcodeActions = emptyList(),
            textActions = textActions
        )
    }

    /**
     * Barcode-only scan for camera screen gallery picker.
     * No OCR to avoid noise.
     */
    suspend fun scanBarcodeOnly(uri: Uri): List<ScanAction> = withContext(Dispatchers.IO) {
        val bitmap = loadBitmapFromUri(uri) ?: return@withContext emptyList()

        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            detectBarcodes(inputImage)
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun recognizeText(inputImage: InputImage): OcrResult = 
        suspendCancellableCoroutine { continuation ->
            textRecognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    if (visionText.text.isBlank()) {
                        continuation.resume(OcrResult.Empty)
                        return@addOnSuccessListener
                    }

                    val blocks = visionText.textBlocks.map { block ->
                        TextBlockData(
                            text = block.text,
                            boundingBoxLeft = block.boundingBox?.left ?: 0,
                            boundingBoxTop = block.boundingBox?.top ?: 0,
                            boundingBoxRight = block.boundingBox?.right ?: 0,
                            boundingBoxBottom = block.boundingBox?.bottom ?: 0
                        )
                    }

                    val allConfidences = visionText.textBlocks.flatMap { block ->
                        block.lines.flatMap { line ->
                            line.elements.mapNotNull { it.confidence }
                        }
                    }
                    val avgConfidence = if (allConfidences.isNotEmpty()) {
                        allConfidences.average().toFloat()
                    } else {
                        1.0f
                    }

                    continuation.resume(
                        OcrResult.Success(
                            text = visionText.text,
                            blocks = blocks,
                            confidence = avgConfidence
                        )
                    )
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Text recognition failed", e)
                    continuation.resume(OcrResult.Error(e.message ?: "Recognition failed"))
                }
        }

    private suspend fun detectBarcodes(inputImage: InputImage): List<ScanAction> = 
        suspendCancellableCoroutine { continuation ->
            barcodeScanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    // Dedupe by raw value to prevent duplicate barcode entries
                    val uniqueBarcodes = barcodes
                        .distinctBy { it.rawValue ?: "" }
                        .filter { it.rawValue?.isNotBlank() == true }
                    
                    val actions = uniqueBarcodes.flatMap { barcode ->
                        mapBarcodeToActions(barcode)
                    }
                    
                    continuation.resume(actions)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Barcode detection failed", e)
                    continuation.resume(emptyList())
                }
        }

    private fun mapBarcodeToActions(barcode: Barcode): List<ScanAction> {
        val rawValue = barcode.rawValue ?: return emptyList()

        // Return ONLY the primary action for this barcode type (no duplicates)
        val primaryAction: ScanAction = when (barcode.valueType) {
            Barcode.TYPE_URL -> {
                barcode.url?.let { url ->
                    ScanAction.OpenUrl(url.url ?: rawValue)
                } ?: ScanAction.CopyText(rawValue, "Copy URL")
            }
            Barcode.TYPE_WIFI -> {
                barcode.wifi?.let { wifi ->
                    val wifiType = when (wifi.encryptionType) {
                        Barcode.WiFi.TYPE_WPA -> WifiType.WPA
                        Barcode.WiFi.TYPE_WEP -> WifiType.WEP
                        else -> WifiType.OPEN
                    }
                    ScanAction.ConnectWifi(
                        ssid = wifi.ssid ?: "",
                        password = wifi.password,
                        type = wifiType
                    )
                } ?: ScanAction.CopyText(rawValue, "Copy")
            }
            Barcode.TYPE_EMAIL -> {
                barcode.email?.let { email ->
                    ScanAction.SendEmail(
                        email = email.address ?: rawValue,
                        subject = email.subject,
                        body = email.body
                    )
                } ?: ScanAction.CopyText(rawValue, "Copy Email")
            }
            Barcode.TYPE_PHONE -> {
                barcode.phone?.let { phone ->
                    ScanAction.CallPhone(phone.number ?: rawValue)
                } ?: ScanAction.CopyText(rawValue, "Copy Phone")
            }
            Barcode.TYPE_SMS -> {
                barcode.sms?.let { sms ->
                    ScanAction.SendSms(
                        number = sms.phoneNumber ?: "",
                        message = sms.message
                    )
                } ?: ScanAction.CopyText(rawValue, "Copy")
            }
            Barcode.TYPE_CONTACT_INFO -> {
                barcode.contactInfo?.let { contact ->
                    ScanAction.AddContact(
                        name = contact.name?.formattedName,
                        phone = contact.phones.firstOrNull()?.number,
                        email = contact.emails.firstOrNull()?.address,
                        organization = contact.organization
                    )
                } ?: ScanAction.CopyText(rawValue, "Copy")
            }
            Barcode.TYPE_GEO -> {
                ScanAction.OpenUrl("geo:${barcode.geoPoint?.lat},${barcode.geoPoint?.lng}")
            }
            else -> {
                // For raw/unknown barcodes, just use Copy (cleaner than ShowRaw + Copy)
                ScanAction.CopyText(rawValue, "Copy")
            }
        }

        return listOf(primaryAction)
    }

    companion object {
        /** Maximum dimension for loaded bitmaps to prevent OOM */
        private const val MAX_BITMAP_DIMENSION = 2048
    }

    /**
     * Load bitmap from URI with downsampling to prevent OOM on large images.
     * Max dimension is capped at [MAX_BITMAP_DIMENSION] pixels.
     */
    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                // First pass: decode bounds only to calculate sample size
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)
                
                // Calculate inSampleSize
                options.inSampleSize = calculateInSampleSize(
                    options.outWidth, 
                    options.outHeight, 
                    MAX_BITMAP_DIMENSION
                )
                options.inJustDecodeBounds = false
                
                // Re-open stream for actual decode (stream was consumed)
                context.contentResolver.openInputStream(uri)?.use { newStream ->
                    BitmapFactory.decodeStream(newStream, null, options)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bitmap", e)
            null
        }
    }

    /**
     * Calculate optimal sample size for downsampling.
     * Uses power-of-2 scaling for efficiency.
     */
    private fun calculateInSampleSize(
        width: Int, 
        height: Int, 
        maxDimension: Int
    ): Int {
        var inSampleSize = 1
        
        if (width > maxDimension || height > maxDimension) {
            val halfWidth = width / 2
            val halfHeight = height / 2
            
            // Calculate the largest inSampleSize that keeps dimensions > maxDimension
            while ((halfWidth / inSampleSize) >= maxDimension || 
                   (halfHeight / inSampleSize) >= maxDimension) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }

    fun close() {
        textRecognizer.close()
        barcodeScanner.close()
    }
}

/**
 * Combined result from unified scanning.
 */
data class UnifiedScanResult(
    val textResult: OcrResult?,
    val barcodeActions: List<ScanAction>,
    val textActions: List<ScanAction>
) {
    val hasText: Boolean get() = textResult is OcrResult.Success
    val hasBarcodes: Boolean get() = barcodeActions.isNotEmpty()
    val hasSmartActions: Boolean get() = textActions.isNotEmpty()
    val isEmpty: Boolean get() = !hasText && !hasBarcodes
}
