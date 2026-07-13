package com.skeler.scanely.core.ocr.paddle

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.skeler.scanely.core.ocr.OcrResult
import com.skeler.scanely.core.ocr.PdfRendererHelper
import com.skeler.scanely.core.pdf.ScanExporter
import com.skeler.scanely.settings.data.SettingsKeys
import com.skeler.scanely.settings.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The scan from the bug report: a page of ruled marks tables. Regression cover for two
 * corruptions that reached the exported Markdown — rows reversed right-to-left, and
 * numeric cells rotated 180 degrees ("80" read back as "08").
 */
class PaddleMarksTableTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** The scan lives in the test APK's assets; the models live in the app's. */
    private val testContext = InstrumentationRegistry.getInstrumentation().context
    private lateinit var service: PaddleOcrService
    private lateinit var engine: PaddleOcrEngine

    /** Defaults from [SettingsKeys], which is what a fresh install runs with. */
    private class DefaultSettings : SettingsRepository {
        override fun getBoolean(key: SettingsKeys) = flowOf(key.default as Boolean)
        override fun getInt(key: SettingsKeys) = flowOf(key.default as Int)
        override fun getFloat(key: SettingsKeys) = flowOf(key.default as Float)
        override fun getString(key: SettingsKeys) = flowOf(key.default as String)
        override fun getStringSet(key: SettingsKeys): Flow<Set<String>> = flowOf(emptySet())
        override suspend fun setBoolean(key: SettingsKeys, value: Boolean) = Unit
        override suspend fun setInt(key: SettingsKeys, value: Int) = Unit
        override suspend fun setFloat(key: SettingsKeys, value: Float) = Unit
        override suspend fun setString(key: SettingsKeys, value: String) = Unit
        override suspend fun setStringSet(key: SettingsKeys, value: Set<String>) = Unit
        override suspend fun toggleSetting(key: SettingsKeys) = Unit
    }

    @Before
    fun setUp() {
        engine = PaddleOcrEngine(PaddleModelStore(context, OkHttpClient()))
        service = PaddleOcrService(engine, DefaultSettings(), PdfRendererHelper(context))
    }

    @After
    fun tearDown() = engine.close()

    private fun scan(): OcrResult = runBlocking {
        testContext.assets.open("marks_table.jpg").use { stream ->
            service.recognize(BitmapFactory.decodeStream(stream))
        }
    }

    /** This device drops app logcat, so the output goes to a file the host can pull. */
    private fun dump(name: String, body: String) {
        java.io.File(context.getExternalFilesDir(null), name).writeText(body)
    }

    @Test
    fun marksTableIsReadLeftToRightAndNotRotated() {
        val result = scan()
        assertTrue("recognition failed: $result", result is OcrResult.Success)
        val success = result as OcrResult.Success

        dump("scan_text.txt", success.text)
        dump("scan_markdown.md", success.markdown ?: "(none)")

        val text = success.text
        val row = text.lineSequence().firstOrNull { it.contains("Moni") }
        assertTrue("no Moni row in:\n$text", row != null)

        // The header names must land before the marks, not after them.
        assertTrue("row reads right-to-left: $row", row!!.trimStart().startsWith("Moni"))

        // 80 rotated 180 degrees reads as 08, 90 as 06, 98 as 86, 85 as S8.
        assertTrue("row lost its marks: $row", listOf("80", "85", "88", "82").all { it in row })
        assertTrue("digits rotated 180: $row", "08" !in row)
        assertTrue("digits rotated 180: $text", "S8" !in text)

        val header = text.lineSequence().firstOrNull { it.contains("Science") }
        assertTrue("no header row in:\n$text", header != null)
        assertTrue(
            "header reads right-to-left: $header",
            header!!.indexOf("Name") < header.indexOf("Science")
        )
    }

    @Test
    fun exportedMarkdownContainsAPipeTable() {
        val result = scan()
        assertTrue("recognition failed: $result", result is OcrResult.Success)
        val markdown = (result as OcrResult.Success).markdown

        assertTrue("no structured Markdown was produced", markdown != null)
        dump("export.md", markdown!!)

        val rows = markdown.lines().filter { it.trimStart().startsWith("|") }
        assertTrue("no pipe table in:\n$markdown", rows.size >= 3)
        assertTrue(
            "no header separator in:\n$markdown",
            markdown.lines().any { it.trimStart().startsWith("| ---") }
        )
        assertTrue("table lost the names in:\n$markdown", "Moni" in markdown)

        // Every question heading has to survive the layout pass, in the order it is on
        // the page: the first one was being classed as page furniture and dropped, and
        // an uncovered one was being appended below the tables it introduces.
        val headings = (1..5).map { "Question $it" }
        for (heading in headings) {
            assertTrue("markdown lost '$heading':\n$markdown", heading in markdown)
        }
        val positions = headings.map { markdown.indexOf(it) }
        assertTrue(
            "headings are out of reading order $positions in:\n$markdown",
            positions == positions.sorted()
        )
    }

    @Test
    fun exportedJsonIsValidAndOrdered() {
        val result = scan()
        assertTrue("recognition failed: $result", result is OcrResult.Success)
        val success = result as OcrResult.Success

        val json = ScanExporter.buildJson(success.text, success.blocks)
        dump("export.json", json)

        val root = JSONObject(json)
        val lines = root.getJSONArray("pages").getJSONObject(0).getJSONArray("lines")
        assertEquals("every block should be a line", success.blocks.size, lines.length())

        val texts = (0 until lines.length()).map { lines.getJSONObject(it).getString("text") }
        assertTrue("json lost the marks: $texts", listOf("80", "85", "88", "82").all { it in texts })
        assertTrue("json has 180-rotated digits: $texts", "08" !in texts && "S8" !in texts)
        assertTrue("Moni missing: $texts", "Moni" in texts)

        // Lines are emitted in reading order: the header row left-to-right, rows top-down.
        val header = listOf("Name", "Tamil", "English", "Maths", "Science", "Total")
        val headerAt = header.map { texts.indexOf(it) }
        assertTrue("header row is right-to-left: $texts", headerAt == headerAt.sorted())

        val rowsAt = listOf("Moni", "Selvi", "Susmi").map { texts.indexOf(it) }
        assertTrue("rows are bottom-up: $texts", rowsAt == rowsAt.sorted())

        for (i in 0 until lines.length()) {
            val line = lines.getJSONObject(i)
            val box = line.getJSONArray("box")
            assertEquals("box must be [l,t,r,b]", 4, box.length())
            assertTrue("box is inverted: $box", box.getInt(2) > box.getInt(0))
            assertTrue("box is inverted: $box", box.getInt(3) > box.getInt(1))
            val confidence = line.getDouble("confidence")
            assertTrue("confidence out of range: $confidence", confidence in 0.0..1.0)
        }

        // A box must place the line on the page: "Name" sits left of "Tamil" in column order.
        val boxOf = { word: String -> lines.getJSONObject(texts.indexOf(word)).getJSONArray("box") }
        assertTrue("boxes disagree with reading order", boxOf("Name").getInt(0) < boxOf("Tamil").getInt(0))
    }
}
