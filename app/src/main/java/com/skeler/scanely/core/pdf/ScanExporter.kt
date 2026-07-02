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
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Exports finished scans as a high-quality multi-page PDF or as individual
 * images saved to the device gallery. All I/O runs off the main thread.
 */
object ScanExporter {

    private const val AUTHORITY_SUFFIX = ".fileprovider"
    private const val ALBUM = "Scanly"
    private const val JPEG_QUALITY = 95
    private const val TAG = "ScanExporter"

    // A4 at 72dpi, in points. Generous margins keep text off the page edges.
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val PAGE_MARGIN = 40
    private const val BODY_TEXT_SIZE = 12f
    private const val BODY_LINE_SPACING_EXTRA = 4f

    /**
     * Render [bitmaps] into a single PDF (one page per bitmap, at the bitmap's
     * native resolution) inside the shareable cache/scans directory.
     *
     * @return a content:// [Uri] backed by the app's FileProvider.
     */
    suspend fun exportPdf(
        context: Context,
        bitmaps: List<Bitmap>,
        baseName: String
    ): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "scans").apply { mkdirs() }
        val file = File(dir, "$baseName.pdf")

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
            FileOutputStream(file).use { document.writeTo(it) }
        } finally {
            document.close()
        }

        FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
    }

    /**
     * Render plain [text] into a clean, multi-page A4 PDF with margins and
     * write it to [destination] — typically a Storage Access Framework URI
     * returned by ACTION_CREATE_DOCUMENT, so the user chooses where it lives.
     * Line breaks are preserved and long lines are wrapped; text that
     * overflows a page continues on the next one (never clipped mid-line).
     */
    suspend fun exportTextPdf(
        context: Context,
        text: String,
        destination: Uri
    ): Unit = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(destination)?.use { out ->
            val document = buildTextPdfDocument(text)
            try {
                document.writeTo(out)
            } finally {
                document.close()
            }
        } ?: throw IOException("Unable to open output stream for $destination")
    }

    /**
     * Write [text] to [destination] as a single-column CSV, one row per line
     * of the source text. Extracted prose has no real column structure, so no
     * artificial header or columns are invented; fields are quoted only when
     * CSV metacharacters (comma or quote) require it.
     */
    suspend fun exportTextCsv(
        context: Context,
        text: String,
        destination: Uri
    ): Unit = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(destination)?.use { out ->
            out.bufferedWriter().use { writer ->
                text.lineSequence().forEach { line ->
                    writer.append(csvField(line)).append("\r\n")
                }
            }
        } ?: throw IOException("Unable to open output stream for $destination")
    }

    private fun csvField(value: String): String =
        if (value.any { it == ',' || it == '"' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    /**
     * Lay out [text] into a paginated A4 [PdfDocument]. The caller owns the
     * returned document and must close it.
     */
    private fun buildTextPdfDocument(text: String): PdfDocument {
        val contentWidth = PAGE_WIDTH - 2 * PAGE_MARGIN
        val contentHeight = PAGE_HEIGHT - 2 * PAGE_MARGIN

        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = BODY_TEXT_SIZE
            typeface = Typeface.SANS_SERIF
        }

        // StaticLayout needs at least one character to lay out a line.
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
                // A single line taller than the page must still make progress.
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

    /**
     * Save each bitmap as a JPEG into the gallery under Pictures/Scanly.
     *
     * @return number of images successfully saved.
     */
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

    /**
     * Launch an "Open with…" chooser so the user picks which installed app
     * opens the exported PDF for viewing (ACTION_VIEW).
     *
     * Never throws: returns false if no viewer is available or the launch is
     * rejected, so the caller can show a friendly message instead of crashing.
     *
     * @return true if the chooser was launched.
     */
    fun openPdf(context: Context, uri: Uri): Boolean {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            clipData = ClipData.newRawUri("PDF", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // Bail out to the caller's fallback if nothing can actually view a PDF,
        // instead of launching an empty chooser or crashing.
        if (!hasHandler(context, view)) {
            Log.w(TAG, "No activity can view application/pdf")
            return false
        }

        // Some viewers ignore the intent grant flag and read the URI on a
        // different process/thread; grant read to every resolver explicitly.
        grantToResolvers(context, view, uri)

        val chooser = Intent.createChooser(view, "Open PDF with…").apply {
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

    /**
     * Launch a chooser to share an exported document of any [mimeType].
     *
     * Never throws: if no app can handle the share (or the launch is otherwise
     * rejected) it returns false so the caller can surface a friendly message
     * instead of crashing.
     *
     * @return true if the chooser was launched.
     */
    fun shareDocument(context: Context, uri: Uri, mimeType: String): Boolean {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            // ClipData carries the grant to the target app on modern Android.
            clipData = ClipData.newRawUri("Export", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        grantToResolvers(context, send, uri)
        val chooser = Intent.createChooser(send, "Share").apply {
            // NEW_TASK is only valid (and only needed) off an Activity context;
            // adding it when launching from an Activity is what crashed export.
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

    /** True if at least one installed activity can handle [intent]. */
    private fun hasHandler(context: Context, intent: Intent): Boolean =
        context.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .isNotEmpty()

    /** Grant temporary read access to [uri] for every app that resolves [intent]. */
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

    /** Render a bitmap into a solid-white-backed copy (used before drawing). */
    fun flatten(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(result).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, 0f, 0f, null)
        }
        return result
    }
}
