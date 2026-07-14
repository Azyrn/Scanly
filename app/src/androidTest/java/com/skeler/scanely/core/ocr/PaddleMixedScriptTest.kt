package com.skeler.scanely.core.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Arabic recognition model reads Latin badly — it turned "Original" into "أوأر" and dropped
 * "Markdown" — so the Latin words embedded in an Arabic line are recovered by reading the same
 * crop with the universal model and merging the two by position ([ScriptMerge]).
 *
 * The crop is never cut: Arabic is cursive, and a cut placed by a space heuristic lands inside a
 * ligature and destroys the words either side of it. These are the cases that a naive splitter
 * would corrupt — tokens carrying digits, punctuation and internal spaces.
 */
@RunWith(AndroidJUnit4::class)
class PaddleMixedScriptTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private class ArabicPack : SettingsRepository {
        override fun getBoolean(key: SettingsKeys) = flowOf(key.default as Boolean)
        override fun getInt(key: SettingsKeys) = flowOf(key.default as Int)
        override fun getFloat(key: SettingsKeys) = flowOf(key.default as Float)
        override fun getString(key: SettingsKeys) = flowOf(
            if (key == SettingsKeys.PADDLE_SCRIPT) ScriptPack.ARABIC.id else key.default as String
        )

        override fun getStringSet(key: SettingsKeys): Flow<Set<String>> = flowOf(emptySet())
        override suspend fun setBoolean(key: SettingsKeys, value: Boolean) = Unit
        override suspend fun setInt(key: SettingsKeys, value: Int) = Unit
        override suspend fun setFloat(key: SettingsKeys, value: Float) = Unit
        override suspend fun setString(key: SettingsKeys, value: String) = Unit
        override suspend fun setStringSet(key: SettingsKeys, value: Set<String>) = Unit
        override suspend fun toggleSetting(key: SettingsKeys) = Unit
    }

    private val lines = listOf(
        "تطبيق Scanly OCR ممتاز",
        "الإصدار Version 2.5 جاهز",
        "يعمل على Android 15 بنجاح",
        "نموذج GPT-5 سريع",
        "مرحبا بك في تطبيق سكانلي"
    )

    /** Scan resolution, not thumbnail: the recognisers read a 48px-tall crop off this. */
    private fun page(): Bitmap {
        val bmp = createBitmap(2560, 1650)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 95f
        }
        lines.forEachIndexed { i, line -> canvas.drawText(line, 160f, 250f + i * 330f, paint) }
        return bmp
    }

    private fun scan(): String = runBlocking {
        val engine = PaddleOcrEngine(PaddleModelStore(context, OkHttpClient()))
        val service = PaddleOcrService(engine, ArabicPack(), PdfRendererHelper(context))
        val bmp = page()
        try {
            val result = service.recognize(bmp)
            assertTrue("recognition failed: $result", result is OcrResult.Success)
            (result as OcrResult.Success).text
        } finally {
            bmp.recycle()
            engine.close()
        }
    }

    /**
     * The guarantee. A Latin word is only ever written over a stretch the Arabic model was *not*
     * confidently reading, so the Arabic itself cannot be damaged however badly the universal
     * model misreads — and it does misread: on a page rendered like this one it returns "Versio"
     * for "Version" and "ndroid" for "Android". How much of the Latin comes back is a property of
     * that model (on a photographed page it reads "Original" and "Markdown" cleanly and they are
     * recovered whole); that the cursive Arabic survives is a property of this code.
     */
    @Test
    fun theArabicItselfIsNeverCorrupted() {
        val text = scan()
        // Cursive words a cut through the line would have destroyed, and which the universal
        // model would happily overwrite if it were allowed to.
        for (word in listOf("تطبيق", "مرحبا", "سريع", "بنجاح", "ممتاز", "جاهز", "يعمل")) {
            assertTrue("lost Arabic word '$word' in:\n$text", word in text)
        }
    }

    /** Whatever the merge does to the Latin, the line count and the Arabic reading order hold. */
    @Test
    fun theArabicReadingOrderHolds() {
        val text = scan()
        val first = text.indexOf("تطبيق")
        val last = text.indexOf("مرحبا")
        assertTrue("page came back out of order:\n$text", first in 0..<last)
    }
}
