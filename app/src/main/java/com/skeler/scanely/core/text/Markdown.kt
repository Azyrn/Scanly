package com.skeler.scanely.core.text

object Markdown {

    /** Escapes inline formatting; a leading list/heading marker is escaped too. */
    fun escape(line: String): String {
        val inline = buildString(line.length) {
            line.forEach { char ->
                if (char in "\\`*_[]<|") append('\\')
                append(char)
            }
        }
        return if (inline.firstOrNull() in setOf('#', '>', '-', '+')) "\\$inline" else inline
    }

    /** Renders a grid as a pipe table; the first row becomes the header. */
    fun table(rows: List<List<String>>): String {
        if (rows.isEmpty()) return ""
        val columns = rows.maxOf { it.size }
        if (columns == 0) return ""

        fun row(cells: List<String>): String =
            (0 until columns).joinToString(" | ", "| ", " |") { i ->
                escape(cells.getOrElse(i) { "" }.replace('\n', ' ')).ifBlank { " " }
            }

        return buildString {
            append(row(rows.first())).append('\n')
            append((0 until columns).joinToString(" | ", "| ", " |") { "---" }).append('\n')
            rows.drop(1).forEach { append(row(it)).append('\n') }
        }
    }
}
