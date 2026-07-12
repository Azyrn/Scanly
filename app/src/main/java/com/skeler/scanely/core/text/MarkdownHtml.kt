package com.skeler.scanely.core.text

/** Markdown -> printable HTML: what the print dialog (and its Save-as-PDF) lays out. */
object MarkdownHtml {

    /** Prints the text exactly as shown in the plain view; no Markdown interpretation. */
    fun renderPlain(source: String): String =
        page("<div class=\"plain\">${escape(source)}</div>")

    fun render(source: String): String {
        val body = MarkdownParser.parse(source).joinToString("\n") { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val level = block.level.coerceIn(1, 6)
                    "<h$level>${inline(block.text)}</h$level>"
                }
                is MdBlock.Paragraph ->
                    "<p>" + block.lines.joinToString("<br/>") { inline(it) } + "</p>"
                is MdBlock.Bullet -> {
                    val box = when (block.checked) {
                        true -> "&#9745; "
                        false -> "&#9744; "
                        null -> ""
                    }
                    "<ul style=\"margin-left:${block.indent * 8}px\"><li>$box${inline(block.text)}</li></ul>"
                }
                is MdBlock.Numbered ->
                    "<div class=\"num\" style=\"margin-left:${block.indent * 8 + 16}px\">" +
                        "<b>${escape(block.marker)}</b> ${inline(block.text)}</div>"
                is MdBlock.Quote ->
                    "<blockquote>" + block.lines.joinToString("<br/>") { inline(it) } + "</blockquote>"
                is MdBlock.Code -> "<pre>${escape(block.text)}</pre>"
                is MdBlock.Table -> table(block)
                MdBlock.Divider -> "<hr/>"
            }
        }
        return page(body)
    }

    private fun page(body: String): String = """<!DOCTYPE html>
<html><head><meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<style>
  @page { margin: 14mm; }
  body { font-family: -apple-system, Roboto, sans-serif; font-size: 12pt; line-height: 1.5; color: #111; }
  h1, h2, h3, h4, h5, h6 { margin: 14px 0 6px; }
  p { margin: 6px 0; }
  .num { margin: 3px 0; }
  .plain { white-space: pre-wrap; }
  ul { margin: 3px 0; padding-left: 18px; }
  blockquote { margin: 8px 0; padding-left: 10px; border-left: 3px solid #888; color: #444; font-style: italic; }
  pre { background: #f2f2f2; padding: 10px; border-radius: 6px; white-space: pre-wrap; font-size: 10pt; }
  code { background: #f2f2f2; padding: 1px 3px; border-radius: 3px; }
  table { border-collapse: collapse; width: 100%; margin: 10px 0; page-break-inside: auto; }
  th, td { border: 1px solid #555; padding: 6px 8px; text-align: left; vertical-align: top; }
  th { background: #eee; }
  tr { page-break-inside: avoid; }
  hr { border: none; border-top: 1px solid #999; margin: 12px 0; }
</style></head>
<body>
$body
</body></html>"""

    private fun table(table: MdBlock.Table): String = buildString {
        append("<table><thead><tr>")
        table.header.forEach { append("<th>${inline(it)}</th>") }
        append("</tr></thead><tbody>")
        table.rows.forEach { row ->
            append("<tr>")
            table.header.indices.forEach { column ->
                append("<td>${inline(row.getOrElse(column) { "" })}</td>")
            }
            append("</tr>")
        }
        append("</tbody></table>")
    }

    private fun inline(text: String): String =
        MarkdownParser.parseInline(text).joinToString("") { span ->
            var html = escape(span.text)
            if (span.code) html = "<code>$html</code>"
            if (span.bold) html = "<b>$html</b>"
            if (span.italic) html = "<i>$html</i>"
            if (span.strike) html = "<s>$html</s>"
            if (span.underline) html = "<u>$html</u>"
            if (span.link != null) html = "<a href=\"${escape(span.link)}\">$html</a>"
            html
        }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
