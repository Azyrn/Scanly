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
import com.skeler.scanely.core.ocr.paddle.PaddleOcrService
import com.skeler.scanely.core.ocr.paddle.ScriptPack
import com.skeler.scanely.settings.data.SettingsKeys
import com.skeler.scanely.settings.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The document orientation classifier centre-crops to 224, so on a photo where the page does not
 * fill the frame it judges mostly margin and answers "180" whichever way up the page is. The
 * pipeline used to act on that and turn the page over, which reversed the reading order and left
 * every short line — the ones the per-line classifier declines to touch — upside down and garbled.
 * Upside-down is now settled by recognition, so the page must come back upright either way up.
 */
@RunWith(AndroidJUnit4::class)
class PaddlePageOrientationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val engine = PaddleOcrEngine(PaddleModelStore(context, OkHttpClient()))

    private val first = "مرحبا بك في تطبيق سكانلي"
    private val last = "تم حفظ الملف بنجاح"

    private class FakeSettings(private val script: ScriptPack) : SettingsRepository {
        override fun getBoolean(key: SettingsKeys): Flow<Boolean> = flowOf(
            when (key) {
                SettingsKeys.PADDLE_DOC_ORIENTATION -> true
                else -> false
            }
        )

        override fun getString(key: SettingsKeys): Flow<String> = flowOf(
            if (key == SettingsKeys.PADDLE_SCRIPT) script.id else ""
        )

        override suspend fun setBoolean(key: SettingsKeys, value: Boolean) = Unit
        override suspend fun toggleSetting(key: SettingsKeys) = Unit
        override fun getInt(key: SettingsKeys): Flow<Int> = flowOf(0)
        override suspend fun setInt(key: SettingsKeys, value: Int) = Unit
        override fun getFloat(key: SettingsKeys): Flow<Float> = flowOf(0f)
        override suspend fun setFloat(key: SettingsKeys, value: Float) = Unit
        override suspend fun setString(key: SettingsKeys, value: String) = Unit
        override fun getStringSet(key: SettingsKeys): Flow<Set<String>> = flowOf(emptySet())
        override suspend fun setStringSet(key: SettingsKeys, value: Set<String>) = Unit
    }

    private fun service() = PaddleOcrService(
        engine,
        FakeSettings(ScriptPack.ARABIC),
        PdfRendererHelper(context)
    )

    /** Long first line, deliberately short middle line, long last line. */
    private fun arabicPage(): Bitmap {
        val bmp = createBitmap(1240, 700)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 44f
        }
        listOf(first, "هذا مقصود.", last).forEachIndexed { i, line ->
            canvas.drawText(line, 100f, 160f + i * 140f, paint)
        }
        return bmp
    }

    private fun textOf(bmp: Bitmap): String = runBlocking {
        val result = service().recognize(bmp)
        assertTrue("page did not recognize: $result", result is OcrResult.Success)
        (result as OcrResult.Success).text
    }

    @Test
    fun uprightArabicPageIsNotTurnedOver() {
        val page = arabicPage()
        val text = textOf(page)

        val top = text.indexOf(first.take(6))
        val bottom = text.indexOf(last.take(6))
        assertTrue("first line missing from: $text", top >= 0)
        assertTrue("last line missing from: $text", bottom >= 0)
        assertTrue("page was read bottom-up: $text", top < bottom)
        page.recycle()
    }

    /** A page that really is upside down still has to come back the right way round. */
    @Test
    fun upsideDownArabicPageIsTurnedBack() {
        val page = arabicPage()
        val flipped = ImageOps.rotate(page, 180)
        val text = textOf(flipped)

        val top = text.indexOf(first.take(6))
        val bottom = text.indexOf(last.take(6))
        assertTrue("first line missing from: $text", top >= 0)
        assertTrue("upside-down page was not corrected: $text", top < bottom)
        page.recycle()
        flipped.recycle()
    }

    /**
     * The decision itself, independent of what the orientation classifier happens to say: it is
     * the vote that has to stay sound, because on a loosely framed photo it is the only thing
     * standing between an upright page and a reversed one.
     */
    @Test
    fun recognitionVoteDecidesWhichWayUpThePageIs() {
        val page = arabicPage()
        val quads = engine.detect(page, 960)
        val pixels = ImageOps.Page(page)
        val upright = quads.map { ImageOps.cropQuad(pixels, it) }
        val flipped = upright.map { ImageOps.rotate180(it) }
        val service = service()

        assertEquals(
            "upright page must not be turned over",
            false,
            service.isUpsideDown(upright, ScriptPack.ARABIC)
        )
        assertEquals(
            "upside-down page must be turned back",
            true,
            service.isUpsideDown(flipped, ScriptPack.ARABIC)
        )

        (upright + flipped).forEach { it.recycle() }
        page.recycle()
    }
}
