package com.skeler.scanely.core.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val TAG = "MlKitOcrService"

/**
 * On-Device Text Recognition service using Google ML Kit.
 *
 * Features:
 * - Fully offline (neural network model bundled in APK)
 * - ~50-200ms processing time (hardware-accelerated)
 * - Supports Latin script with high accuracy
 * - Paragraph/line/block detection
 */
@Singleton
class MlKitOcrService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pdfRendererHelper: PdfRendererHelper
) {

    // Created lazily so the ML Kit client isn't built on the main thread while
    // the first screen composes (this service is injected into a ViewModel).
    // First touch happens from recognizeFromBitmap on a background dispatcher.
    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Recognize text from an image URI.
     *
     * @param imageUri URI of the image to process
     * @return OcrResult containing extracted text or error
     */
    suspend fun recognizeFromUri(imageUri: Uri): OcrResult = withContext(Dispatchers.IO) {
        try {
            val bitmap = loadBitmapFromUri(imageUri)
                ?: return@withContext OcrResult.Error("Failed to load image")

            recognizeFromBitmap(bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Recognition failed", e)
            OcrResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Recognize text from a Bitmap.
     *
     * @param bitmap Source bitmap (preferably high-resolution)
     * @return OcrResult containing extracted text or error
     */
    suspend fun recognizeFromBitmap(bitmap: Bitmap): OcrResult = suspendCancellableCoroutine { continuation ->
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(inputImage)
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

                // Calculate average confidence from all elements
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
                Log.e(TAG, "ML Kit processing failed", e)
                continuation.resume(OcrResult.Error(e.message ?: "Recognition failed"))
            }
    }

    /**
     * Recognize text from a PDF document.
     *
     * @param pdfUri URI of the PDF
     * @param pageIndex Page to process (0-indexed), or null for all pages
     * @return OcrResult with combined text from all processed pages
     */
    suspend fun recognizeFromPdf(pdfUri: Uri, pageIndex: Int? = null): OcrResult = withContext(Dispatchers.IO) {
        try {
            if (pageIndex != null) {
                // Single page
                val bitmap = pdfRendererHelper.renderPage(pdfUri, pageIndex)
                    ?: return@withContext OcrResult.Error("Failed to render PDF page $pageIndex")

                recognizeFromBitmap(bitmap)
            } else {
                // All pages
                val bitmaps = pdfRendererHelper.renderAllPages(pdfUri)
                if (bitmaps.isEmpty()) {
                    return@withContext OcrResult.Error("Failed to render PDF pages")
                }

                val allText = StringBuilder()
                val allBlocks = mutableListOf<TextBlockData>()
                var totalConfidence = 0f
                var successCount = 0

                bitmaps.forEachIndexed { index, bitmap ->
                    when (val result = recognizeFromBitmap(bitmap)) {
                        is OcrResult.Success -> {
                            if (allText.isNotEmpty()) {
                                allText.append("\n\n--- Page ${index + 1} ---\n\n")
                            }
                            allText.append(result.text)
                            allBlocks.addAll(result.blocks)
                            totalConfidence += result.confidence
                            successCount++
                        }
                        is OcrResult.Empty -> {
                            // Skip empty pages
                        }
                        is OcrResult.Error -> {
                            Log.w(TAG, "Page $index failed: ${result.message}")
                        }
                    }
                    // Recycle bitmap after processing
                    bitmap.recycle()
                }

                if (allText.isEmpty()) {
                    OcrResult.Empty
                } else {
                    OcrResult.Success(
                        text = allText.toString(),
                        blocks = allBlocks,
                        confidence = if (successCount > 0) totalConfidence / successCount else 0f
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "PDF recognition failed", e)
            OcrResult.Error(e.message ?: "PDF processing failed")
        }
    }

    /**
     * Load a Bitmap from a URI.
     */
    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bitmap from URI", e)
            null
        }
    }

    /**
     * Release ML Kit resources.
     */
    fun close() {
        recognizer.close()
    }
}
