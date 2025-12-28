package com.skeler.scanely.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PdfProcessor {
    private const val TAG = "PdfProcessor"

    data class ProgressUpdate(
        val currentPage: Int,
        val totalPages: Int,
        val extractedText: String,
        val statusMessage: String = ""
    )
    
    data class PdfResult(
        val text: String,
        val thumbnail: Bitmap?,
        val detectedLanguage: String
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
     * @return PdfResult containing text, thumbnail, and detected language
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
                
                val firstPage = pdfRenderer.openPage(0)
                try {
                    // Generate thumbnail (smaller for preview)
                    val thumbWidth = 400
                    val thumbHeight = (thumbWidth * firstPage.height / firstPage.width.toFloat()).toInt()
                    thumbnail = Bitmap.createBitmap(thumbWidth, thumbHeight, Bitmap.Config.ARGB_8888)
                    thumbnail.eraseColor(android.graphics.Color.WHITE)
                    firstPage.render(thumbnail, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    
                    // Quick OCR for script detection (use lower res for speed)
                    val detectWidth = 800
                    val detectHeight = (detectWidth * firstPage.height / firstPage.width.toFloat()).toInt()
                    val detectBitmap = Bitmap.createBitmap(detectWidth, detectHeight, Bitmap.Config.ARGB_8888)
                    detectBitmap.eraseColor(android.graphics.Color.WHITE)
                    firstPage.render(detectBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    
                    val preprocessed = ImagePreprocessor.preprocess(detectBitmap)
                    val sampleResult = ocrHelper.recognizeText(preprocessed)
                    
                    if (preprocessed !== detectBitmap) preprocessed.recycle()
                    detectBitmap.recycle()
                    
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
                } finally {
                    firstPage.close()
                }
            }
            
            // === PHASE 2: Process all pages ===
            // Need to reopen the PDF since we closed page 0
            pdfRenderer.close()
            fileDescriptor.close()
            
            fileDescriptor = context.contentResolver.openFileDescriptor(pdfUri, "r")
            pdfRenderer = PdfRenderer(fileDescriptor!!)
            
            for (i in 0 until pageCount) {
                var page: PdfRenderer.Page? = null
                var bitmap: Bitmap? = null
                
                try {
                    page = pdfRenderer.openPage(i)
                    
                    val targetDpi = 300f
                    val scale = targetDpi / 72f
                    
                    var width = (page.width * scale).toInt()
                    var height = (page.height * scale).toInt()
                    
                    val maxDimension = 2480
                    if (width > maxDimension || height > maxDimension) {
                        val ratio = width.toFloat() / height.toFloat()
                        if (width > height) {
                            width = maxDimension
                            height = (width / ratio).toInt()
                        } else {
                            height = maxDimension
                            width = (height * ratio).toInt()
                        }
                    }
                    
                    bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    
                    // Generate thumbnail from first page if not already done
                    if (i == 0 && thumbnail == null) {
                        val thumbScale = 400f / width
                        thumbnail = Bitmap.createScaledBitmap(
                            bitmap, 
                            400, 
                            (height * thumbScale).toInt(), 
                            true
                        )
                    }
                    
                    val preprocessedBitmap = ImagePreprocessor.preprocess(bitmap)
                    val ocrResult = ocrHelper.recognizeText(preprocessedBitmap)
                    
                    if (preprocessedBitmap !== bitmap) {
                        preprocessedBitmap.recycle()
                    }
                    
                    val text = ocrResult?.text ?: ""
                    
                    stringBuilder.append("--- Page ${i + 1} ---\n")
                    stringBuilder.append(text)
                    stringBuilder.append("\n\n")
                    
                    onProgress(ProgressUpdate(i + 1, pageCount, text, "Processing page ${i + 1} of $pageCount"))
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing page $i", e)
                    stringBuilder.append("--- Page ${i + 1} (Error) ---\n\n")
                } finally {
                    page?.close()
                    bitmap?.recycle()
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Critical PDF extraction error", e)
            return@withContext PdfResult("Error processing PDF: ${e.message}", thumbnail, detectedLanguage)
        } finally {
            try {
                pdfRenderer?.close()
                fileDescriptor?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing PDF resources", e)
            }
        }
        
        return@withContext PdfResult(stringBuilder.toString(), thumbnail, detectedLanguage)
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
            listOf("eng"), // Default to English
            onProgress
        )
        return result.text
    }
}
