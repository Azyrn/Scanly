package com.skeler.scanely.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.skeler.scanely.core.ocr.TextBlockData
import com.skeler.scanely.core.pdf.ScanExporter
import com.skeler.scanely.core.text.TextTables
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

enum class TextExportFormat(val mimeType: String, val extension: String, val label: String) {
    PDF(ScanExporter.PDF_MIME_TYPE, "pdf", "Save as PDF"),
    WORD(ScanExporter.WORD_MIME_TYPE, "docx", "Save as Word (.docx)"),
    MARKDOWN(ScanExporter.MARKDOWN_MIME_TYPE, "md", "Save as Markdown"),
    CSV(ScanExporter.CSV_MIME_TYPE, "csv", "Save as CSV"),
    JSON(ScanExporter.JSON_MIME_TYPE, "json", "Save as JSON")
}

/**
 * Exactly what the preview is showing: its text, whether it is being rendered as Markdown, and
 * the boxes behind it. Every export reads this and nothing else, so no file can disagree with
 * the screen.
 */
data class ExportContent(
    val text: String,
    val isMarkdown: Boolean = false,
    val blocks: List<TextBlockData> = emptyList()
)

/** Formats every result supports; JSON needs the offline engine's boxes. */
val TEXT_EXPORT_FORMATS = listOf(
    TextExportFormat.PDF,
    TextExportFormat.WORD,
    TextExportFormat.MARKDOWN,
    TextExportFormat.CSV
)

val PADDLE_EXPORT_FORMATS = TEXT_EXPORT_FORMATS + TextExportFormat.JSON

/** CSV can only carry a table, and only the preview says whether there is one. */
fun unavailableFormats(content: ExportContent): Set<TextExportFormat> =
    if (TextTables.hasTables(content.text, content.isMarkdown)) {
        emptySet()
    } else {
        setOf(TextExportFormat.CSV)
    }

/** Writes straight to Downloads/Scanly (like the image exports), then offers to share. */
@Composable
fun rememberTextExporter(): (ExportContent, TextExportFormat) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    return exporter@{ content: ExportContent, format: TextExportFormat ->
        val text = content.text
        if (text.isBlank()) {
            toast(context, "Nothing to export")
            return@exporter
        }
        val rows = if (format == TextExportFormat.CSV) {
            TextTables.rows(text, content.isMarkdown)
        } else {
            emptyList()
        }
        if (format == TextExportFormat.CSV && rows.isEmpty()) {
            toast(context, "No table data to export")
            return@exporter
        }
        scope.launch {
            runCatching {
                val name = timestampName()
                when (format) {
                    TextExportFormat.PDF ->
                        ScanExporter.exportTextPdf(context, text, content.isMarkdown, name)
                    TextExportFormat.WORD ->
                        ScanExporter.exportTextWord(context, text, content.isMarkdown, name)
                    TextExportFormat.MARKDOWN ->
                        ScanExporter.exportTextMarkdown(context, text, name)
                    TextExportFormat.CSV ->
                        ScanExporter.exportTextCsv(context, rows, name)
                    TextExportFormat.JSON ->
                        ScanExporter.exportTextJson(context, text, content.blocks, name)
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
