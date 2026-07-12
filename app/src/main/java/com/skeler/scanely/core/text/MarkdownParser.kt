package com.skeler.scanely.core.text

sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val lines: List<String>) : MdBlock
    data class Bullet(val text: String, val indent: Int, val checked: Boolean?) : MdBlock
    data class Numbered(val marker: String, val text: String, val indent: Int) : MdBlock
    data class Quote(val lines: List<String>) : MdBlock
    data class Code(val text: String) : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
    data object Divider : MdBlock
}

data class MdSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strike: Boolean = false,
    val underline: Boolean = false,
    val code: Boolean = false,
    val link: String? = null
)

object MarkdownParser {

    private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
    private val DIVIDER = Regex("^\\s*([-*_])\\s*\\1\\s*\\1[\\s\\-*_]*$")
    private val BULLET = Regex("^(\\s*)[-*+]\\s+(.*)$")
    private val NUMBERED = Regex("^(\\s*)(\\d+[.)])\\s+(.*)$")
    private val CHECKBOX = Regex("^\\[([ xX])]\\s+(.*)$")
    private val TABLE_DIVIDER = Regex("^\\|?[\\s:|-]*-[\\s:|-]*\\|?$")
    private val LINK = Regex("\\[([^]]*)]\\(([^)]*)\\)")

    /** Everything [Markdown.escape] puts a backslash in front of. */
    private const val ESCAPABLE = "\\`*_[]<|#>-+"

    fun looksLikeMarkdown(text: String): Boolean = text.lineSequence().any { line ->
        val trimmed = line.trim()
        (trimmed.startsWith("|") && trimmed.endsWith("|")) ||
            HEADING.matches(trimmed) ||
            trimmed.startsWith("```") ||
            trimmed.startsWith("- ") ||
            "**" in trimmed
    }

    fun parse(source: String): List<MdBlock> {
        val lines = source.replace("\r\n", "\n").split("\n")
        val blocks = mutableListOf<MdBlock>()
        val paragraph = mutableListOf<String>()
        val quote = mutableListOf<String>()

        fun flush() {
            if (paragraph.isNotEmpty()) {
                blocks += MdBlock.Paragraph(paragraph.toList())
                paragraph.clear()
            }
            if (quote.isNotEmpty()) {
                blocks += MdBlock.Quote(quote.toList())
                quote.clear()
            }
        }

        var i = 0
        while (i < lines.size) {
            val raw = lines[i]
            val line = raw.trim()

            when {
                line.startsWith("```") -> {
                    flush()
                    val body = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        body += lines[i]
                        i++
                    }
                    blocks += MdBlock.Code(body.joinToString("\n"))
                }

                line.isEmpty() -> flush()

                isTableStart(lines, i) -> {
                    flush()
                    val rows = mutableListOf<List<String>>()
                    while (i < lines.size && lines[i].trim().startsWith("|")) {
                        val cells = lines[i].trim()
                        if (!TABLE_DIVIDER.matches(cells)) rows += splitRow(cells)
                        i++
                    }
                    i--
                    if (rows.isNotEmpty()) {
                        val width = rows.maxOf { it.size }
                        val padded = rows.map { row -> row + List(width - row.size) { "" } }
                        blocks += MdBlock.Table(padded.first(), padded.drop(1))
                    }
                }

                DIVIDER.matches(line) -> {
                    flush()
                    blocks += MdBlock.Divider
                }

                HEADING.matches(line) -> {
                    flush()
                    val (hashes, text) = HEADING.find(line)!!.destructured
                    blocks += MdBlock.Heading(hashes.length, text.trim())
                }

                line.startsWith(">") -> {
                    if (paragraph.isNotEmpty()) flush()
                    quote += line.removePrefix(">").trim()
                }

                BULLET.matches(raw) -> {
                    flush()
                    val (indent, text) = BULLET.find(raw)!!.destructured
                    val box = CHECKBOX.find(text)
                    blocks += if (box != null) {
                        val (mark, rest) = box.destructured
                        MdBlock.Bullet(rest, indent.length, mark.isNotBlank())
                    } else {
                        MdBlock.Bullet(text, indent.length, null)
                    }
                }

                NUMBERED.matches(raw) -> {
                    flush()
                    val (indent, marker, text) = NUMBERED.find(raw)!!.destructured
                    blocks += MdBlock.Numbered(marker, text, indent.length)
                }

                else -> {
                    if (quote.isNotEmpty()) flush()
                    paragraph += raw.trimEnd()
                }
            }
            i++
        }
        flush()
        return blocks
    }

    /** Reading view: same content, Markdown syntax taken back out, tables aligned with spaces. */
    fun toPlainText(source: String): String = buildString {
        parse(source).forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    appendLine(plain(block.text))
                    appendLine()
                }
                is MdBlock.Paragraph -> {
                    block.lines.forEach { appendLine(plain(it)) }
                    appendLine()
                }
                is MdBlock.Bullet -> {
                    val box = when (block.checked) {
                        true -> "☑ "
                        false -> "☐ "
                        null -> ""
                    }
                    appendLine(" ".repeat(block.indent) + "• " + box + plain(block.text))
                }
                is MdBlock.Numbered ->
                    appendLine(" ".repeat(block.indent) + block.marker + " " + plain(block.text))
                is MdBlock.Quote -> {
                    block.lines.forEach { appendLine(plain(it)) }
                    appendLine()
                }
                is MdBlock.Code -> {
                    appendLine(block.text)
                    appendLine()
                }
                is MdBlock.Table -> {
                    appendLine(alignTable(block))
                }
                MdBlock.Divider -> appendLine()
            }
        }
    }.trim()

    fun parseInline(source: String): List<MdSpan> {
        val spans = mutableListOf<MdSpan>()
        val buffer = StringBuilder()
        var bold = false
        var italic = false
        var strike = false
        var underline = false
        var i = 0

        fun flush() {
            if (buffer.isNotEmpty()) {
                spans += MdSpan(buffer.toString(), bold, italic, strike, underline)
                buffer.clear()
            }
        }

        // A marker only opens when a closer exists ahead; a stray marker stays literal text.
        fun closes(marker: String, from: Int): Boolean =
            source.indexOf(marker, from, ignoreCase = true) != -1

        while (i < source.length) {
            val c = source[i]
            when {
                c == '\\' && i + 1 < source.length && source[i + 1] in ESCAPABLE -> {
                    buffer.append(source[i + 1])
                    i += 2
                }
                source.startsWith("***", i) -> {
                    if (bold || italic || (closes("**", i + 3) && closes("*", i + 3))) {
                        flush()
                        bold = !bold
                        italic = !italic
                    } else {
                        buffer.append("***")
                    }
                    i += 3
                }
                source.startsWith("**", i) -> {
                    if (bold || closes("**", i + 2)) {
                        flush()
                        bold = !bold
                    } else {
                        buffer.append("**")
                    }
                    i += 2
                }
                source.startsWith("~~", i) -> {
                    if (strike || closes("~~", i + 2)) {
                        flush()
                        strike = !strike
                    } else {
                        buffer.append("~~")
                    }
                    i += 2
                }
                source.startsWith("<u>", i, ignoreCase = true) -> {
                    if (closes("</u>", i + 3)) {
                        flush()
                        underline = true
                    } else {
                        buffer.append(source, i, i + 3)
                    }
                    i += 3
                }
                source.startsWith("</u>", i, ignoreCase = true) -> {
                    if (underline) {
                        flush()
                        underline = false
                    } else {
                        buffer.append(source, i, i + 4)
                    }
                    i += 4
                }
                c == '*' && isEmphasis(source, i) -> {
                    if (italic || closes("*", i + 1)) {
                        flush()
                        italic = !italic
                    } else {
                        buffer.append(c)
                    }
                    i += 1
                }
                c == '`' -> {
                    val end = source.indexOf('`', i + 1)
                    if (end == -1) {
                        buffer.append(c)
                        i++
                    } else {
                        flush()
                        spans += MdSpan(source.substring(i + 1, end), code = true)
                        i = end + 1
                    }
                }
                c == '[' -> {
                    val match = LINK.matchAt(source, i)
                    if (match == null) {
                        buffer.append(c)
                        i++
                    } else {
                        val (label, url) = match.destructured
                        flush()
                        spans += MdSpan(label, bold, italic, strike, underline, link = url)
                        i += match.value.length
                    }
                }
                else -> {
                    buffer.append(c)
                    i++
                }
            }
        }
        flush()
        return spans
    }

    private fun alignTable(table: MdBlock.Table): String {
        val rows = listOf(table.header) + table.rows
        val widths = table.header.indices.map { column ->
            rows.maxOf { plain(it.getOrElse(column) { "" }).length }
        }
        return rows.joinToString("\n") { row ->
            table.header.indices.joinToString("   ") { column ->
                plain(row.getOrElse(column) { "" }).padEnd(widths[column])
            }.trimEnd()
        } + "\n"
    }

    private fun plain(text: String): String = parseInline(text).joinToString("") { it.text }

    private fun isTableStart(lines: List<String>, index: Int): Boolean {
        if (!lines[index].trim().startsWith("|")) return false
        val next = lines.getOrNull(index + 1)?.trim() ?: return false
        return TABLE_DIVIDER.matches(next) || next.startsWith("|")
    }

    /** Splits on unescaped pipes only, so `\|` stays inside its cell. */
    private fun splitRow(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var endedOnPipe = false
        var i = if (line.startsWith("|")) 1 else 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '\\' && i + 1 < line.length -> {
                    current.append(c).append(line[i + 1])
                    endedOnPipe = false
                    i += 2
                }
                c == '|' -> {
                    cells += current.toString().trim()
                    current.clear()
                    endedOnPipe = true
                    i++
                }
                else -> {
                    current.append(c)
                    endedOnPipe = false
                    i++
                }
            }
        }
        if (!endedOnPipe) cells += current.toString().trim()
        return cells
    }

    /** A lone `*` is emphasis only next to a word, so `5 * 3` stays arithmetic. */
    private fun isEmphasis(source: String, index: Int): Boolean {
        val after = source.getOrNull(index + 1) ?: return false
        if (after == '*') return false
        val before = source.getOrNull(index - 1)
        return if (after.isWhitespace()) before != null && !before.isWhitespace() else true
    }
}
