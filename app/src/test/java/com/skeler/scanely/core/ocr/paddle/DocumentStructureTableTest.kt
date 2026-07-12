package com.skeler.scanely.core.ocr.paddle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The marks table from the reported scan: six columns, a header row and three data rows,
 * with the Total column empty. Exercises the SLANet-free path.
 */
class DocumentStructureTableTest {

    private fun line(text: String, left: Float, top: Float): PositionedLine {
        val right = left + 40f
        val bottom = top + 14f
        return PositionedLine(
            text = text,
            quad = Quad(floatArrayOf(left, top, right, top, right, bottom, left, bottom)),
            confidence = 0.9f
        )
    }

    private val columnX = floatArrayOf(100f, 200f, 300f, 400f, 500f, 600f)

    private fun row(y: Float, cells: List<String>): List<PositionedLine> =
        cells.mapIndexedNotNull { i, text ->
            text.takeIf { it.isNotBlank() }?.let { line(it, columnX[i], y) }
        }

    private val tableLines: List<PositionedLine> =
        row(100f, listOf("Name", "Tamil", "English", "Maths", "Science", "Total")) +
            row(140f, listOf("Moni", "80", "85", "88", "82", "")) +
            row(180f, listOf("Selvi", "90", "98", "98", "90", "")) +
            row(220f, listOf("Susmi", "85", "90", "80", "90", ""))

    private fun region(label: LayoutLabel) =
        LayoutRegion(label, 0.9f, 80f, 80f, 700f, 260f)

    @Test
    fun `table region without SLANet still renders a pipe table`() {
        val markdown = DocumentStructure.markdown(listOf(region(LayoutLabel.TABLE)), tableLines)

        val expected = """
            | Name | Tamil | English | Maths | Science | Total |
            | --- | --- | --- | --- | --- | --- |
            | Moni | 80 | 85 | 88 | 82 |   |
            | Selvi | 90 | 98 | 98 | 90 |   |
            | Susmi | 85 | 90 | 80 | 90 |   |
        """.trimIndent()

        assertEquals(expected, markdown.trim())
    }

    @Test
    fun `a paragraph region is not turned into a table`() {
        val prose = listOf(
            line("Question 1: Highlight above 90 Marks.", 100f, 100f),
            line("Question 2: Double Line Border.", 100f, 140f)
        )
        val markdown = DocumentStructure.markdown(listOf(region(LayoutLabel.TEXT)), prose)

        assertTrue(markdown, "|" !in markdown)
        assertTrue(markdown, "Question 1" in markdown)
    }

    /** The layout model routinely calls a page's first heading a HEADER; it must survive. */
    @Test
    fun `a heading misread as page furniture is kept`() {
        val markdown = DocumentStructure.markdown(
            listOf(region(LayoutLabel.HEADER)),
            listOf(line("Question 1: Highlight above 90 Marks.", 100f, 100f))
        )

        assertTrue(markdown, "Question 1" in markdown)
    }

    @Test
    fun `uncovered text is placed in reading order, not appended`() {
        val heading = line("Question 1: Highlight above 90 Marks.", 100f, 20f)
        val markdown = DocumentStructure.markdown(
            // Only the table is covered; the heading above it falls to no region at all.
            listOf(region(LayoutLabel.TABLE)),
            listOf(heading) + tableLines
        )

        assertTrue(markdown, markdown.indexOf("Question 1") < markdown.indexOf("| Name"))
    }

    @Test
    fun `heading region becomes a heading`() {
        val markdown = DocumentStructure.markdown(
            listOf(region(LayoutLabel.PARAGRAPH_TITLE)),
            listOf(line("Question 1: Highlight above 90 Marks.", 100f, 100f))
        )

        assertTrue(markdown, markdown.startsWith("## Question 1"))
    }
}
