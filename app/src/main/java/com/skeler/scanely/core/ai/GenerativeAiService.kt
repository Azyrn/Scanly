package com.skeler.scanely.core.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.skeler.scanely.settings.data.datastore.SettingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI processing mode for image/document analysis.
 */
enum class AiMode {
    EXTRACT_TEXT,
    EXTRACT_PDF_TEXT,
    ICON_TRANSLATE
}

/**
 * Sealed class representing AI operation results.
 */
sealed class AiResult {
    data class Success(val text: String) : AiResult()
    data class RateLimited(val remainingMs: Long) : AiResult()
    data class Error(val message: String) : AiResult()
}

/**
 * Service handling Google Generative AI (Gemini) operations.
 *
 * Gemini API Supported File Types:
 * - PDF: application/pdf (up to 50MB, 1000 pages)
 * - Text: text/plain
 * - Images: image/png, image/jpeg, image/webp
 * 
 * NOT Supported (need conversion):
 * - DOCX, PPTX, XLSX - must convert to PDF or extract text
 */
@Singleton
class GenerativeAiService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val generativeModel: GenerativeModel
) {
    companion object {
        private const val PROMPT_EXTRACT = "Extract all visible text from this image. Return only the extracted text, nothing else."
        private const val PROMPT_PDF_EXTRACT = """Extract ALL text content from this document. 
Rules:
1. Extract every single word, number, and character
2. Preserve the original formatting and structure
3. Include all tables, headers, footers, and captions
4. Do not summarize or skip any content
5. Return ONLY the extracted text, no descriptions or commentary"""
        private const val PROMPT_ICON_TRANSLATE = "Extract all visible text from this image and translate it to English."
        
        // Gemini supported document types
        private val PDF_MIME_TYPE = "application/pdf"
        private val TEXT_MIME_TYPE = "text/plain"
    }

    /**
     * Process an image or document with the specified AI mode.
     */
    suspend fun processImage(uri: Uri, mode: AiMode): AiResult = withContext(Dispatchers.IO) {
        try {
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            
            // Select prompt based on mode
            val prompt = when (mode) {
                AiMode.EXTRACT_TEXT -> PROMPT_EXTRACT
                AiMode.EXTRACT_PDF_TEXT -> PROMPT_PDF_EXTRACT
                AiMode.ICON_TRANSLATE -> PROMPT_ICON_TRANSLATE
            }

            val response = when {
                // PDF - send as raw bytes (Gemini supports PDF natively)
                mimeType == PDF_MIME_TYPE || mode == AiMode.EXTRACT_PDF_TEXT && mimeType == PDF_MIME_TYPE -> {
                    val fileBytes = loadFileBytes(uri)
                        ?: return@withContext AiResult.Error("Failed to read PDF file")
                    
                    generativeModel.generateContent(
                        content {
                            blob(PDF_MIME_TYPE, fileBytes)
                            text(prompt)
                        }
                    )
                }
                
                // Plain text - send as raw bytes
                mimeType == TEXT_MIME_TYPE -> {
                    val fileBytes = loadFileBytes(uri)
                        ?: return@withContext AiResult.Error("Failed to read text file")
                    
                    generativeModel.generateContent(
                        content {
                            blob(TEXT_MIME_TYPE, fileBytes)
                            text(prompt)
                        }
                    )
                }
                
                // For unsupported document types in EXTRACT_PDF_TEXT mode,
                // try to render as PDF pages (for offline processing)
                mode == AiMode.EXTRACT_PDF_TEXT -> {
                    // Try PDF rendering first
                    val bitmap = renderPdfFirstPage(uri)
                    if (bitmap != null) {
                        generativeModel.generateContent(
                            content {
                                image(bitmap)
                                text(prompt)
                            }
                        )
                    } else {
                        return@withContext AiResult.Error(
                            "Unsupported file type: $mimeType\n\n" +
                            "Gemini AI supports:\n" +
                            "• PDF files (.pdf)\n" +
                            "• Text files (.txt)\n" +
                            "• Images (.jpg, .png)\n\n" +
                            "For Word/PowerPoint/Excel files, please convert to PDF first."
                        )
                    }
                }
                
                // Images - load as bitmap
                else -> {
                    val bitmap = loadBitmapFromUri(uri)
                        ?: return@withContext AiResult.Error("Failed to load image")
                    
                    generativeModel.generateContent(
                        content {
                            image(bitmap)
                            text(prompt)
                        }
                    )
                }
            }

            val resultText = response.text ?: "No response generated"
            AiResult.Success(resultText)

        } catch (e: Exception) {
            e.printStackTrace()
            AiResult.Error(e.message ?: "Unknown error occurred")
        }
    }

    suspend fun extractText(imageUri: Uri): AiResult = processImage(imageUri, AiMode.EXTRACT_TEXT)

    /**
     * Translate text to a target language.
     */
    suspend fun translateText(text: String, targetLanguage: String): AiResult = withContext(Dispatchers.IO) {
        try {
            val prompt = "Translate the following text to $targetLanguage. Return only the translated text, nothing else:\n\n$text"
            val response = generativeModel.generateContent(prompt)
            val resultText = response.text ?: "Translation failed"
            AiResult.Success(resultText)
        } catch (e: Exception) {
            AiResult.Error(e.message ?: "Translation error occurred")
        }
    }

    /**
     * Load raw file bytes from URI.
     */
    private fun loadFileBytes(uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.readBytes()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Load a Bitmap from a content URI (for images).
     */
    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Render the first page of a PDF as a bitmap (fallback for offline).
     */
    private fun renderPdfFirstPage(uri: Uri): Bitmap? {
        return try {
            val tempFile = File(context.cacheDir, "temp_pdf_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            val fileDescriptor = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = PdfRenderer(fileDescriptor)

            if (pdfRenderer.pageCount == 0) {
                pdfRenderer.close()
                fileDescriptor.close()
                tempFile.delete()
                return null
            }

            val page = pdfRenderer.openPage(0)
            val scale = 2.0f
            val width = (page.width * scale).toInt()
            val height = (page.height * scale).toInt()
            
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            page.close()
            pdfRenderer.close()
            fileDescriptor.close()
            tempFile.delete()

            bitmap
        } catch (e: Exception) {
            null
        }
    }
}
