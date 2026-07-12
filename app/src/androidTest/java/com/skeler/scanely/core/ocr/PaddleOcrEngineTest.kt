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
import com.skeler.scanely.core.ocr.paddle.ScriptPack
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaddleOcrEngineTest {

    private lateinit var engine: PaddleOcrEngine

    private val lines = listOf(
        "Scanly offline OCR",
        "PP-OCRv6 small 2026",
        "Invoice #A-4471 total 92.50"
    )

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        engine = PaddleOcrEngine(PaddleModelStore(context, OkHttpClient()))
    }

    private fun page(): Bitmap {
        val bmp = createBitmap(900, 300)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 44f
        }
        lines.forEachIndexed { i, line ->
            canvas.drawText(line, 40f, 80f + i * 80f, paint)
        }
        return bmp
    }

    @Test
    fun detectsEveryTextLine() {
        val quads = engine.detect(page(), 960)
        assertEquals(lines.size, quads.size)
    }

    @Test
    fun recognizesRenderedText() {
        val bitmap = page()
        val quads = engine.detect(bitmap, 960).sortedBy { it.minY }
        val crops = quads.map { ImageOps.cropQuad(bitmap, it) }

        val recognized = engine.recognize(crops, ScriptPack.UNIVERSAL)
        val text = recognized.joinToString("\n") { it.text }

        lines.forEach { expected ->
            assertTrue("Missing '$expected' in:\n$text", text.contains(expected))
        }
        assertTrue(recognized.all { it.confidence > 0.7f })
    }

    @Test
    fun flagsUpsideDownLines() {
        val bitmap = page()
        val quad = engine.detect(bitmap, 960).minBy { it.minY }
        val crop = ImageOps.cropQuad(bitmap, quad)
        val flipped = ImageOps.rotate180(crop)

        val flags = engine.detectFlippedLines(listOf(crop, flipped))
        assertEquals(false, flags[0])
        assertEquals(true, flags[1])
    }

    @Test
    fun detectsPageRotation() {
        val upright = page()
        assertEquals(0, engine.detectPageRotation(upright))
    }
}
