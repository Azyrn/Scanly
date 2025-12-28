package com.skeler.scanely.scanner

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "BarcodeScanner"

/**
 * Barcode data holder
 */
data class BarcodeResult(
    val rawValue: String,
    val displayValue: String,
    val format: Int,
    val formatName: String,
    val valueType: Int
)

/**
 * ML Kit Barcode Scanner wrapper.
 * Supports QR, Code128, EAN, PDF417, Data Matrix, and more.
 */
object BarcodeScanner {
    
    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_ALL_FORMATS
        )
        .build()
    
    private val scanner = BarcodeScanning.getClient(options)
    
    /**
     * Scan barcodes from a Bitmap.
     */
    suspend fun scanBitmap(bitmap: Bitmap): List<BarcodeResult> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            
            scanner.process(inputImage)
                .addOnSuccessListener { barcodes ->
                    val results = barcodes.map { barcode ->
                        BarcodeResult(
                            rawValue = barcode.rawValue ?: "",
                            displayValue = barcode.displayValue ?: barcode.rawValue ?: "",
                            format = barcode.format,
                            formatName = getFormatName(barcode.format),
                            valueType = barcode.valueType
                        )
                    }
                    Log.d(TAG, "Scanned ${results.size} barcodes")
                    continuation.resume(results)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Barcode scanning failed", e)
                    continuation.resumeWithException(e)
                }
        }
    }
    
    /**
     * Get human-readable format name
     */
    fun getFormatName(format: Int): String = when (format) {
        Barcode.FORMAT_QR_CODE -> "QR Code"
        Barcode.FORMAT_CODE_128 -> "Code 128"
        Barcode.FORMAT_CODE_39 -> "Code 39"
        Barcode.FORMAT_CODE_93 -> "Code 93"
        Barcode.FORMAT_EAN_8 -> "EAN-8"
        Barcode.FORMAT_EAN_13 -> "EAN-13"
        Barcode.FORMAT_UPC_A -> "UPC-A"
        Barcode.FORMAT_UPC_E -> "UPC-E"
        Barcode.FORMAT_PDF417 -> "PDF417"
        Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
        Barcode.FORMAT_AZTEC -> "Aztec"
        Barcode.FORMAT_ITF -> "ITF"
        Barcode.FORMAT_CODABAR -> "Codabar"
        else -> "Unknown"
    }
    
    /**
     * Close the scanner when done.
     */
    fun close() {
        scanner.close()
    }
}
