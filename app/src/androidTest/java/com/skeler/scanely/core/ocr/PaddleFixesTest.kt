package com.skeler.scanely.core.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.StrictMode
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.skeler.scanely.core.ocr.paddle.CtcDecoder
import com.skeler.scanely.core.ocr.paddle.ImageOps
import com.skeler.scanely.core.ocr.paddle.PaddleModelStore
import com.skeler.scanely.core.ocr.paddle.PaddleOcrEngine
import com.skeler.scanely.core.ocr.paddle.Quad
import com.skeler.scanely.core.ocr.paddle.RecLine
import com.skeler.scanely.core.ocr.paddle.ScriptPack
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.math.exp

@RunWith(AndroidJUnit4::class)
class PaddleFixesTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun newEngine() = PaddleOcrEngine(PaddleModelStore(context, OkHttpClient()))

    private fun textPage(w: Int = 1240, h: Int = 1754, lineCount: Int = 20): Bitmap {
        val bmp = createBitmap(w, h)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 40f
        }
        repeat(lineCount) { i ->
            canvas.drawText(
                "Line ${i + 1}: the quick brown fox jumps over the lazy dog",
                100f,
                160f + i * 64f,
                paint
            )
        }
        return bmp
    }

    // Fix 1a: Bitmap.createBitmap returns src itself for a full-bitmap rect,
    // so a full-image quad crop must be copied or callers double-recycle the page.
    @Test
    fun fullImageQuadCropIsIndependentOfSource() {
        val src = createBitmap(400, 100)
        src.eraseColor(Color.WHITE)
        val quad = Quad(floatArrayOf(0f, 0f, 400f, 0f, 400f, 100f, 0f, 100f))

        val crop = ImageOps.cropQuad(src, quad)

        assertNotSame("full-image quad returned the source bitmap", src, crop)
        crop.recycle()
        assertFalse("recycling the crop recycled the source page", src.isRecycled)
        src.getPixel(0, 0)
        src.recycle()
    }

    // Fix 1b: close() during inference must defer, not free native sessions mid-run.
    @Test
    fun closeDuringInferenceDefersAndEngineRecovers() {
        val engine = newEngine()
        val page = textPage()
        val quads = engine.detect(page, 960)
        assertTrue("expected many lines, got ${quads.size}", quads.size >= 15)
        val crops = quads.map { ImageOps.cropQuad(page, it) }

        var result: List<RecLine>? = null
        var failure: Throwable? = null
        val worker = thread {
            try {
                result = engine.recognize(crops, ScriptPack.UNIVERSAL)
            } catch (t: Throwable) {
                failure = t
            }
        }
        Thread.sleep(400)
        engine.close()
        worker.join()

        assertNull("recognize threw after close(): $failure", failure)
        assertTrue(
            "recognize returned no text after mid-flight close()",
            result.orEmpty().count { it.text.isNotBlank() } >= 15
        )
        assertEquals(
            "deferred close must run once inference drains",
            0,
            engine.openSessionCount()
        )
        assertEquals(
            "engine must recover by recreating sessions",
            quads.size,
            engine.detect(page, 960).size
        )
        crops.forEach { it.recycle() }
        page.recycle()
    }

    // Fix 2b: PaddleClas eval preprocessing (resize_short 256 + centre-crop 224)
    // must classify rotated non-square pages; correction must round-trip to upright.
    @Test
    fun pageRotationRoundTripsOnPortraitPage() {
        val engine = newEngine()
        val page = textPage()
        val results = StringBuilder()

        for (applied in listOf(0, 90, 180, 270)) {
            val rotated = ImageOps.rotate(page, applied)
            val detected = engine.detectPageRotation(rotated)
            val corrected = ImageOps.rotate(rotated, detected)
            val residual = engine.detectPageRotation(corrected)
            results.append("applied=$applied detected=$detected residual=$residual; ")

            assertEquals(
                "correction did not restore upright: $results",
                0,
                residual
            )
            val quads = engine.detect(corrected, 960)
            assertTrue(
                "corrected page lost text lines (${quads.size}): $results",
                quads.size >= 15
            )
            if (corrected !== rotated) corrected.recycle()
            if (rotated !== page) rotated.recycle()
        }
        page.recycle()
    }

    // Fix 3a: confidence path is decided per output tensor by row sum, not by
    // whether a single value happens to land in [0,1].
    @Test
    fun ctcConfidenceIsDeterministicForSoftmaxAndRawLogits() {
        val charset = listOf("", "a", "b", " ")

        val soft = floatArrayOf(
            0.05f,
            0.80f,
            0.10f,
            0.05f,
            0.90f,
            0.04f,
            0.03f,
            0.03f
        )
        assertTrue(CtcDecoder.isSoftmaxed(soft, 4))
        val softLine = CtcDecoder.decodeBatch(soft, 1, 2, 4, charset).single()
        assertEquals("a", softLine.text)
        assertEquals(0.80f, softLine.confidence, 1e-4f)

        // Raw logits whose max lands in [0,1] — the old range heuristic
        // would have reported 0.9 as a probability.
        val raw = floatArrayOf(
            0.5f,
            0.9f,
            -3.0f,
            -2.0f,
            5.0f,
            1.0f,
            0.0f,
            0.0f
        )
        assertFalse(CtcDecoder.isSoftmaxed(raw, 4))
        val rawLine = CtcDecoder.decodeBatch(raw, 1, 2, 4, charset).single()
        assertEquals("a", rawLine.text)
        val z = exp(0.5f - 0.9f) + 1f + exp(-3.0f - 0.9f) + exp(-2.0f - 0.9f)
        assertEquals(1f / z, rawLine.confidence, 1e-4f)
    }

    // Fix 3b: LFS pointer parsing feeding SHA/size verification.
    @Test
    fun parsesLfsPointerShaAndSize() {
        val sha = "a".repeat(32) + "0123456789abcdef0123456789abcdef"
        val pointer = PaddleModelStore.parseLfsPointer(
            "version https://git-lfs.github.com/spec/v1\noid sha256:$sha\nsize 8388608\n"
        )
        assertEquals(sha, pointer?.sha256)
        assertEquals(8_388_608L, pointer?.size)

        assertNull(PaddleModelStore.parseLfsPointer("not a pointer"))
        assertNull(PaddleModelStore.parseLfsPointer("oid sha256:zz\nsize 5"))
    }

    // Fix 3b end-to-end: real download of the smallest pack, verified against the
    // repo's LFS sha, then usable as a recognizer charset. Requires network.
    @Test
    fun downloadsGreekPackWithShaVerification() {
        val store = PaddleModelStore(context, OkHttpClient())
        store.delete(ScriptPack.GREEK)
        try {
            val result = runBlocking { store.download(ScriptPack.GREEK) }
            assertTrue(
                "download failed: ${result.exceptionOrNull()}",
                result.isSuccess
            )
            assertTrue("pack not installed after download", store.isInstalled(ScriptPack.GREEK))
            val (model, charset) = store.recModel(ScriptPack.GREEK)
            assertTrue("model too small: ${model.size}", model.size > 1_000_000)
            assertTrue("charset too small: ${charset.size}", charset.size > 10)
            assertEquals("", charset.first())
            assertEquals(" ", charset.last())
        } finally {
            store.delete(ScriptPack.GREEK)
        }
    }

    // Fix 3c: constructing the store (as eager Hilt injection does at startup)
    // must not touch disk on the calling thread.
    @Test
    fun modelStoreConstructorDoesNoDiskIoOnMainThread() {
        val client = OkHttpClient()
        val violations = CopyOnWriteArrayList<String>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val old = StrictMode.getThreadPolicy()
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .penaltyListener({ it.run() }) { violation ->
                        violations.add(violation.toString())
                    }
                    .build()
            )
            try {
                PaddleModelStore(context, client)
            } finally {
                StrictMode.setThreadPolicy(old)
            }
        }
        assertTrue("disk I/O on main thread during construction: $violations", violations.isEmpty())
    }
}
