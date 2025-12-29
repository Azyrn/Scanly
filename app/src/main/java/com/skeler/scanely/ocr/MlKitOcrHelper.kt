package com.skeler.scanely.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private const val TAG = "MlKitOcrHelper"

/**
 * ML Kit Text Recognition OCR helper.
 * Provides high-quality text recognition for Latin script.
 * 
 * Uses Google's ML Kit which runs on-device for privacy.
 */
class MlKitOcrHelper(private val context: Context) {
    
    private val mutex = Mutex()
    private var recognizer: TextRecognizer? = null
    private var isInitialized = false
    
    /**
     * Initialize ML Kit Text Recognizer.
     * ML Kit automatically handles model downloading and initialization.
     * 
     * @param languages Ignored for ML Kit (uses Latin recognizer)
     * @return true if initialization succeeded
     */
    suspend fun initialize(languages: List<String> = emptyList()): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                if (isInitialized && recognizer != null) {
                    return@withContext true
                }
                
                // Create text recognizer with Latin script options
                recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                isInitialized = true
                
                Log.d(TAG, "ML Kit Text Recognizer initialized successfully")
                true
            } catch (e: Exception) {
                Log.e(TAG, "ML Kit initialization failed", e)
                false
            }
        }
    }
    
    /**
     * Recognize text from an image URI.
     */
    suspend fun recognizeText(imageUri: Uri): OcrResult? = withContext(Dispatchers.IO) {
        if (!isInitialized || recognizer == null) {
            Log.e(TAG, "MlKitOcrHelper not initialized")
            return@withContext null
        }
        
        try {
            val inputImage = InputImage.fromFilePath(context, imageUri)
            recognizeFromInputImage(inputImage)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create InputImage from URI", e)
            null
        }
    }
    
    /**
     * Recognize text from a bitmap.
     * Note: ML Kit handles thread safety internally, so no mutex is needed.
     */
    suspend fun recognizeText(bitmap: Bitmap): OcrResult? = withContext(Dispatchers.IO) {
        if (!isInitialized || recognizer == null) {
            Log.e(TAG, "MlKitOcrHelper not initialized")
            return@withContext null
        }
        
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            recognizeFromInputImage(inputImage)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create InputImage from Bitmap", e)
            null
        }
    }
    
    private suspend fun recognizeFromInputImage(inputImage: InputImage): OcrResult? {
        return suspendCancellableCoroutine { continuation ->
            val startTime = System.currentTimeMillis()
            
            recognizer?.process(inputImage)
                ?.addOnSuccessListener { visionText ->
                    val processingTime = System.currentTimeMillis() - startTime
                    
                    val text = visionText.text
                    
                    // Calculate confidence from block-level confidence
                    val confidences = visionText.textBlocks.mapNotNull { block ->
                        block.lines.flatMap { line ->
                            line.elements.mapNotNull { it.confidence }
                        }
                    }.flatten()
                    
                    val avgConfidence = if (confidences.isNotEmpty()) {
                        (confidences.average() * 100).toInt()
                    } else {
                        0
                    }
                    
                    Log.d(TAG, "OCR completed in ${processingTime}ms, confidence: $avgConfidence%")
                    
                    continuation.resume(
                        OcrResult(
                            text = text,
                            confidence = avgConfidence,
                            languages = listOf("eng"), // ML Kit Latin
                            processingTimeMs = processingTime
                        )
                    )
                }
                ?.addOnFailureListener { e ->
                    Log.e(TAG, "ML Kit text recognition failed", e)
                    continuation.resume(null)
                }
                ?: continuation.resume(null)
        }
    }
    
    /**
     * Check if the helper is ready.
     */
    fun isReady(): Boolean = isInitialized && recognizer != null
    
    /**
     * Release ML Kit resources.
     */
    fun release() {
        try {
            recognizer?.close()
            recognizer = null
            isInitialized = false
            Log.d(TAG, "ML Kit resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing ML Kit", e)
        }
    }
    
    /**
     * Reinitialize (for compatibility with OcrHelper interface).
     */
    suspend fun reinitialize(languages: List<String>): Boolean {
        release()
        return initialize(languages)
    }
}
