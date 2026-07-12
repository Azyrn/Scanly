package com.skeler.scanely.core.ocr.paddle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression tests for the defects found in the structure-pipeline review. */
class StructureReviewFindingsTest {

    private fun line(text: String, left: Float, top: Float): PositionedLine {
        val right = left + 60f
        val bottom = top + 10f
        return PositionedLine(
            text = text,
            quad = Quad(floatArrayOf(left, top, right, top, right, bottom, left, bottom)),
            confidence = 0.9f
        )
    }

    private fun region(
        label: LayoutLabel,
        top: Float,
        bottom: Float,
        left: Float = 0f,
        right: Float = 500f
    ) = LayoutRegion(label, 0.9f, left, top, right, bottom)

    @Test
    fun `a two-column page with stacked paragraphs keeps each column contiguous`() {
        // Left column holds two stacked paragraphs; the right column is one tall paragraph.
        val markdown = DocumentStructure.markdown(
            regions = listOf(
                region(LayoutLabel.TEXT, 0f, 300f, left = 0f, right = 200f),
                region(LayoutLabel.TEXT, 310f, 600f, left = 0f, right = 200f),
                region(LayoutLabel.TEXT, 0f, 600f, left = 300f, right = 500f)
            ),
            lines = listOf(
                line("left first", 10f, 20f),
                line("left second", 10f, 320f),
                line("right column", 310f, 20f)
            )
        )

        val leftFirst = markdown.indexOf("left first")
        val leftSecond = markdown.indexOf("left second")
        val right = markdown.indexOf("right column")
        assertTrue(
            "left column must stay contiguous, got:\n$markdown",
            leftFirst < leftSecond && leftSecond < right
        )
    }

    private val dictionary = listOf(
        "<thead>", "</thead>", "<tbody>", "</tbody>", "<tr>", "</tr>", "<td", ">", "</td>",
        " colspan=\"2\"", " colspan=\"3\"", " rowspan=\"2\"", " rowspan=\"3\"", "<td></td>"
    )
    private val vocab = TableDecoder.vocabulary(dictionary)

    private fun decode(tokens: List<String>): TableStructure {
        val classes = vocab.size
        val steps = tokens.size
        val structure = FloatArray(steps * classes)
        tokens.forEachIndexed { step, token ->
            structure[step * classes + vocab.indexOf(token)] = 1f
        }
        return TableDecoder.decode(structure, FloatArray(steps * 8), steps, classes, vocab, 1f)
    }

    @Test
    fun `a td missing its closing gt does not swallow the next row`() {
        // The model emits "<td" + attribute but never ">", then carries on with a new row.
        val table = decode(
            listOf("<tr>", "<td", " colspan=\"2\"", "<tr>", "<td></td>", "</tr>", "eos")
        )

        assertEquals("the second row's cell must survive", 2, table.cells.size)
        assertEquals(2, table.rows)
    }

    @Test
    fun `eos inside an attribute scan ends the whole decode`() {
        // Everything after eos is padding the model never meant to emit.
        val table = decode(
            listOf("<tr>", "<td", "eos", "<tr>", "<td></td>")
        )

        assertEquals("decode must stop at eos", 1, table.cells.size)
        assertEquals(1, table.rows)
    }

    @Test
    fun `a table line outside every cell box still reaches the output`() {
        // Cell boxes arrive in page space, already translated by the crop origin.
        val table = TableStructure(
            rows = 1,
            columns = 2,
            cells = listOf(
                TableCell(0, 0, 1, 1, 0f, 100f, 40f, 120f),
                TableCell(0, 1, 1, 1, 50f, 100f, 90f, 120f)
            )
        )
        // "stray" sits inside the table region but below both cell boxes.
        val markdown = DocumentStructure.markdown(
            regions = listOf(region(LayoutLabel.TABLE, 100f, 200f, left = 0f, right = 100f)),
            lines = listOf(
                line("Item", 5f, 102f),
                line("Qty", 55f, 102f),
                line("stray", 5f, 160f)
            ),
            tableOf = { table }
        )

        assertTrue("text inside the table region must not vanish, got:\n$markdown",
            markdown.contains("stray"))
    }
}
