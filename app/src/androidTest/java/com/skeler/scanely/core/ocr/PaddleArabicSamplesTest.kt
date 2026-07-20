package com.skeler.scanely.core.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.skeler.scanely.core.ocr.paddle.PaddleModelStore
import com.skeler.scanely.core.ocr.paddle.PaddleOcrEngine
import com.skeler.scanely.core.ocr.paddle.PaddleOcrService
import com.skeler.scanely.settings.data.SettingsKeys
import com.skeler.scanely.settings.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Accuracy harness for the offline PaddleOCR pipeline on real Arabic documents.
 * Reads sample images bundled in androidTest assets, runs the full service, and
 * scores the output against ground truth. Writes a full report to external files.
 */
@RunWith(AndroidJUnit4::class)
class PaddleArabicSamplesTest {

    private val instr = InstrumentationRegistry.getInstrumentation()
    private val appCtx = instr.targetContext
    private val testCtx = instr.context

    private class FixedSettings(private val values: Map<SettingsKeys, Any>) : SettingsRepository {
        override fun getBoolean(key: SettingsKeys): Flow<Boolean> =
            flowOf(values[key] as? Boolean ?: (key.default as? Boolean ?: false))
        override fun getInt(key: SettingsKeys): Flow<Int> =
            flowOf(values[key] as? Int ?: (key.default as? Int ?: 0))
        override fun getFloat(key: SettingsKeys): Flow<Float> =
            flowOf(values[key] as? Float ?: (key.default as? Float ?: 0f))
        override fun getString(key: SettingsKeys): Flow<String> =
            flowOf(values[key] as? String ?: (key.default as? String ?: ""))
        override fun getStringSet(key: SettingsKeys): Flow<Set<String>> = flowOf(emptySet())
        override suspend fun setBoolean(key: SettingsKeys, value: Boolean) = Unit
        override suspend fun toggleSetting(key: SettingsKeys) = Unit
        override suspend fun setInt(key: SettingsKeys, value: Int) = Unit
        override suspend fun setFloat(key: SettingsKeys, value: Float) = Unit
        override suspend fun setString(key: SettingsKeys, value: String) = Unit
        override suspend fun setStringSet(key: SettingsKeys, value: Set<String>) = Unit
    }

    private fun service(): PaddleOcrService {
        val engine = PaddleOcrEngine(PaddleModelStore(appCtx, OkHttpClient()))
        val settings = FixedSettings(
            mapOf(
                SettingsKeys.PADDLE_SCRIPT to "arabic",
                SettingsKeys.PADDLE_DOC_ORIENTATION to false,
                SettingsKeys.PADDLE_DOC_UNWARP to false,
                SettingsKeys.PADDLE_LINE_ORIENTATION to false,
                SettingsKeys.PADDLE_STRUCTURE to false
            )
        )
        return PaddleOcrService(engine, settings, PdfRendererHelper(appCtx))
    }

    private fun asset(name: String): Bitmap {
        testCtx.assets.open("ocr_ar/$name").use {
            return BitmapFactory.decodeStream(it)
        }
    }

    private data class Sample(val file: String, val label: String, val groundTruth: String)

    @Test
    fun scoreArabicSamples() {
        val samples = listOf(
            Sample("img1_paragraph.png", "img1 religious paragraph", GT_IMG1),
            Sample("img2_alwatan.png", "img2 Al-Watan newspaper", GT_IMG2),
            Sample("img3_munch.png", "img3 Munch article", GT_IMG3)
        )

        val svc = service()
        val report = StringBuilder()
        report.appendLine("=== PaddleOCR Arabic accuracy run ===")

        for (s in samples) {
            val bmp = asset(s.file)
            val result = runBlocking { svc.recognize(bmp) }
            bmp.recycle()

            val text = when (result) {
                is OcrResult.Success -> result.text
                is OcrResult.Error -> "ERROR: ${result.message}"
                OcrResult.Empty -> "EMPTY"
            }
            val conf = (result as? OcrResult.Success)?.confidence ?: 0f

            report.appendLine()
            report.appendLine("################ ${s.label} (${s.file}) ################")
            report.appendLine("confidence=$conf")
            if (s.groundTruth.isNotBlank()) {
                val raw = similarity(normalize(text, keepDiacritics = true),
                    normalize(s.groundTruth, keepDiacritics = true))
                val norm = similarity(normalize(text, keepDiacritics = false),
                    normalize(s.groundTruth, keepDiacritics = false))
                report.appendLine("charAccuracy(raw)=${pct(raw)}  charAccuracy(diacritics-insensitive)=${pct(norm)}")
                report.appendLine("lineRecall=${lineRecall(text, s.groundTruth)}")
            }
            report.appendLine("---- OCR OUTPUT ----")
            report.appendLine(text)
            if (s.groundTruth.isNotBlank()) {
                report.appendLine("---- GROUND TRUTH ----")
                report.appendLine(s.groundTruth)
            }
            report.appendLine("################ end ${s.label} ################")
        }

        val out = File(appCtx.getExternalFilesDir(null), "arabic_samples.txt")
        out.writeText(report.toString())
        // Echo into instrumentation status so it survives the connected-test wipe.
        println("REPORT_PATH=${out.absolutePath}")
    }

    @Test
    fun sweepWordGapFactor() {
        val engine = PaddleOcrEngine(PaddleModelStore(appCtx, OkHttpClient()))
        val bmp = asset("img1_paragraph.png")
        val quads = engine.detect(bmp, 960).sortedBy { it.minY }
        val crops = quads.map { com.skeler.scanely.core.ocr.paddle.ImageOps.cropQuad(bmp, it) }
        // Raw CTC lines (no space recovery), Arabic pack.
        val rec = engine.recognize(crops, com.skeler.scanely.core.ocr.paddle.ScriptPack.ARABIC)
        crops.forEach { it.recycle() }
        bmp.recycle()

        val sb = StringBuilder()
        val factors = listOf(0f, 1.7f, 1.9f, 2.1f, 2.3f, 2.5f, 2.8f)
        for (f in factors) {
            val page = rec.joinToString("\n") { line ->
                if (f == 0f) line.text
                else com.skeler.scanely.core.ocr.paddle.RtlText.visualToLogical(
                    com.skeler.scanely.core.ocr.paddle.RtlText.recoverWordSpaces(line.chars, line.text, f)
                )
            }
            val acc = similarity(normalize(page, false), normalize(GT_IMG1, false))
            sb.appendLine("factor=$f  charAccuracy(diacritics-insensitive)=${pct(acc)}")
        }
        File(appCtx.getExternalFilesDir(null), "sweep.txt").writeText(sb.toString())
        println(sb.toString())
    }

    // Order-independent: how many ground-truth lines survive (diacritics-insensitively) as a
    // near-match window in the output — fair on a multi-column page whose reading order interleaves.
    private fun lineRecall(text: String, groundTruth: String): String {
        val hay = normalize(text, keepDiacritics = false)
        val lines = groundTruth.lines().map { normalize(it, keepDiacritics = false) }.filter { it.length > 3 }
        var hit = 0
        val misses = mutableListOf<String>()
        for (gt in lines) {
            if (bestWindowSimilarity(hay, gt) >= 0.8) hit++ else misses.add(gt)
        }
        return "$hit/${lines.size}" + if (misses.isEmpty()) "" else "  misses=$misses"
    }

    private fun bestWindowSimilarity(hay: String, needle: String): Double {
        if (needle.isEmpty()) return 1.0
        if (hay.length < needle.length) return similarity(hay, needle)
        var best = 0.0
        val n = needle.length
        var i = 0
        while (i + n <= hay.length) {
            best = maxOf(best, similarity(hay.substring(i, i + n), needle))
            if (best == 1.0) break
            i++
        }
        return best
    }

    private fun pct(v: Double): String = String.format("%.1f%%", v * 100)

    private fun normalize(s: String, keepDiacritics: Boolean): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            when {
                c.isWhitespace() -> if (sb.isNotEmpty() && sb.last() != ' ') sb.append(' ')
                !keepDiacritics && c.code in 0x064B..0x0652 -> {}
                !keepDiacritics && c == 'ـ' -> {}
                else -> sb.append(if (!keepDiacritics) foldArabic(c) else c)
            }
        }
        return sb.toString().trim()
    }

    private fun foldArabic(c: Char): Char = when (c) {
        'أ', 'إ', 'آ', 'ٱ' -> 'ا'
        'ى' -> 'ي'
        'ة' -> 'ه'
        else -> c
    }

    private fun similarity(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        val dist = levenshtein(a, b)
        return 1.0 - dist.toDouble() / maxOf(a.length, b.length)
    }

    private fun levenshtein(a: String, b: String): Int {
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            System.arraycopy(cur, 0, prev, 0, cur.size)
        }
        return prev[b.length]
    }

    private companion object {
        val GT_IMG1 = """
            عند حسن ظن إخواننا بنا، ليس الأمر كذلك. الحقيقة التي أشعر
            بها من قرارة نفسي أنني حينما أسمع مثل هذا الكلام أتذكر المثل
            القديم المعروف عند الأدباء، ألا وهو (إن البغاث بأرضنا
            يستنسر)، قد يخفى على بعض الناس المقصود من هذا الكلام أو
            من هذا المثل، البغاث: هو طائر صغير لا قيمة له، فيصبح هذا
            الطير الصغير نسرا عند الناس، لجهلهم بقوة النسر وضخامته،
            فصدق هذا المثل على كثير ممن يدعون بحق وبصواب، أو بخطأ
            وباطل إلى الإسلام. لكن الله يعلم أنه خلت الأرض – الأرض
            الإسلامية كلها – إلا من أفراد قليلين جدا جدا ممن يصح أن يقال
            فيهم: فلان عالم، كما جاء في الحديث الصحيح الذي أخرجه
            الإمام البخاري في صحيحه من حديث عبد الله بن عمرو بن العاص
            رضي الله تعالى عنهما – قال: قال رسول الله: إن الله لا
            ينتزع العلم انتزاعا من صدور العلماء، ولكنه يقبض العلم بقبض
            العلماء، حتى إذا لم يبق عالما – هذا هو الشاهد – حتى إذا لم
            يبق عالما اتخذ الناس رؤوسا جهالا، فسئلوا فأفتوا بغير علم
        """.trimIndent()

        // Headline + masthead only; body columns are dense and scored qualitatively.
        val GT_IMG2 = """
            الوطن
            يومية سياسية مستقلة
            «دبي للجودة» تنظم ثلاث ورش عمل تفاعلية ضمن «جائزة الإمارات للابتكار»
            لتأهيل 118 مقيما ضمن الدورة الأولى
        """.trimIndent()

        val GT_IMG3 = """
            علم و عالم
            "صرخة" مونش القياسية: 119,92 مليون دولار
        """.trimIndent()
    }
}
