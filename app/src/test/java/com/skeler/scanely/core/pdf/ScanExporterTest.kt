package com.skeler.scanely.core.pdf

import com.skeler.scanely.core.ocr.TextBlockData
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanExporterTest {

    private val multiPageText =
        "Invoice 2024-01-08\nTotal: 42\n\n--- Page 2 ---\n\n- item one\nThank you"

    @Test
    fun `markdown keeps single page text as line-broken paragraphs`() {
        val markdown = ScanExporter.buildMarkdown("First line\nSecond line")

        assertEquals("First line  \nSecond line\n", markdown)
    }

    @Test
    fun `markdown turns page markers into headings`() {
        val markdown = ScanExporter.buildMarkdown(multiPageText)

        assertTrue(markdown.startsWith("## Page 1\n\n"))
        assertTrue(markdown.contains("## Page 2\n\n"))
        assertTrue(markdown.contains("Invoice 2024-01-08  \n"))
    }

    @Test
    fun `markdown escapes characters that would render as formatting`() {
        val markdown = ScanExporter.buildMarkdown("- item *not emphasis* [x]")

        assertEquals("\\- item \\*not emphasis\\* \\[x\\]\n", markdown)
    }

    @Test
    fun `json carries per-line box and confidence grouped by page`() {
        val blocks = listOf(
            TextBlockData("Invoice", 10, 20, 110, 45, confidence = 0.98f, page = 1),
            TextBlockData("Thank you", 12, 22, 130, 48, confidence = 0.91f, page = 2)
        )

        val json = JSONObject(ScanExporter.buildJson(multiPageText, blocks))
        val pages = json.getJSONArray("pages")

        assertEquals(multiPageText, json.getString("text"))
        assertEquals(2, pages.length())

        val firstLine = pages.getJSONObject(0).getJSONArray("lines").getJSONObject(0)
        assertEquals(1, pages.getJSONObject(0).getInt("page"))
        assertEquals("Invoice", firstLine.getString("text"))
        assertEquals(0.98, firstLine.getDouble("confidence"), 0.001)
        assertEquals(listOf(10, 20, 110, 45), firstLine.getJSONArray("box").let { box ->
            (0 until box.length()).map { box.getInt(it) }
        })
        assertEquals(2, pages.getJSONObject(1).getInt("page"))
    }

    @Test
    fun `json without blocks falls back to text lines`() {
        val json = JSONObject(ScanExporter.buildJson(multiPageText, emptyList()))
        val pages = json.getJSONArray("pages")

        assertEquals(2, pages.length())
        val page2Lines = pages.getJSONObject(1).getJSONArray("lines")
        assertEquals("- item one", page2Lines.getJSONObject(0).getString("text"))
        assertTrue(page2Lines.getJSONObject(0).isNull("confidence"))
    }
}
