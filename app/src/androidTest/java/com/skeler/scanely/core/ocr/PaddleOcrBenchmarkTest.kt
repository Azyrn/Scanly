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
import java.io.File
import kotlin.system.measureTimeMillis

/** Latency on a full A4-ish page. Results land in the app's external files dir. */
@RunWith(AndroidJUnit4::class)
class PaddleOcrBenchmarkTest {

    private fun page(): Bitmap {
        val bmp = createBitmap(1654, 2339)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 40f
        }
        repeat(35) { i ->
            canvas.drawText(
                "Line ${i + 1}: the quick brown fox jumps over the lazy dog 0123456789",
                120f,
                200f + i * 58f,
                paint
            )
        }
        return bmp
    }

    @Test
    fun measureFullPage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = PaddleOcrEngine(PaddleModelStore(context, OkHttpClient()))
        val bitmap = page()

        val warmup = engine.detect(bitmap, 960)
        engine.recognize(warmup.take(1).map { ImageOps.cropQuad(bitmap, it) }, ScriptPack.UNIVERSAL)

        var quads = warmup
        val detectMs = measureTimeMillis { quads = engine.detect(bitmap, 960) }

        var crops = emptyList<Bitmap>()
        val cropMs = measureTimeMillis { crops = quads.map { ImageOps.cropQuad(bitmap, it) } }

        val clsMs = measureTimeMillis { engine.detectFlippedLines(crops) }

        var text = ""
        val recMs = measureTimeMillis {
            text = engine.recognize(crops, ScriptPack.UNIVERSAL).joinToString("\n") { it.text }
        }

        val report = buildString {
            appendLine("page       : ${bitmap.width}x${bitmap.height}")
            appendLine("lines      : ${quads.size}")
            appendLine("detect     : $detectMs ms")
            appendLine("crop       : $cropMs ms")
            appendLine("line-orient: $clsMs ms")
            appendLine("recognize  : $recMs ms")
            appendLine("total      : ${detectMs + cropMs + clsMs + recMs} ms")
            appendLine("--- text ---")
            append(text.take(400))
        }
        File(context.getExternalFilesDir(null), "paddle_bench.txt").writeText(report)

        assertEquals(report, 35, quads.size)
        assertTrue(report, text.contains("Line 35: the quick brown fox jumps over the lazy dog"))
        assertTrue(report, detectMs < 2_000)
        assertTrue(report, recMs < 15_000)
    }
}
