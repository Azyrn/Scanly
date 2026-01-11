package com.skeler.scanely.core.pdf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider

/**
 * PDF external viewer utility.
 *
 * ULTRATHINK Deep Reasoning:
 * - FileProvider not needed for content:// URIs from OpenDocument picker
 * - FLAG_GRANT_READ_URI_PERMISSION grants temporary access to external apps
 * - createChooser() provides graceful fallback if no PDF viewer installed
 * - ActivityNotFoundException caught for edge case: user uninstalled all viewers
 */
object PdfHelper {

    private const val MIME_TYPE_PDF = "application/pdf"

    /**
     * Open a PDF in an external viewer application.
     *
     * @param context Android context
     * @param uri Content URI of the PDF document
     * @return true if PDF viewer launched successfully, false otherwise
     */
    fun openPdfExternally(context: Context, uri: Uri): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, MIME_TYPE_PDF)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(intent, "Open PDF with...")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "No PDF viewer found. Install a PDF reader app.",
                Toast.LENGTH_LONG
            ).show()
            false
        }
    }

    /**
     * Check if the URI represents a PDF document.
     *
     * @param context Android context for ContentResolver access
     * @param uri URI to check
     * @return true if the URI is a PDF, false otherwise
     */
    fun isPdfUri(context: Context, uri: Uri): Boolean {
        val mimeType = context.contentResolver.getType(uri)
        return mimeType == MIME_TYPE_PDF ||
               uri.toString().lowercase().endsWith(".pdf")
    }
}
