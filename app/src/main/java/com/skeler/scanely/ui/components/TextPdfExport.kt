package com.skeler.scanely.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.skeler.scanely.core.ocr.TextBlockData
import com.skeler.scanely.core.pdf.ScanExporter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

enum class TextExportFormat(val mimeType: String, val extension: String, val label: String) {
    PDF(ScanExporter.PDF_MIME_TYPE, "pdf", "Save as PDF"),
    WORD(ScanExporter.WORD_MIME_TYPE, "docx", "Save as Word (.docx)"),
    CSV(ScanExporter.CSV_MIME_TYPE, "csv", "Save as CSV"),
    MARKDOWN(ScanExporter.MARKDOWN_MIME_TYPE, "md", "Save as Markdown"),
    JSON(ScanExporter.JSON_MIME_TYPE, "json", "Save as JSON")
}

/** Formats every result supports; Markdown/JSON are added only for offline Paddle scans. */
val TEXT_EXPORT_FORMATS = listOf(TextExportFormat.PDF, TextExportFormat.WORD, TextExportFormat.CSV)

val PADDLE_EXPORT_FORMATS = TEXT_EXPORT_FORMATS + TextExportFormat.MARKDOWN + TextExportFormat.JSON

/** Writes straight to Downloads/Scanly (like the image exports), then offers to share. */
@Composable
fun rememberTextExporter(
    blocks: List<TextBlockData> = emptyList(),
    markdown: String? = null
): (String, TextExportFormat) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    return exporter@{ text: String, format: TextExportFormat ->
        if (text.isBlank()) {
            toast(context, "Nothing to export")
            return@exporter
        }
        scope.launch {
            runCatching {
                val name = timestampName()
                when (format) {
                    TextExportFormat.PDF -> ScanExporter.exportTextPdf(context, text, name)
                    TextExportFormat.WORD -> ScanExporter.exportTextWord(context, text, name)
                    TextExportFormat.CSV -> ScanExporter.exportTextCsv(context, text, name)
                    TextExportFormat.MARKDOWN ->
                        ScanExporter.exportTextMarkdown(context, text, markdown, name)
                    TextExportFormat.JSON ->
                        ScanExporter.exportTextJson(context, text, blocks, name)
                }
            }.fold(
                onSuccess = { uri ->
                    toast(context, "Saved to Downloads/Scanly")
                    ScanExporter.shareDocument(context, uri, format.mimeType)
                },
                onFailure = {
                    toast(context, "Couldn't save ${format.name}")
                }
            )
        }
    }
}

private fun toast(context: Context, message: String) =
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

private fun timestampName(): String =
    "Text_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
