package com.skeler.scanely.core.pdf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object PdfHelper {

    private const val MIME_TYPE_PDF = "application/pdf"

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

    fun isPdfUri(context: Context, uri: Uri): Boolean {
        val mimeType = context.contentResolver.getType(uri)
        return mimeType == MIME_TYPE_PDF ||
            uri.toString().lowercase().endsWith(".pdf")
    }
}
