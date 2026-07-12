package com.skeler.scanely.core.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.skeler.scanely.core.ocr.paddle.ImageOps
import com.skeler.scanely.core.ocr.paddle.PaddleModelStore
import com.skeler.scanely.core.ocr.paddle.PaddleOcrEngine
import com.skeler.scanely.core.ocr.paddle.RtlText
import com.skeler.scanely.core.ocr.paddle.ScriptPack
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PaddleArabicRtlTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // Logical-order Arabic: "the file was saved successfully", "new copy 123",
    // and a mixed-direction line with Latin + digits.
    private val lines = listOf(
        "تم حفظ الملف بنجاح",
        "نسخة جديدة رقم 123",
        "ملف PDF جاهز للتصدير"
    )

    private fun arabicPage(): Bitmap {
        val bmp = createBitmap(1100, 420)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 52f
        }
        lines.forEachIndexed { i, line ->
            canvas.drawText(line, 80f, 110f + i * 120f, paint)
        }
        return bmp
    }

    @Test
    fun arabicComesOutInLogicalOrder() {
        val engine = PaddleOcrEngine(PaddleModelStore(context, OkHttpClient()))
        val page = arabicPage()
        val quads = engine.detect(page, 960).sortedBy { it.minY }
        val crops = quads.map { ImageOps.cropQuad(page, it) }
        val rec = engine.recognize(crops, ScriptPack.ARABIC)
        crops.forEach { it.recycle() }
        page.recycle()

        val report = buildString {
            appendLine("quads=${quads.size}")
            rec.forEachIndexed { i, line ->
                appendLine("[$i] conf=${line.confidence} text=${line.text}")
                appendLine("[$i] codepoints=${line.text.map { String.format("U+%04X", it.code) }}")
            }
            appendLine("expected (logical):")
            lines.forEach { appendLine("    $it") }
        }
        File(context.getExternalFilesDir(null), "arabic_rtl.txt").writeText(report)

        assertTrue("no text recognized:\n$report", rec.any { it.text.isNotBlank() })
        assertEquals("line 0 not logical order:\n$report", lines[0], rec[0].text)
        assertTrue(
            "line 1 not logical order:\n$report",
            rec[1].text.contains("نسخة جديدة")
        )
        assertEquals("mixed RTL/LTR line not logical order:\n$report", lines[2], rec[2].text)
    }

    @Test
    fun visualToLogicalHandlesMixedRuns() {
        // Pure LTR untouched
        assertEquals("Invoice 92.50", RtlText.visualToLogical("Invoice 92.50"))
        // Visual "ريدصتلل زهاج PDF فلم" -> logical, Latin run preserved
        assertEquals("ملف PDF جاهز للتصدير", RtlText.visualToLogical("ريدصتلل زهاج PDF فلم"))
        // Digits with joiner stay LTR: visual "3.14 ةبسنلا" -> logical
        assertEquals("النسبة 3.14", RtlText.visualToLogical("3.14 ةبسنلا"))
        // Mirrored brackets swap: visual "(ةخسن)" -> logical
        assertEquals("(نسخة)", RtlText.visualToLogical("(ةخسن)"))
    }
}
