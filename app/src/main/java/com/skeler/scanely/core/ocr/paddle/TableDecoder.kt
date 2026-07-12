package com.skeler.scanely.core.ocr.paddle

/**
 * Decodes SLANet's per-step outputs into a grid.
 *
 * The vocabulary is HTML-ish: `<tr>`, `<td></td>`, or `<td` followed by span attributes
 * and `>`. Cell boxes are emitted on the step that opens the cell.
 */
object TableDecoder {

    private const val SPAN_PREFIX_COL = " colspan=\""
    private const val SPAN_PREFIX_ROW = " rowspan=\""

    fun decode(
        structure: FloatArray,
        boxes: FloatArray,
        steps: Int,
        classes: Int,
        vocab: List<String>,
        scale: Float
    ): TableStructure {
        val cells = mutableListOf<TableCell>()
        // Grid slots already taken by an earlier cell's row/col span.
        val occupied = mutableSetOf<Long>()
        var row = -1
        var column = 0
        var ended = false

        var step = 0
        while (step < steps && !ended) {
            val token = vocab.getOrNull(argmax(structure, step * classes, classes)) ?: break
            if (token == EOS) break

            when {
                token == "<tr>" -> {
                    row++
                    column = 0
                }

                token == "<td></td>" || token == "<td" -> {
                    if (row < 0) row = 0
                    var colSpan = 1
                    var rowSpan = 1
                    // The box belongs to the step that opened the cell, not to its attributes.
                    val cellStep = step

                    if (token == "<td") {
                        // Attributes trail the opening token until '>' closes it. The model
                        // sometimes drops the '>', so anything that isn't a span attribute
                        // ends the cell and is re-read as structure on the next pass.
                        var next = step + 1
                        while (next < steps) {
                            val attr = vocab.getOrNull(
                                argmax(structure, next * classes, classes)
                            )
                            if (attr == null || attr == EOS) {
                                ended = true
                                break
                            }
                            if (attr == ">") break
                            val col = spanOf(attr, SPAN_PREFIX_COL)
                            val rsp = spanOf(attr, SPAN_PREFIX_ROW)
                            if (col == null && rsp == null) {
                                next--
                                break
                            }
                            col?.let { colSpan = it }
                            rsp?.let { rowSpan = it }
                            next++
                        }
                        step = next
                    }

                    while (occupied.contains(slot(row, column))) column++

                    val box = FloatArray(8) { boxes[cellStep * 8 + it] }
                    val xs = (0 until 8 step 2).map { box[it] * scale }
                    val ys = (1 until 8 step 2).map { box[it] * scale }

                    cells.add(
                        TableCell(
                            row = row,
                            column = column,
                            rowSpan = rowSpan,
                            colSpan = colSpan,
                            left = xs.min(),
                            top = ys.min(),
                            right = xs.max(),
                            bottom = ys.max()
                        )
                    )

                    for (r in row until row + rowSpan) {
                        for (c in column until column + colSpan) occupied.add(slot(r, c))
                    }
                    column += colSpan
                }
            }
            step++
        }

        if (cells.isEmpty()) return TableStructure(0, 0, emptyList())

        // `<tr>` count is authoritative: a misread span (the model can emit rowspan="20" on a
        // 5-row table) would otherwise stretch the grid into dozens of empty rows.
        val rows = maxOf(row + 1, cells.maxOf { it.row + 1 })
        val clamped = cells.map { cell ->
            cell.copy(rowSpan = cell.rowSpan.coerceAtMost(rows - cell.row))
        }
        val columns = clamped.maxOf { it.column + it.colSpan }
        return TableStructure(rows, columns, clamped)
    }

    private fun spanOf(token: String, prefix: String): Int? =
        token.takeIf { it.startsWith(prefix) }
            ?.removePrefix(prefix)
            ?.removeSuffix("\"")
            ?.toIntOrNull()
            ?.takeIf { it in 2..100 }

    private fun slot(row: Int, column: Int): Long = row.toLong() shl 32 or column.toLong()

    private fun argmax(values: FloatArray, base: Int, classes: Int): Int {
        var best = 0
        var bestScore = values[base]
        for (c in 1 until classes) {
            val v = values[base + c]
            if (v > bestScore) {
                bestScore = v
                best = c
            }
        }
        return best
    }

    const val SOS = "sos"
    const val EOS = "eos"

    /** PaddleOCR wraps the file dictionary in sos/eos before decoding. */
    fun vocabulary(dictionary: List<String>): List<String> = buildList {
        add(SOS)
        addAll(dictionary)
        add(EOS)
    }
}
