package com.skeler.scanely.core.ocr.paddle

import org.junit.Assert.assertEquals
import org.junit.Test

class TableDecoderTest {

    // The real dictionary, in the order PaddleOCR builds it after merge_no_span_structure.
    private val dictionary = listOf(
        "<thead>", "</thead>", "<tbody>", "</tbody>", "<tr>", "</tr>", "<td", ">", "</td>",
        " colspan=\"2\"", " colspan=\"3\"", " rowspan=\"2\"", " rowspan=\"3\"", "<td></td>"
    )
    private val vocab = TableDecoder.vocabulary(dictionary)

    /** One-hot logits for a token sequence, plus a box per step. */
    private fun decode(tokens: List<String>, boxes: List<FloatArray> = emptyList()): TableStructure {
        val classes = vocab.size
        val steps = tokens.size
        val structure = FloatArray(steps * classes)
        tokens.forEachIndexed { step, token ->
            structure[step * classes + vocab.indexOf(token)] = 1f
        }
        val boxData = FloatArray(steps * 8)
        boxes.forEachIndexed { step, box -> box.copyInto(boxData, step * 8) }
        return TableDecoder.decode(structure, boxData, steps, classes, vocab, scale = 1f)
    }

    @Test
    fun `decodes a plain grid`() {
        val table = decode(
            listOf(
                "<thead>", "<tr>", "<td></td>", "<td></td>", "</tr>", "</thead>",
                "<tbody>", "<tr>", "<td></td>", "<td></td>", "</tr>", "</tbody>", "eos"
            )
        )

        assertEquals(2, table.rows)
        assertEquals(2, table.columns)
        assertEquals(4, table.cells.size)
        assertEquals(listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1), table.cells.map { it.row to it.column })
    }

    @Test
    fun `a column span shifts the following cell`() {
        val table = decode(
            listOf(
                "<tr>", "<td", " colspan=\"2\"", ">", "</td>", "<td></td>", "</tr>", "eos"
            )
        )

        assertEquals(1, table.rows)
        assertEquals(3, table.columns)
        assertEquals(2, table.cells[0].colSpan)
        assertEquals(0, table.cells[0].column)
        assertEquals(2, table.cells[1].column)
    }

    @Test
    fun `a row span reserves the slot on the next row`() {
        val table = decode(
            listOf(
                "<tr>", "<td", " rowspan=\"2\"", ">", "</td>", "<td></td>", "</tr>",
                "<tr>", "<td></td>", "</tr>", "eos"
            )
        )

        assertEquals(2, table.rows)
        assertEquals(2, table.columns)
        // The second row's only cell must skip column 0, which the span still occupies.
        val second = table.cells.single { it.row == 1 }
        assertEquals(1, second.column)
    }

    @Test
    fun `cell box comes from the opening token, not its span attributes`() {
        val box = floatArrayOf(10f, 20f, 30f, 20f, 30f, 40f, 10f, 40f)
        val table = decode(
            tokens = listOf("<tr>", "<td", " colspan=\"2\"", ">", "</td>", "</tr>", "eos"),
            // step 1 is "<td"; the attribute steps carry junk boxes.
            boxes = listOf(
                FloatArray(8),
                box,
                floatArrayOf(99f, 99f, 99f, 99f, 99f, 99f, 99f, 99f),
                floatArrayOf(77f, 77f, 77f, 77f, 77f, 77f, 77f, 77f)
            )
        )

        val cell = table.cells.single()
        assertEquals(10f, cell.left, 0.01f)
        assertEquals(20f, cell.top, 0.01f)
        assertEquals(30f, cell.right, 0.01f)
        assertEquals(40f, cell.bottom, 0.01f)
    }

    @Test
    fun `an oversized row span cannot stretch the grid past the row count`() {
        val table = decode(
            listOf(
                "<tr>", "<td", " rowspan=\"3\"", ">", "</td>", "</tr>",
                "<tr>", "<td></td>", "</tr>", "eos"
            )
        )

        assertEquals("only two <tr> tokens were emitted", 2, table.rows)
        assertEquals(2, table.cells.first().rowSpan)
    }

    @Test
    fun `decoding stops at eos`() {
        val table = decode(
            listOf("<tr>", "<td></td>", "eos", "<tr>", "<td></td>", "<td></td>")
        )

        assertEquals(1, table.rows)
        assertEquals(1, table.cells.size)
    }
}
