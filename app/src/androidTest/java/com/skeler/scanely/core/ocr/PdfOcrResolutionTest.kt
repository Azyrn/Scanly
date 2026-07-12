package com.skeler.scanely.core.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.graphics.scale
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.skeler.scanely.core.ocr.paddle.ImageOps
import com.skeler.scanely.core.ocr.paddle.PaddleModelStore
import com.skeler.scanely.core.ocr.paddle.PaddleOcrEngine
import com.skeler.scanely.core.ocr.paddle.ScriptPack
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Document scans are OCR'd from a PDF re-render; at the old fixed 2x an A4's
 * 10pt body text reached the recognizer ~20px tall (native height is 48px).
 * Verifies the adaptive render feeds OCR full-detail pages.
 */
@RunWith(AndroidJUnit4::class)
class PdfOcrResolutionTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val lines = listOf(
        "Rental Agreement between the parties below",
        "Section 7.3 Liability is capped at 92,500.00 EUR",
        "the quick brown fox jumps over the lazy dog",
        "Payments are due within 30 days of invoice",
        "Signed in Berlin on 11 July 2026",
        "Reference number SCN-2026-004471",
        "All amendments require written consent",
        "Termination notice period is 90 days"
    )

    /** A4 in PDF points with 10pt body text, like a typical exported document. */
    private fun writePdf(pages: Int = 1): Uri {
        val doc = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 10f
        }
        repeat(pages) { p ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, p + 1).create())
            lines.forEachIndexed { i, line ->
                page.canvas.drawText(line, 60f, 80f + i * 26f, paint)
            }
            doc.finishPage(page)
        }
        val file = File(context.cacheDir, "res_test.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return Uri.fromFile(file)
    }

    private fun exactLines(engine: PaddleOcrEngine, page: Bitmap): Pair<Int, String> {
        val quads = engine.detect(page, 960).sortedBy { it.minY }
        val crops = quads.map { ImageOps.cropQuad(page, it) }
        val text = engine.recognize(crops, ScriptPack.UNIVERSAL).joinToString("\n") { it.text }
        crops.forEach { it.recycle() }
        return lines.count { text.contains(it) } to text
    }

    @Test
    fun adaptiveRenderMakesSmallPrintReadable() {
        val helper = PdfRendererHelper(context)
        val engine = PaddleOcrEngine(PaddleModelStore(context, OkHttpClient()))
        val uri = writePdf()

        val page = runBlocking { helper.renderPage(uri, 0) }
        assertNotNull("PDF render failed", page)
        val longSide = maxOf(page!!.width, page.height)
        assertTrue("render too small for OCR: ${page.width}x${page.height}", longSide in 3000..3300)

        val (newExact, newText) = exactLines(engine, page)

        // The old pipeline: fixed 2x of PDF points = 1190x1684 for this page.
        val old = page.scale((page.width * 1684f / page.height).toInt(), 1684)
        val (oldExact, oldText) = exactLines(engine, old)
        old.recycle()
        page.recycle()

        val report = buildString {
            appendLine("render      : ${page.width}x${page.height}")
            appendLine("new (adaptive): $newExact/${lines.size} exact")
            appendLine("old (fixed 2x): $oldExact/${lines.size} exact")
            appendLine("--- new text ---")
            appendLine(newText)
            appendLine("--- old 2x text ---")
            appendLine(oldText)
        }
        File(context.getExternalFilesDir(null), "pdf_resolution.txt").writeText(report)

        assertTrue("10pt text still misread at full render:\n$report", newExact >= lines.size - 1)
        assertTrue("adaptive render must not read worse than old 2x:\n$report", newExact >= oldExact)
    }

    // Camera scans embed a photo in the PDF page; the render scale decides how
    // much of the photo's detail survives. Arabic glyphs (dot-differentiated)
    // are where low render resolution actually costs letters.
    @Test
    fun embeddedPhotoArabicSurvivesAdaptiveRender() {
        val arabicLines = listOf(
            "تم حفظ الملف بنجاح",
            "نسخة جديدة للعقد المرفق",
            "يجب توقيع جميع الصفحات قبل الإرسال",
            "تنتهي مدة العقد بعد سنة واحدة"
        )
        val drawn = androidx.core.graphics.createBitmap(2380, 3366)
        android.graphics.Canvas(drawn).apply {
            drawColor(Color.rgb(232, 230, 226))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(60, 58, 55)
                textSize = 44f
            }
            arabicLines.forEachIndexed { i, line ->
                drawText(line, 200f, 400f + i * 150f, paint)
            }
        }
        // Camera-like degradation: JPEG artifacts + sensor noise.
        val jpeg = java.io.ByteArrayOutputStream().also {
            drawn.compress(Bitmap.CompressFormat.JPEG, 30, it)
        }.toByteArray()
        drawn.recycle()
        val decoded = android.graphics.BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        val photo = decoded.copy(Bitmap.Config.ARGB_8888, true)
        decoded.recycle()
        val px = IntArray(photo.width * photo.height)
        photo.getPixels(px, 0, photo.width, 0, 0, photo.width, photo.height)
        val rng = java.util.Random(42)
        for (i in px.indices) {
            val n = rng.nextInt(29) - 14
            val p = px[i]
            val r = ((p shr 16 and 0xFF) + n).coerceIn(0, 255)
            val g = ((p shr 8 and 0xFF) + n).coerceIn(0, 255)
            val b = ((p and 0xFF) + n).coerceIn(0, 255)
            px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        photo.setPixels(px, 0, photo.width, 0, 0, photo.width, photo.height)
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        page.canvas.drawBitmap(
            photo, null,
            android.graphics.Rect(0, 0, 595, 842),
            Paint(Paint.FILTER_BITMAP_FLAG)
        )
        doc.finishPage(page)
        photo.recycle()
        val file = File(context.cacheDir, "res_photo_test.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()

        val helper = PdfRendererHelper(context)
        val engine = PaddleOcrEngine(PaddleModelStore(context, OkHttpClient()))

        fun ocr(bmp: Bitmap): Pair<Int, String> {
            val quads = engine.detect(bmp, 960).sortedBy { it.minY }
            val crops = quads.map { ImageOps.cropQuad(bmp, it) }
            val text = engine.recognize(crops, ScriptPack.ARABIC).joinToString("\n") { it.text }
            crops.forEach { it.recycle() }
            return arabicLines.count { text.contains(it) } to text
        }

        val rendered = runBlocking { helper.renderPage(Uri.fromFile(file), 0) }!!
        val (newExact, newText) = ocr(rendered)
        val old = rendered.scale((rendered.width * 1684f / rendered.height).toInt(), 1684)
        val (oldExact, oldText) = ocr(old)
        old.recycle()
        rendered.recycle()

        val report = buildString {
            appendLine("new (adaptive): $newExact/${arabicLines.size} exact")
            appendLine("old (fixed 2x): $oldExact/${arabicLines.size} exact")
            appendLine("--- new ---")
            appendLine(newText)
            appendLine("--- old 2x ---")
            appendLine(oldText)
        }
        File(context.getExternalFilesDir(null), "pdf_resolution_arabic.txt").writeText(report)

        assertTrue("Arabic misread even at full render:\n$report", newExact >= arabicLines.size - 1)
        assertTrue("adaptive render must not read worse than old 2x:\n$report", newExact >= oldExact)
    }

    @Test
    fun forEachPageStreamsEveryPageAtFullResolution() {
        val helper = PdfRendererHelper(context)
        val uri = writePdf(pages = 3)
        val seen = mutableListOf<Pair<Int, Int>>()

        val opened = runBlocking {
            helper.forEachPage(uri) { index, bitmap ->
                seen.add(index to maxOf(bitmap.width, bitmap.height))
                bitmap.recycle()
            }
        }

        assertTrue("PDF failed to open", opened)
        assertEquals("wrong page count: $seen", listOf(0, 1, 2), seen.map { it.first })
        assertTrue(
            "pages not rendered at target resolution: $seen",
            seen.all { it.second in 3000..3300 }
        )
    }
}
