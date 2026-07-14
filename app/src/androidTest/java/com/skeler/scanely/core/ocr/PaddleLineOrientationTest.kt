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
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The orientation classifier used to be fed a letterboxed crop, which is off-distribution:
 * it answered near 0.5 and "corrected" upright Arabic lines into garbage. Both directions
 * are asserted here — a false positive silently destroys a line that recognized fine.
 */
@RunWith(AndroidJUnit4::class)
class PaddleLineOrientationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val engine = PaddleOcrEngine(PaddleModelStore(context, OkHttpClient()))

    private val arabic = listOf(
        "الجن الكافر : الاذان",
        "العين المعجبه : سورة يوسف",
        "السحر المعقود : سورة الفلق",
        "المس العاشق : المسك الاسود"
    )

    private fun page(): Bitmap {
        val bmp = createBitmap(1080, 700)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 54f
        }
        arabic.forEachIndexed { i, line -> canvas.drawText(line, 90f, 140f + i * 140f, paint) }
        return bmp
    }

    private fun crops(bmp: Bitmap): List<Bitmap> {
        val quads = engine.detect(bmp, 960).sortedBy { it.minY }
        val pixels = ImageOps.Page(bmp)
        return quads.map { ImageOps.cropQuad(pixels, it) }
    }

    @Test
    fun uprightArabicLinesAreNeverFlagged() {
        val lines = crops(page())
        val flags = engine.detectFlippedLines(lines)
        val falsePositives = flags.count { it }

        assertEquals("upright lines flagged as upside down: $falsePositives", 0, falsePositives)
        lines.forEach { it.recycle() }
    }

    @Test
    fun rotatedArabicLinesAreFlagged() {
        val lines = crops(page()).map { ImageOps.rotate180(it).also { _ -> it.recycle() } }
        val flags = engine.detectFlippedLines(lines)

        assertTrue("upside-down lines not detected: ${flags.toList()}", flags.all { it })
        lines.forEach { it.recycle() }
    }

    /** The end the user sees: a false flip turns a confident line into low-confidence noise. */
    @Test
    fun arabicSurvivesTheOrientationStage() {
        val bmp = page()
        val lines = crops(bmp)
        val flags = engine.detectFlippedLines(lines)
        val oriented = lines.mapIndexed { i, c -> if (flags[i]) ImageOps.rotate180(c) else c }
        val rec = engine.recognize(oriented, ScriptPack.ARABIC)

        assertTrue(
            "Arabic degraded through the orientation stage: $rec",
            rec.all { it.confidence > 0.8f }
        )
        assertTrue(rec.any { it.text.contains("سورة يوسف") })
        oriented.forEach { it.recycle() }
    }
}
