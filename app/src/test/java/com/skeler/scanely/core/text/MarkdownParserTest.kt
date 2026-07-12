package com.skeler.scanely.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    private val sample = """
        Question 1: Highlight above 90 Marks.

        | Name | Tamil | English | Maths | Science | Total |
        | :--- | :--- | :--- | :--- | :--- | :--- |
        | Moni | 80 | 85 | 88 | 82 | |
        | **Selvi** | 90 | 98 | 98 | 90 | |
    """.trimIndent()

    @Test
    fun `table parses without the divider row`() {
        val table = MarkdownParser.parse(sample).filterIsInstance<MdBlock.Table>().single()
        assertEquals(listOf("Name", "Tamil", "English", "Maths", "Science", "Total"), table.header)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("Moni", "80", "85", "88", "82", ""), table.rows[0])
        assertEquals(6, table.rows[1].size)
    }

    @Test
    fun `plain text drops pipes and emphasis markers`() {
        val plain = MarkdownParser.toPlainText(sample)
        assertFalse(plain.contains("|"))
        assertFalse(plain.contains("**"))
        assertFalse(plain.contains(":---"))
        assertTrue(plain.contains("Question 1: Highlight above 90 Marks."))
        assertTrue(plain.contains("Selvi"))
    }

    @Test
    fun `inline spans carry emphasis`() {
        val spans = MarkdownParser.parseInline("plain **bold** *it* `code` ~~out~~ <u>u</u>")
        assertTrue(spans.single { it.text == "bold" }.bold)
        assertTrue(spans.single { it.text == "it" }.italic)
        assertTrue(spans.single { it.text == "code" }.code)
        assertTrue(spans.single { it.text == "out" }.strike)
        assertTrue(spans.single { it.text == "u" }.underline)
    }

    @Test
    fun `arithmetic and headings survive`() {
        assertEquals("5 * 3 = 15", MarkdownParser.toPlainText("5 * 3 = 15"))
        val heading = MarkdownParser.parse("## Report").single()
        assertEquals(MdBlock.Heading(2, "Report"), heading)
    }

    @Test
    fun `plain OCR text is not flagged as markdown`() {
        assertFalse(MarkdownParser.looksLikeMarkdown("Hello world\nsecond line"))
        assertTrue(MarkdownParser.looksLikeMarkdown(sample))
    }

    @Test
    fun `escaped pipe stays inside its cell`() {
        val source = "| Rate | Max |\n| :--- | :--- |\n| 5\\|10 | 20 |"
        val table = MarkdownParser.parse(source).filterIsInstance<MdBlock.Table>().single()
        assertEquals(listOf("5\\|10", "20"), table.rows[0])
        assertTrue(MarkdownParser.toPlainText(source).contains("5|10"))
    }

    @Test
    fun `unmatched markers stay literal`() {
        assertEquals("Use *asterisk for footnotes", MarkdownParser.toPlainText("Use *asterisk for footnotes"))
        assertEquals("5*3=15", MarkdownParser.toPlainText("5*3=15"))
        assertEquals("a ** b", MarkdownParser.toPlainText("a ** b"))
        assertEquals("open ~~strike", MarkdownParser.toPlainText("open ~~strike"))
        assertEquals("a <u> b", MarkdownParser.toPlainText("a <u> b"))
    }

    @Test
    fun `backslash escapes render literally`() {
        assertEquals("- not a bullet", MarkdownParser.toPlainText("\\- not a bullet"))
        assertEquals("5 * 3", MarkdownParser.parseInline("5 \\* 3").joinToString("") { it.text })
        assertEquals("C:\\temp\\file", MarkdownParser.toPlainText("C:\\temp\\file"))
    }

    @Test
    fun `links still parse mid-line`() {
        val spans = MarkdownParser.parseInline("see [docs](https://x.dev) here")
        val link = spans.single { it.link != null }
        assertEquals("docs", link.text)
        assertEquals("https://x.dev", link.link)
    }
}
