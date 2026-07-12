package com.skeler.scanely.core.ocr.paddle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentStructureTest {

    private fun line(text: String, left: Float, top: Float): PositionedLine {
        val right = left + 60f
        val bottom = top + 10f
        return PositionedLine(
            text = text,
            quad = Quad(floatArrayOf(left, top, right, top, right, bottom, left, bottom)),
            confidence = 0.9f
        )
    }

    private fun region(label: LayoutLabel, top: Float, bottom: Float, left: Float = 0f, right: Float = 500f) =
        LayoutRegion(label, 0.9f, left, top, right, bottom)

    @Test
    fun `titles become headings and text becomes paragraphs`() {
        val markdown = DocumentStructure.markdown(
            regions = listOf(
                region(LayoutLabel.DOC_TITLE, 0f, 20f),
                region(LayoutLabel.PARAGRAPH_TITLE, 30f, 50f),
                region(LayoutLabel.TEXT, 60f, 100f)
            ),
            lines = listOf(
                line("Quarterly Report", 10f, 5f),
                line("Summary", 10f, 35f),
                line("Revenue grew", 10f, 65f),
                line("across regions", 10f, 80f)
            )
        )

        // Two trailing spaces are a Markdown hard break, keeping the OCR's own line split.
        assertEquals(
            "# Quarterly Report\n\n## Summary\n\nRevenue grew  \nacross regions\n",
            markdown
        )
    }

    @Test
    fun `headers and footers stay out of the document body`() {
        val markdown = DocumentStructure.markdown(
            regions = listOf(
                region(LayoutLabel.HEADER, 0f, 20f),
                region(LayoutLabel.TEXT, 30f, 60f),
                region(LayoutLabel.FOOTER, 70f, 90f)
            ),
            lines = listOf(
                line("confidential", 10f, 5f),
                line("Body text", 10f, 35f),
                line("page 4 of 9", 10f, 75f)
            )
        )

        assertTrue(markdown.contains("Body text"))
        assertFalse(markdown.contains("confidential"))
        assertFalse(markdown.contains("page 4 of 9"))
    }

    @Test
    fun `a table region renders as a pipe table filled from the OCR lines`() {
        // Cell boxes arrive in page space, already translated by the crop origin.
        val table = TableStructure(
            rows = 2,
            columns = 2,
            cells = listOf(
                TableCell(0, 0, 1, 1, 0f, 100f, 40f, 120f),
                TableCell(0, 1, 1, 1, 50f, 100f, 90f, 120f),
                TableCell(1, 0, 1, 1, 0f, 125f, 40f, 145f),
                TableCell(1, 1, 1, 1, 50f, 125f, 90f, 145f)
            )
        )
        val markdown = DocumentStructure.markdown(
            regions = listOf(region(LayoutLabel.TABLE, 100f, 150f, left = 0f, right = 100f)),
            lines = listOf(
                line("Item", 5f, 102f),
                line("Qty", 55f, 102f),
                line("Bolt", 5f, 127f),
                line("2", 55f, 127f)
            ),
            tableOf = { table }
        )

        assertEquals(
            """
            | Item | Qty |
            | --- | --- |
            | Bolt | 2 |
            """.trimIndent() + "\n",
            markdown
        )
    }

    @Test
    fun `a table falls back to plain lines without the table model`() {
        val markdown = DocumentStructure.markdown(
            regions = listOf(region(LayoutLabel.TABLE, 0f, 50f)),
            lines = listOf(line("Item Qty", 5f, 10f)),
            tableOf = { null }
        )

        assertEquals("Item Qty\n", markdown)
    }

    @Test
    fun `a title keeps its place when its box overlaps the text below it`() {
        // Layout boxes routinely overlap by a few px; that must not reorder the page.
        val markdown = DocumentStructure.markdown(
            regions = listOf(
                region(LayoutLabel.DOC_TITLE, 0f, 60f, left = 200f, right = 400f),
                region(LayoutLabel.TEXT, 45f, 200f, left = 80f, right = 400f)
            ),
            lines = listOf(line("The Title", 210f, 10f), line("Body text", 90f, 100f))
        )

        assertTrue(
            "title must come first, got:\n$markdown",
            markdown.indexOf("# The Title") < markdown.indexOf("Body text")
        )
    }

    @Test
    fun `two columns read left then right`() {
        val markdown = DocumentStructure.markdown(
            regions = listOf(
                region(LayoutLabel.TEXT, 0f, 200f, left = 300f, right = 500f),
                region(LayoutLabel.TEXT, 0f, 200f, left = 0f, right = 200f)
            ),
            lines = listOf(line("right column", 310f, 20f), line("left column", 10f, 20f))
        )

        assertTrue(
            "left column must come first, got:\n$markdown",
            markdown.indexOf("left column") < markdown.indexOf("right column")
        )
    }

    @Test
    fun `an abstract is body text, not a heading`() {
        val markdown = DocumentStructure.markdown(
            regions = listOf(region(LayoutLabel.ABSTRACT, 0f, 60f)),
            lines = listOf(line("We present a method for", 10f, 10f))
        )

        assertEquals("We present a method for\n", markdown)
    }

    @Test
    fun `lines outside every region still reach the output`() {
        val markdown = DocumentStructure.markdown(
            regions = listOf(region(LayoutLabel.TEXT, 0f, 30f)),
            lines = listOf(line("inside", 10f, 10f), line("orphan", 10f, 200f))
        )

        assertTrue(markdown.contains("inside"))
        assertTrue(markdown.contains("orphan"))
    }
}
