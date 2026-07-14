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
import com.skeler.scanely.core.ocr.paddle.RecLine
import com.skeler.scanely.core.ocr.paddle.ScriptPack
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * An Arabic page scanned on the default (universal) pack used to come back empty: the
 * universal dictionary has no Arabic glyphs. Recognition must fall back to a bundled pack
 * that does, without the user having to know the setting exists.
 */
@RunWith(AndroidJUnit4::class)
class PaddleScriptFallbackTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val engine = PaddleOcrEngine(PaddleModelStore(context, OkHttpClient()))

    private val arabic = listOf("تم حفظ الملف بنجاح", "مرحبا بك في تطبيق سكانلي")
    private val latin = listOf("Invoice number 4471", "Total due 92.50")

    private fun page(lines: List<String>, textSize: Float = 40f): Bitmap {
        val bmp = createBitmap(1240, 700)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            this.textSize = textSize
        }
        lines.forEachIndexed { i, line -> canvas.drawText(line, 100f, 150f + i * 120f, paint) }
        return bmp
    }

    private fun recognize(bmp: Bitmap, pack: ScriptPack): List<RecLine> {
        val quads = engine.detect(bmp, 960).sortedBy { it.minY }
        val pixels = ImageOps.Page(bmp)
        val crops = quads.map { ImageOps.cropQuad(pixels, it) }
        val lines = engine.recognize(crops, pack)
        crops.forEach { it.recycle() }
        return lines
    }

    /** The condition the service keys the fallback off: detected well, recognized nothing. */
    @Test
    fun universalRecognizesAlmostNothingOnArabic() {
        val bmp = page(arabic)
        val chars = recognize(bmp, ScriptPack.UNIVERSAL)
            .sumOf { if (it.confidence >= 0.3f) it.text.trim().length else 0 }
        val arabicChars = recognize(bmp, ScriptPack.ARABIC)
            .sumOf { if (it.confidence >= 0.3f) it.text.trim().length else 0 }

        assertTrue("universal should read ~nothing on Arabic, got $chars", chars < 5)
        assertTrue("arabic pack should read the page, got $arabicChars", arabicChars > 30)
    }

    /** The fallback must not fire on a Latin page: universal already reads it. */
    @Test
    fun universalStaysOnLatinPage() {
        val lines = recognize(page(latin), ScriptPack.UNIVERSAL)
        val usable = lines.count { it.confidence >= 0.3f && it.text.isNotBlank() }
        assertTrue("universal should read the Latin page, got $lines", usable >= 2)
        assertTrue(lines.any { it.text.contains("4471") })
    }
}
