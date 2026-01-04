package com.skeler.scanely.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * PDF Processor for extracting text from PDF documents.
 * 
 * Features:
 * - Sequential page processing (memory efficient)
 * - Per-page language detection
 * - Quality metrics tracking
 * - Progress callbacks
 * - Proper resource cleanup
 */
object PdfProcessor {
    private const val TAG = "PdfProcessor"
    
    // Rendering settings
    private const val TARGET_DPI = 300f
    private const val MAX_PAGE_DIMENSION = 2480 // ~8.5" at 300 DPI
    private const val THUMBNAIL_WIDTH = 400

    data class ProgressUpdate(
        val currentPage: Int,
        val totalPages: Int,
        val extractedText: String,
        val statusMessage: String = "",
        val pageConfidence: Int = 0
    )
    
    data class PdfResult(
        val text: String,
        val thumbnail: Bitmap?,
        val detectedLanguage: String,
        val averageConfidence: Int = 0,
        val pageCount: Int = 0
    )

    /**
     * Extracts text from a PDF Uri by rendering each page as an image
     * and running OCR on it.
     * 
     * Uses script detection on first page to select optimal single language,
     * avoiding multi-language mixing errors.
     *
     * @param context Context
     * @param pdfUri Uri of the PDF file
     * @param ocrHelper Initialized OcrHelper instance
     * @param enabledLanguages User's enabled OCR languages
     * @param onProgress Callback for progress updates
     * @return PdfResult containing text, thumbnail, detected language, and metrics
     */
    suspend fun extractTextFromPdf(
        context: Context,
        pdfUri: Uri,
        ocrHelper: OcrHelper,
        enabledLanguages: List<String>,
        onProgress: (ProgressUpdate) -> Unit
    ): PdfResult = withContext(Dispatchers.IO) {
        val stringBuilder = StringBuilder()
        var fileDescriptor: ParcelFileDescriptor? = null
        var pdfRenderer: PdfRenderer? = null
        var thumbnail: Bitmap? = null
        var detectedLanguage = enabledLanguages.firstOrNull() ?: "eng"
        var totalConfidence = 0
        var processedPages = 0
        
        try {
            fileDescriptor = context.contentResolver.openFileDescriptor(pdfUri, "r")
            
            if (fileDescriptor == null) {
                Log.e(TAG, "Failed to load PDF: FileDescriptor is null")
                return@withContext PdfResult("Error: Could not load PDF file.", null, detectedLanguage)
            }

            pdfRenderer = PdfRenderer(fileDescriptor)
            val pageCount = pdfRenderer.pageCount
            
            Log.d(TAG, "Starting PDF extraction. Total pages: $pageCount, enabled languages: $enabledLanguages")
            
            // === PHASE 1: Generate thumbnail and detect script ===
            if (pageCount > 0 && enabledLanguages.size > 1) {
                onProgress(ProgressUpdate(0, pageCount, "", "Detecting document language..."))
                
                pdfRenderer.openPage(0).use { firstPage ->
                    // Generate thumbnail (smaller for preview)
                    val thumbHeight = (THUMBNAIL_WIDTH * firstPage.height / firstPage.width.toFloat()).toInt()
                    thumbnail = Bitmap.createBitmap(THUMBNAIL_WIDTH, thumbHeight, Bitmap.Config.ARGB_8888)
                    thumbnail?.eraseColor(android.graphics.Color.WHITE)
                    firstPage.render(thumbnail!!, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    
                    // Quick OCR for script detection (use lower res for speed)
                    val detectWidth = 800
                    val detectHeight = (detectWidth * firstPage.height / firstPage.width.toFloat()).toInt()
                    val detectBitmap = Bitmap.createBitmap(detectWidth, detectHeight, Bitmap.Config.ARGB_8888)
                    detectBitmap.eraseColor(android.graphics.Color.WHITE)
                    firstPage.render(detectBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    
                    val preprocessed = ImagePreprocessor.preprocess(detectBitmap, recycleInput = true)
                    val sampleResult = ocrHelper.recognizeText(preprocessed)
                    preprocessed.recycle()
                    
                    if (sampleResult != null && sampleResult.text.length > 20) {
                        detectedLanguage = ScriptDetector.detectAndSelectLanguage(
                            sampleResult.text, 
                            enabledLanguages
                        )
                        Log.d(TAG, "Detected language: $detectedLanguage from sample: '${sampleResult.text.take(50)}...'")
                        
                        // Reinitialize with single detected language for accuracy
                        if (detectedLanguage != enabledLanguages.joinToString("+")) {
                            onProgress(ProgressUpdate(0, pageCount, "", "Optimizing for $detectedLanguage..."))
                            ocrHelper.reinitialize(listOf(detectedLanguage))
                        }
                    }
                }
            }
            
            // Close and reopen PDF for fresh page iteration
            pdfRenderer.close()
            fileDescriptor.close()
            
            fileDescriptor = context.contentResolver.openFileDescriptor(pdfUri, "r")
            pdfRenderer = PdfRenderer(fileDescriptor!!)
            
            // === PHASE 2: Process all pages sequentially ===
            for (i in 0 until pageCount) {
                // Allow cancellation between pages
                yield()
                
                try {
                    pdfRenderer.openPage(i).use { page ->
                        // Calculate dimensions
                        val scale = TARGET_DPI / 72f
                        var width = (page.width * scale).toInt()
                        var height = (page.height * scale).toInt()
                        
                        // Cap dimensions to prevent OOM
                        if (width > MAX_PAGE_DIMENSION || height > MAX_PAGE_DIMENSION) {
                            val ratio = width.toFloat() / height.toFloat()
                            if (width > height) {
                                width = MAX_PAGE_DIMENSION
                                height = (width / ratio).toInt()
                            } else {
                                height = MAX_PAGE_DIMENSION
                                width = (height * ratio).toInt()
                            }
                        }
                        
                        // Render page to bitmap
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        
                        // Generate thumbnail from first page if not already done
                        if (i == 0 && thumbnail == null) {
                            val thumbScale = THUMBNAIL_WIDTH.toFloat() / width
                            thumbnail = Bitmap.createScaledBitmap(
                                bitmap, 
                                THUMBNAIL_WIDTH, 
                                (height * thumbScale).toInt(), 
                                true
                            )
                        }
                        
                        // Early empty page detection to save CPU cycles
                        if (ImagePreprocessor.isLikelyEmpty(bitmap)) {
                            Log.d(TAG, "Page ${i + 1} appears empty, skipping OCR")
                            bitmap.recycle()
                            onProgress(ProgressUpdate(
                                currentPage = i + 1,
                                totalPages = pageCount,
                                extractedText = "",
                                statusMessage = "Page ${i + 1} is blank",
                                pageConfidence = 0
                            ))
                            return@use
                        }
                        
                        // Preprocess and OCR
                        val preprocessedBitmap = ImagePreprocessor.preprocess(bitmap, recycleInput = true)
                        val ocrResult = ocrHelper.recognizeText(preprocessedBitmap)
                        preprocessedBitmap.recycle()
                        
                        val text = ocrResult?.text ?: ""
                        val confidence = ocrResult?.confidence ?: 0
                        
                        totalConfidence += confidence
                        processedPages++
                        
                        // Append text with page separator
                        if (text.isNotEmpty()) {
                            stringBuilder.append("--- Page ${i + 1} ---\n")
                            stringBuilder.append(text)
                            stringBuilder.append("\n\n")
                        }
                        
                        onProgress(ProgressUpdate(
                            currentPage = i + 1, 
                            totalPages = pageCount, 
                            extractedText = text, 
                            statusMessage = "Processing page ${i + 1} of $pageCount",
                            pageConfidence = confidence
                        ))
                        
                        Log.d(TAG, "Page ${i + 1}/$pageCount processed, confidence: $confidence%")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing page $i", e)
                    stringBuilder.append("--- Page ${i + 1} (Error) ---\n\n")
                    onProgress(ProgressUpdate(i + 1, pageCount, "", "Error on page ${i + 1}"))
                }
            }
            
            val averageConfidence = if (processedPages > 0) totalConfidence / processedPages else 0
            
            PdfResult(
                text = stringBuilder.toString(),
                thumbnail = thumbnail,
                detectedLanguage = detectedLanguage,
                averageConfidence = averageConfidence,
                pageCount = pageCount
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Critical PDF extraction error", e)
            PdfResult("Error processing PDF: ${e.message}", thumbnail, detectedLanguage)
        } finally {
            try {
                pdfRenderer?.close()
                fileDescriptor?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing PDF resources", e)
            }
        }
    }
    
    /**
     * Legacy method for backward compatibility.
     */
    suspend fun extractTextFromPdf(
        context: Context,
        pdfUri: Uri,
        ocrHelper: OcrHelper,
        onProgress: (ProgressUpdate) -> Unit
    ): String {
        val result = extractTextFromPdf(
            context, pdfUri, ocrHelper, 
            listOf("eng"),
            onProgress
        )
        return result.text
    }
}

/**
 * Extension to use PdfRenderer.Page with use {} for auto-close.
 */
private inline fun <R> PdfRenderer.Page.use(block: (PdfRenderer.Page) -> R): R {
    return try {
        block(this)
    } finally {
        close()
    }
}
