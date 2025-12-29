package com.skeler.scanely.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private const val TAG = "MlKitOcrHelper"

/**
 * ML Kit Helper optimized for Barcode/QR Scanning.
 * 
 * Text recognition has been moved to Tesseract.
 */
class MlKitOcrHelper(private val context: Context) {
    
    private val mutex = Mutex()
    private var barcodeScanner: BarcodeScanner? = null
    private var isInitialized = false
    
    /**
     * Initialize ML Kit Barcode Scanner.
     */
    suspend fun initialize(languages: List<String> = emptyList()): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                if (isInitialized && barcodeScanner != null) {
                    return@withContext true
                }
                
                // Configure for all barcode formats
                val options = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                    .build()
                    
                barcodeScanner = BarcodeScanning.getClient(options)
                isInitialized = true
                
                Log.d(TAG, "ML Kit Barcode Scanner initialized")
                true
            } catch (e: Exception) {
                Log.e(TAG, "ML Kit Barcode init failed", e)
                false
            }
        }
    }
    
    /**
     * Scan Barcodes from URI.
     */
    suspend fun scanBarcode(imageUri: Uri): OcrResult? = withContext(Dispatchers.IO) {
         if (!isInitialized) initialize()
         
         try {
             val inputImage = InputImage.fromFilePath(context, imageUri)
             scanFromInputImage(inputImage)
         } catch(e: Exception) {
             Log.e(TAG, "Failed to load image for barcode", e)
             null
         }
    }

    /**
     * Scan Barcodes from Bitmap.
     */
    suspend fun scanBarcode(bitmap: Bitmap): OcrResult? = withContext(Dispatchers.IO) {
        if (!isInitialized) initialize()
        
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            scanFromInputImage(inputImage)
        } catch(e: Exception) {
             Log.e(TAG, "Failed to load bitmap for barcode", e)
             null
        }
    }
    
    /**
     * Internal generic scan function.
     */
    private suspend fun scanFromInputImage(inputImage: InputImage): OcrResult? {
        return mutex.withLock {
             suspendCancellableCoroutine { continuation ->
                 val startTime = System.currentTimeMillis()
                 
                 barcodeScanner?.process(inputImage)
                    ?.addOnSuccessListener { barcodes ->
                        val processingTime = System.currentTimeMillis() - startTime
                        
                        if (barcodes.isNotEmpty()) {
                            val barcode = barcodes.first() // Take the first one for now
                            val rawValue = barcode.rawValue ?: ""
                            val format = barcode.format
                            
                            Log.d(TAG, "Barcode scanned: $rawValue (Format: $format)")
                            
                            continuation.resume(
                                OcrResult(
                                    text = rawValue,
                                    confidence = 100, // Explicit confidence isn't standard in Barcode API
                                    languages = listOf("Barcode"),
                                    processingTimeMs = processingTime
                                )
                            )
                        } else {
                            continuation.resume(null)
                        }
                    }
                    ?.addOnFailureListener { e ->
                        Log.e(TAG, "Barcode scan failed", e)
                        continuation.resume(null)
                    }
                    ?: continuation.resume(null)
             }
        }
    }
    
    // Legacy support for OcrEngine text calls - now essentially no-ops or redirected
    // We remove recognizeText entirely from here as per plan to enforce Tesseract
    
    fun isReady(): Boolean = isInitialized && barcodeScanner != null
    
    fun release() {
        try {
            barcodeScanner?.close()
            barcodeScanner = null
            isInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "Error closing barcode scanner", e)
        }
    }
}
