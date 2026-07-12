package com.skeler.scanely.core.pdf

import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.skeler.scanely.core.ocr.TextBlockData
import com.skeler.scanely.core.text.Markdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ScanExporter {

    private const val AUTHORITY_SUFFIX = ".fileprovider"
    private const val ALBUM = "Scanly"
    private const val JPEG_QUALITY = 95
    private const val TAG = "ScanExporter"

    const val PDF_MIME_TYPE = "application/pdf"
    const val WORD_MIME_TYPE =
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    const val CSV_MIME_TYPE = "text/csv"
    const val MARKDOWN_MIME_TYPE = "text/markdown"
    const val JSON_MIME_TYPE = "application/json"

    /** Page separator emitted by the OCR services when scanning multi-page PDFs. */
    private val PAGE_MARKER = Regex("^-{3} Page (\\d+) -{3}$")

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val PAGE_MARGIN = 40
    private const val BODY_TEXT_SIZE = 12f
    private const val BODY_LINE_SPACING_EXTRA = 4f

    suspend fun exportPdf(
        context: Context,
        bitmaps: List<Bitmap>,
        baseName: String
    ): Uri = withContext(Dispatchers.IO) {
        exportToDownloads(context, "$baseName.pdf", PDF_MIME_TYPE) { out ->
            val document = PdfDocument()
            try {
                bitmaps.forEachIndexed { index, bitmap ->
                    val pageInfo = PdfDocument.PageInfo
                        .Builder(bitmap.width, bitmap.height, index + 1)
                        .create()
                    val page = document.startPage(pageInfo)
                    page.canvas.drawColor(Color.WHITE)
                    page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    document.finishPage(page)
                }
                document.writeTo(out)
            } finally {
                document.close()
            }
        }
    }

    /** .docx with one scan image per page — deliberately images, not uncertain OCR text. */
    suspend fun exportWord(
        context: Context,
        bitmaps: List<Bitmap>,
        baseName: String
    ): Uri = withContext(Dispatchers.IO) {
        exportToDownloads(context, "$baseName.docx", WORD_MIME_TYPE) { out ->
            ZipOutputStream(out.buffered()).use { zip ->
                zip.writeText("[Content_Types].xml", wordContentTypes())
                zip.writeText("_rels/.rels", packageRelationships())
                zip.writeText("word/document.xml", wordDocument(bitmaps))
                zip.writeText("word/_rels/document.xml.rels", wordRelationships(bitmaps.size))
                bitmaps.forEachIndexed { index, bitmap ->
                    zip.putNextEntry(ZipEntry("word/media/image${index + 1}.jpg"))
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, zip)) {
                        throw IOException("Couldn't encode scan ${index + 1}")
                    }
                    zip.closeEntry()
                }
            }
        }
    }

    suspend fun exportTextPdf(
        context: Context,
        text: String,
        baseName: String
    ): Uri = withContext(Dispatchers.IO) {
        exportToDownloads(context, "$baseName.pdf", PDF_MIME_TYPE) { out ->
            val document = buildTextPdfDocument(text)
            try {
                document.writeTo(out)
            } finally {
                document.close()
            }
        }
    }

    suspend fun exportTextCsv(
        context: Context,
        text: String,
        baseName: String
    ): Uri = withContext(Dispatchers.IO) {
        exportToDownloads(context, "$baseName.csv", CSV_MIME_TYPE) { out ->
            out.bufferedWriter().use { writer ->
                text.lineSequence().forEach { line ->
                    writer.append(csvField(line)).append("\r\n")
                }
            }
        }
    }

    /** Exports OCR or AI output as editable text in a standard Word .docx document. */
    suspend fun exportTextWord(
        context: Context,
        text: String,
        baseName: String
    ): Uri = withContext(Dispatchers.IO) {
        exportToDownloads(context, "$baseName.docx", WORD_MIME_TYPE) { out ->
            ZipOutputStream(out.buffered()).use { zip ->
                zip.writeText("[Content_Types].xml", wordContentTypes())
                zip.writeText("_rels/.rels", packageRelationships())
                zip.writeText("word/document.xml", wordTextDocument(text))
                zip.writeText("word/_rels/document.xml.rels", wordRelationships(0))
            }
        }
    }

    /**
     * [structured] is the layout-aware Markdown from the offline structure pipeline; without
     * it we can only mirror the OCR's own lines.
     */
    suspend fun exportTextMarkdown(
        context: Context,
        text: String,
        structured: String?,
        baseName: String
    ): Uri = withContext(Dispatchers.IO) {
        val markdown = structured?.takeIf { it.isNotBlank() } ?: buildMarkdown(text)
        exportToDownloads(context, "$baseName.md", MARKDOWN_MIME_TYPE) { out ->
            out.bufferedWriter().use { it.write(markdown) }
        }
    }

    /**
     * PP-OCR gives us lines, not layout, so this mirrors PaddleOCR's own `save_to_json` shape:
     * per-line text, box and confidence — no headings, tables or reading structure.
     */
    suspend fun exportTextJson(
        context: Context,
        text: String,
        blocks: List<TextBlockData>,
        baseName: String
    ): Uri = withContext(Dispatchers.IO) {
        exportToDownloads(context, "$baseName.json", JSON_MIME_TYPE) { out ->
            out.bufferedWriter().use { it.write(buildJson(text, blocks)) }
        }
    }

    internal fun buildMarkdown(text: String): String {
        val pages = splitPages(text)
        val multiPage = pages.size > 1
        return buildString {
            pages.forEach { (number, lines) ->
                if (multiPage) {
                    if (isNotEmpty()) append("\n")
                    append("## Page ").append(number).append("\n\n")
                }
                lines.forEach { line ->
                    if (line.isBlank()) append("\n")
                    // Trailing spaces keep OCR line breaks intact once rendered.
                    else append(Markdown.escape(line)).append("  \n")
                }
            }
        }.trimEnd() + "\n"
    }

    internal fun buildJson(text: String, blocks: List<TextBlockData>): String {
        val pages = JSONArray()

        if (blocks.isEmpty()) {
            splitPages(text).forEach { (number, lines) ->
                pages.put(
                    JSONObject()
                        .put("page", number)
                        .put("lines", JSONArray(lines.filter { it.isNotBlank() }.map {
                            JSONObject().put("text", it)
                        }))
                )
            }
        } else {
            blocks.groupBy { it.page }.toSortedMap().forEach { (number, pageBlocks) ->
                val lines = JSONArray()
                pageBlocks.forEach { block ->
                    lines.put(
                        JSONObject()
                            .put("text", block.text)
                            .put("confidence", block.confidence.toDouble())
                            .put(
                                "box",
                                JSONArray(
                                    listOf(
                                        block.boundingBoxLeft,
                                        block.boundingBoxTop,
                                        block.boundingBoxRight,
                                        block.boundingBoxBottom
                                    )
                                )
                            )
                    )
                }
                pages.put(JSONObject().put("page", number).put("lines", lines))
            }
        }

        return JSONObject()
            .put("text", text)
            .put("pages", pages)
            .toString(2)
    }

    /** Splits OCR output on its page markers; text without markers is a single page. */
    private fun splitPages(text: String): List<Pair<Int, List<String>>> {
        val pages = mutableListOf<Pair<Int, MutableList<String>>>()
        var current = 1 to mutableListOf<String>()

        text.lines().forEach { line ->
            val marker = PAGE_MARKER.matchEntire(line.trim())
            if (marker == null) {
                current.second.add(line.trim())
            } else {
                pages.add(current)
                current = (marker.groupValues[1].toIntOrNull() ?: pages.size + 1) to mutableListOf()
            }
        }
        pages.add(current)

        return pages
            .filter { (_, lines) -> lines.any { it.isNotBlank() } }
            .map { (number, lines) -> number to lines.dropWhile(String::isBlank).dropLastWhile(String::isBlank) }
    }


    private fun csvField(value: String): String =
        if (value.any { it == ',' || it == '"' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    /** Caller owns and must close the returned document. */
    private fun buildTextPdfDocument(text: String): PdfDocument {
        val contentWidth = PAGE_WIDTH - 2 * PAGE_MARGIN
        val contentHeight = PAGE_HEIGHT - 2 * PAGE_MARGIN

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = BODY_TEXT_SIZE
            typeface = Typeface.SANS_SERIF
        }

        val body = text.ifBlank { " " }
        val layout = StaticLayout.Builder
            .obtain(body, 0, body.length, paint, contentWidth)
            .setLineSpacing(BODY_LINE_SPACING_EXTRA, 1f)
            .setIncludePad(false)
            .build()

        val document = PdfDocument()
        try {
            var line = 0
            var pageNumber = 1
            val lineCount = layout.lineCount
            while (line < lineCount) {
                val pageInfo = PdfDocument.PageInfo
                    .Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber)
                    .create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)

                val top = layout.getLineTop(line)
                var end = line
                while (end < lineCount &&
                    layout.getLineBottom(end) - top <= contentHeight
                ) end++
                // Oversized single line must still advance.
                if (end == line) end = line + 1

                canvas.save()
                canvas.translate(PAGE_MARGIN.toFloat(), PAGE_MARGIN.toFloat() - top)
                canvas.clipRect(
                    0f,
                    top.toFloat(),
                    contentWidth.toFloat(),
                    (top + contentHeight).toFloat()
                )
                layout.draw(canvas)
                canvas.restore()

                document.finishPage(page)
                line = end
                pageNumber++
            }
        } catch (t: Throwable) {
            document.close()
            throw t
        }
        return document
    }

    suspend fun saveImagesToGallery(
        context: Context,
        bitmaps: List<Bitmap>,
        baseName: String
    ): Int = withContext(Dispatchers.IO) {
        var saved = 0
        bitmaps.forEachIndexed { index, bitmap ->
            val name = if (bitmaps.size == 1) baseName else "${baseName}_${index + 1}"
            if (saveOneToGallery(context, bitmap, name)) saved++
        }
        saved
    }

    private fun saveOneToGallery(context: Context, bitmap: Bitmap, displayName: String): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/$ALBUM"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val uri = resolver.insert(collection, values) ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            } ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }

    /** Writes durable scan exports to the user-visible Downloads/Scanly directory. */
    private fun exportToDownloads(
        context: Context,
        displayName: String,
        mimeType: String,
        write: (OutputStream) -> Unit
    ): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$ALBUM")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                values
            ) ?: throw IOException("Couldn't create $displayName in Downloads/$ALBUM")
            try {
                resolver.openOutputStream(uri)?.use(write)
                    ?: throw IOException("Couldn't open $displayName for writing")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return uri
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
                throw t
            }
        }

        // Pre-Q public Downloads needs a WRITE_EXTERNAL_STORAGE grant we never ask for; without it
        // fall back to the app's shareable cache so export still produces an openable file.
        val directory = if (hasLegacyWritePermission(context)) {
            @Suppress("DEPRECATION")
            java.io.File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                ALBUM
            )
        } else {
            java.io.File(context.cacheDir, "scans")
        }
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Couldn't create ${directory.absolutePath}")
        }
        val file = java.io.File(directory, displayName)
        try {
            file.outputStream().use(write)
        } catch (t: Throwable) {
            file.delete()
            throw t
        }
        return FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
    }

    private fun hasLegacyWritePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

    /** Never throws; false if no viewer or launch rejected. */
    fun openPdf(context: Context, uri: Uri): Boolean {
        return openDocument(context, uri, PDF_MIME_TYPE, "Open PDF with…")
    }

    /** Never throws; false if no app can open the exported file. */
    fun openDocument(context: Context, uri: Uri, mimeType: String, chooserTitle: String): Boolean {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            clipData = ClipData.newRawUri("Export", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        if (!hasHandler(context, view)) {
            Log.w(TAG, "No activity can view $mimeType")
            return false
        }

        grantToResolvers(context, view, uri)

        val chooser = Intent.createChooser(view, chooserTitle).apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Unable to launch PDF open chooser", e)
            false
        }
    }

    /** Never throws; false if no handler or launch rejected. */
    fun shareDocument(context: Context, uri: Uri, mimeType: String): Boolean {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Export", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        grantToResolvers(context, send, uri)
        val chooser = Intent.createChooser(send, "Share").apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Unable to launch share chooser", e)
            false
        }
    }

    private fun hasHandler(context: Context, intent: Intent): Boolean =
        context.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .isNotEmpty()

    private fun grantToResolvers(context: Context, intent: Intent, uri: Uri) {
        val resolvers = context.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        for (info in resolvers) {
            runCatching {
                context.grantUriPermission(
                    info.activityInfo.packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
    }

    fun flatten(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(result).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, 0f, 0f, null)
        }
        return result
    }

    private fun ZipOutputStream.writeText(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun wordContentTypes(): String = """<?xml version="1.0" encoding="UTF-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Default Extension="jpg" ContentType="image/jpeg"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

    private fun packageRelationships(): String = """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    private fun wordRelationships(pageCount: Int): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
        repeat(pageCount) { index ->
            append("<Relationship Id=\"rId${index + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/image${index + 1}.jpg\"/>")
        }
        append("</Relationships>")
    }

    private fun wordDocument(bitmaps: List<Bitmap>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><w:body>")
        bitmaps.forEachIndexed { index, bitmap ->
            val (cx, cy) = wordImageExtent(bitmap)
            val id = index + 1
            append("<w:p><w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\"><wp:extent cx=\"$cx\" cy=\"$cy\"/><wp:docPr id=\"$id\" name=\"Scan $id\"/><a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><pic:pic><pic:nvPicPr><pic:cNvPr id=\"0\" name=\"Scan $id\"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed=\"rId$id\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$cx\" cy=\"$cy\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>")
            if (index != bitmaps.lastIndex) append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
        }
        append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"720\" w:right=\"720\" w:bottom=\"720\" w:left=\"720\"/></w:sectPr></w:body></w:document>")
    }

    private fun wordTextDocument(text: String): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>")
        text.lineSequence().forEach { line ->
            append("<w:p><w:r><w:t xml:space=\"preserve\">")
            append(escapeXml(line))
            append("</w:t></w:r></w:p>")
        }
        append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"720\" w:right=\"720\" w:bottom=\"720\" w:left=\"720\"/></w:sectPr></w:body></w:document>")
    }

    private fun escapeXml(value: String): String = buildString(value.length) {
        value.forEach { character ->
            // Control characters are illegal in XML 1.0; Word rejects the whole file as corrupt.
            if (character < ' ' && character != '\t') return@forEach
            append(
                when (character) {
                    '&' -> "&amp;"
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '"' -> "&quot;"
                    '\'' -> "&apos;"
                    else -> character
                }
            )
        }
    }

    private fun wordImageExtent(bitmap: Bitmap): Pair<Long, Long> {
        val pageWidth = 5_943_600L // A4 width minus 0.5in margins, in EMUs.
        val pageHeight = 8_229_600L // A4 height minus 0.5in margins, in EMUs.
        return if (bitmap.width.toLong() * pageHeight > bitmap.height.toLong() * pageWidth) {
            pageWidth to (pageWidth * bitmap.height / bitmap.width)
        } else {
            (pageHeight * bitmap.width / bitmap.height) to pageHeight
        }
    }
}
