package com.skeler.scanely.ui.components

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.skeler.scanely.core.pdf.ScanExporter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/** File formats the plain-text export flow can produce. */
enum class TextExportFormat(val mimeType: String, val extension: String) {
    PDF("application/pdf", "pdf"),
    CSV("text/csv", "csv")
}

/**
 * Remembers a "save this text as a document and share it" action for use from
 * result screens. The user picks where the file goes via the system
 * ACTION_CREATE_DOCUMENT picker (the scoped-storage-safe path), the content is
 * written there off the main thread, and the share sheet is then offered so
 * saving and sharing happen in one step. Failures surface as Toasts and never
 * crash the app.
 *
 * The returned lambda is safe to wire straight onto a menu item's onClick.
 */
@Composable
fun rememberTextExporter(): (String, TextExportFormat) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Text captured when the picker is launched, consumed when it returns.
    val pendingText = remember { mutableStateOf<String?>(null) }

    fun writeAndShare(uri: Uri?, format: TextExportFormat) {
        val text = pendingText.value
        pendingText.value = null
        if (uri == null || text == null) return // picker cancelled
        scope.launch {
            runCatching {
                when (format) {
                    TextExportFormat.PDF -> ScanExporter.exportTextPdf(context, text, uri)
                    TextExportFormat.CSV -> ScanExporter.exportTextCsv(context, text, uri)
                }
            }.fold(
                onSuccess = {
                    toast(context, "Saved as ${format.name}")
                    ScanExporter.shareDocument(context, uri, format.mimeType)
                },
                onFailure = {
                    // Don't leave a zero-byte file behind in the user's folder.
                    runCatching {
                        DocumentsContract.deleteDocument(context.contentResolver, uri)
                    }
                    toast(context, "Couldn't save ${format.name}")
                }
            )
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(TextExportFormat.PDF.mimeType)
    ) { writeAndShare(it, TextExportFormat.PDF) }
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(TextExportFormat.CSV.mimeType)
    ) { writeAndShare(it, TextExportFormat.CSV) }

    return exporter@{ text: String, format: TextExportFormat ->
        if (text.isBlank()) {
            toast(context, "Nothing to export")
            return@exporter
        }
        pendingText.value = text
        when (format) {
            TextExportFormat.PDF -> pdfLauncher
            TextExportFormat.CSV -> csvLauncher
        }.launch("${timestampName()}.${format.extension}")
    }
}

private fun toast(context: Context, message: String) =
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

private fun timestampName(): String =
    "Text_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
