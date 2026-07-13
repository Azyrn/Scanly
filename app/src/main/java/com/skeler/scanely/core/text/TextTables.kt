package com.skeler.scanely.core.text

/**
 * The table data a CSV export can carry, read out of whatever the preview shows: the Markdown
 * view's tables, or columns the plain view already lines up on screen. Text with no table in it
 * has nothing to export, which is what leaves CSV unavailable.
 */
object TextTables {

    /** Tab or a run of spaces — what the plain view puts between columns. */
    private val COLUMN_GAP = Regex("\t+| {2,}")

    /** Rows of every table in [text]; tables are separated by a blank row. */
    fun rows(text: String, asMarkdown: Boolean): List<List<String>> =
        if (asMarkdown) markdownRows(text) else plainRows(text)

    fun hasTables(text: String, asMarkdown: Boolean): Boolean =
        text.isNotBlank() && rows(text, asMarkdown).isNotEmpty()

    fun toCsv(rows: List<List<String>>): String = buildString {
        rows.forEach { row ->
            append(row.joinToString(",") { field(it) }).append("\r\n")
        }
    }

    private fun markdownRows(source: String): List<List<String>> =
        MarkdownParser.parse(source)
            .filterIsInstance<MdBlock.Table>()
            .flatMapIndexed { index, table ->
                val rows = (listOf(table.header) + table.rows).map { row ->
                    row.map(MarkdownParser::plain)
                }
                if (index == 0) rows else listOf(emptyList<String>()) + rows
            }

    /** Only runs of lines that split into the same columns count; prose is not a table. */
    private fun plainRows(text: String): List<List<String>> {
        val tables = mutableListOf<List<List<String>>>()
        var run = mutableListOf<List<String>>()

        fun flush() {
            if (run.size >= 2) tables += run.toList()
            run = mutableListOf()
        }

        text.lines().forEach { line ->
            val cells = columns(line)
            when {
                cells == null -> flush()
                run.isNotEmpty() && cells.size != run.first().size -> {
                    flush()
                    run += cells
                }
                else -> run += cells
            }
        }
        flush()

        return tables.flatMapIndexed { index, rows ->
            if (index == 0) rows else listOf(emptyList<String>()) + rows
        }
    }

    private fun columns(line: String): List<String>? {
        val trimmed = line.trim()
        if (trimmed.isBlank()) return null

        val cells = if ('|' in trimmed) {
            trimmed.trim('|').split('|').map(String::trim)
        } else {
            trimmed.split(COLUMN_GAP).map(String::trim)
        }
        return cells.takeIf { it.size >= 2 && it.none(String::isBlank) }
    }

    private fun field(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
