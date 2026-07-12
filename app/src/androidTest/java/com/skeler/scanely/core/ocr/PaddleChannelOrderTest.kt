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
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A/B for PaddleOCR's img_mode BGR quirk: colored + low-contrast text where
 * R and B channels genuinely differ (grayscale pages can't tell the orders apart).
 * Writes rgb-vs-bgr metrics to files/channel_ab.txt for inspection.
 */
@RunWith(AndroidJUnit4::class)
class PaddleChannelOrderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private data class Case(val text: String, val fg: Int, val bg: Int)

    private val cases = listOf(
        Case("Blue ink on white paper 123", Color.rgb(25, 45, 180), Color.WHITE),
        Case("Red stamp OVERDUE 2026", Color.rgb(200, 30, 30), Color.WHITE),
        Case("Green ledger entry 4471", Color.rgb(20, 120, 45), Color.WHITE),
        Case("Orange text on light blue 55", Color.rgb(225, 130, 20), Color.rgb(205, 225, 245)),
        Case("White INVOICE on navy", Color.WHITE, Color.rgb(20, 30, 90)),
        Case("Low contrast lavender 9876", Color.rgb(120, 120, 205), Color.rgb(228, 228, 250))
    )

    private fun coloredPage(): Bitmap {
        val bmp = createBitmap(1000, 120 * cases.size + 40)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 46f }
        cases.forEachIndexed { i, case ->
            val top = 20f + i * 120f
            paint.color = case.bg
            canvas.drawRect(0f, top, bmp.width.toFloat(), top + 120f, paint)
            paint.color = case.fg
            canvas.drawText(case.text, 40f, top + 78f, paint)
        }
        return bmp
    }

    @After
    fun resetChannelOrder() {
        ImageOps.bgrInput = false
    }

    @Test
    fun comparesRgbAndBgrOnColoredText() {
        val engine = PaddleOcrEngine(PaddleModelStore(context, OkHttpClient()))
        val page = coloredPage()

        fun run(bgr: Boolean): Triple<Int, Float, String> {
            ImageOps.bgrInput = bgr
            val quads = engine.detect(page, 960).sortedBy { it.minY }
            val crops = quads.map { ImageOps.cropQuad(page, it) }
            val rec = engine.recognize(crops, ScriptPack.UNIVERSAL)
            crops.forEach { it.recycle() }
            val text = rec.joinToString("\n") { it.text }
            val exact = cases.count { text.contains(it.text) }
            val conf = rec.filter { it.text.isNotBlank() }
                .map { it.confidence }.average().toFloat()
            return Triple(exact, conf, text)
        }

        val rgb = run(false)
        val bgr = run(true)

        val report = buildString {
            appendLine("RGB: exact=${rgb.first}/${cases.size} meanConf=${rgb.second}")
            appendLine("BGR: exact=${bgr.first}/${cases.size} meanConf=${bgr.second}")
            appendLine("--- RGB text ---")
            appendLine(rgb.third)
            appendLine("--- BGR text ---")
            appendLine(bgr.third)
        }
        File(context.getExternalFilesDir(null), "channel_ab.txt").writeText(report)

        assertTrue("both orders failed to read colored text:\n$report", rgb.first >= 4 || bgr.first >= 4)
        page.recycle()
    }
}
