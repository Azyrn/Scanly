package com.skeler.scanely.core.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class PayloadFactory @Inject constructor(
    @ApplicationContext private val context: Context
) {
    class PayloadException(message: String) : Exception(message)

    data class Payload(val prompt: String, val images: List<String>)

    fun create(uri: Uri, mode: AiMode): Payload {
        val size = fileSizeBytes(uri)
        if (size != null && size > MAX_FILE_SIZE_BYTES) {
            throw PayloadException(
                "File too large (${size / (1024 * 1024)} MB). " +
                    "The maximum size for an AI scan is $MAX_FILE_SIZE_MB MB."
            )
        }

        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val prompt = AiPrompts.forMode(mode)

        return when {
            mimeType == TEXT_MIME_TYPE -> {
                val bytes = loadFileBytes(uri)
                    ?: throw PayloadException("Failed to read text file")
                Payload("$prompt\n\n${bytes.toString(Charsets.UTF_8)}", emptyList())
            }

            mimeType == PDF_MIME_TYPE -> {
                val images = renderPdfToBase64(uri)
                if (images.isEmpty()) throw PayloadException("Failed to read PDF file")
                Payload(prompt, images)
            }

            mimeType.startsWith("image/") -> {
                val bitmap = loadBitmapFromUri(uri)
                    ?: throw PayloadException("Failed to load image")
                try {
                    Payload(prompt, listOf(encodeBase64Jpeg(bitmap)))
                } finally {
                    bitmap.recycle()
                }
            }

            else -> throw PayloadException(
                "Unsupported file type: $mimeType\n\n" +
                    "Supported:\n" +
                    "• PDF files (.pdf)\n" +
                    "• Text files (.txt)\n" +
                    "• Images (.jpg, .png, .webp)\n\n" +
                    "For Word/PowerPoint/Excel files, please convert to PDF first."
            )
        }
    }

    private fun encodeBase64Jpeg(bitmap: Bitmap): String {
        val scaled = downscale(bitmap)
        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        if (scaled !== bitmap) scaled.recycle()
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    private fun downscale(bitmap: Bitmap): Bitmap {
        val maxDim = maxOf(bitmap.width, bitmap.height)
        if (maxDim <= MAX_IMAGE_DIMENSION) return bitmap
        val ratio = MAX_IMAGE_DIMENSION.toFloat() / maxDim
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun fileSizeBytes(uri: Uri): Long? = try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use {
            it.statSize.takeIf { size -> size >= 0 }
        }
    } catch (e: Exception) {
        aiDebug { "size check failed: ${e.message}" }
        null
    }

    private fun loadFileBytes(uri: Uri): ByteArray? = try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (e: Exception) {
        aiDebug { "read file failed: ${e.message}" }
        null
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? = try {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) {
        aiDebug { "load bitmap failed: ${e.message}" }
        null
    }

    private fun renderPdfToBase64(uri: Uri): List<String> {
        val pages = mutableListOf<String>()
        var tempFile: File? = null
        try {
            tempFile = File(context.cacheDir, "temp_pdf_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            }

            ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    val pageCount = minOf(renderer.pageCount, MAX_INPUT_IMAGES)
                    for (i in 0 until pageCount) {
                        val bitmap = renderer.openPage(i).use { page ->
                            val width = (page.width * PDF_RENDER_SCALE).toInt()
                            val height = (page.height * PDF_RENDER_SCALE).toInt()
                            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                                it.eraseColor(Color.WHITE)
                                page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                        try {
                            pages.add(encodeBase64Jpeg(bitmap))
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            aiDebug { "PDF render failed: ${e.message}" }
        } finally {
            tempFile?.delete()
        }
        return pages
    }

    companion object {
        private const val PDF_MIME_TYPE = "application/pdf"
        private const val TEXT_MIME_TYPE = "text/plain"

        private const val MAX_FILE_SIZE_MB = 20
        private const val MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB.toLong() * 1024 * 1024
        private const val MAX_INPUT_IMAGES = 3

        private const val MAX_IMAGE_DIMENSION = 1536
        private const val JPEG_QUALITY = 85
        private const val PDF_RENDER_SCALE = 2.0f
    }
}
