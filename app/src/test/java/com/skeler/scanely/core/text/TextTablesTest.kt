package com.skeler.scanely.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextTablesTest {

    private val markdown = """
        # Invoice

        | Item | Cost |
        | --- | --- |
        | **Pen** | 2 |
        | Pad | 10 |
    """.trimIndent()

    @Test
    fun `markdown view exports the table without its formatting markers`() {
        val csv = TextTables.toCsv(TextTables.rows(markdown, asMarkdown = true))

        assertEquals("Item,Cost\r\nPen,2\r\nPad,10\r\n", csv)
    }

    @Test
    fun `markdown view without a table has nothing to export`() {
        assertFalse(TextTables.hasTables("# Notes\n\nJust a **paragraph**.", asMarkdown = true))
    }

    @Test
    fun `plain view exports the columns it lines up on screen`() {
        val plain = MarkdownParser.toPlainText(markdown)

        assertTrue(TextTables.hasTables(plain, asMarkdown = false))
        assertEquals(
            "Item,Cost\r\nPen,2\r\nPad,10\r\n",
            TextTables.toCsv(TextTables.rows(plain, asMarkdown = false))
        )
    }

    @Test
    fun `plain prose is not a table`() {
        val prose = "Invoice 2024-01-08\nTotal: 42\nThank you"

        assertFalse(TextTables.hasTables(prose, asMarkdown = false))
    }

    @Test
    fun `csv quotes fields that carry a comma`() {
        val csv = TextTables.toCsv(listOf(listOf("Pen, blue", "2\"")))

        assertEquals("\"Pen, blue\",\"2\"\"\"\r\n", csv)
    }

    @Test
    fun `two tables stay separate blocks`() {
        val rows = TextTables.rows(
            "| A | B |\n| --- | --- |\n| 1 | 2 |\n\ntext\n\n| C | D |\n| --- | --- |\n| 3 | 4 |",
            asMarkdown = true
        )

        assertEquals(listOf("A", "B"), rows.first())
        assertTrue(rows.contains(emptyList<String>()))
        assertEquals(listOf("3", "4"), rows.last())
    }
}
