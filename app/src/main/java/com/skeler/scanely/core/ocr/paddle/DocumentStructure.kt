package com.skeler.scanely.core.ocr.paddle

import com.skeler.scanely.core.text.Markdown
import kotlin.math.abs

data class PositionedLine(val text: String, val quad: Quad, val confidence: Float)

/**
 * Assembles the layout regions, the recognized lines and any table grids into Markdown.
 *
 * This is the part of PP-StructureV3 we can run on a phone: headings, paragraphs and
 * tables. Formulas, charts and seals have no on-device model, so they degrade to the
 * plain text the OCR read out of them.
 */
object DocumentStructure {

    fun markdown(
        regions: List<LayoutRegion>,
        lines: List<PositionedLine>,
        tableOf: (LayoutRegion) -> TableStructure? = { null }
    ): String {
        // A line the layout model never covered becomes its own text region, so it is
        // placed by reading order like any other. Appending it at the end instead moved
        // a heading below the tables it introduced.
        val covered = regions.flatMap { region -> lines.filter { region.contains(it.quad) } }
        val all = regions + textRegions(lines.filter { it !in covered.toSet() })

        val used = mutableSetOf<PositionedLine>()
        val blocks = readingOrder(all).mapNotNull { region ->
            val inside = lines.filter { it !in used && region.contains(it.quad) }
            used.addAll(inside)
            render(region, inside, tableOf)
        }

        return blocks.filter { it.isNotBlank() }.joinToString("\n\n").trimEnd() + "\n"
    }

    /** Uncovered lines, banded into regions so neighbouring lines stay one paragraph. */
    private fun textRegions(loose: List<PositionedLine>): List<LayoutRegion> =
        groupRows(loose).map { band ->
            LayoutRegion(
                label = LayoutLabel.TEXT,
                score = 0f,
                left = band.minOf { it.quad.minX },
                top = band.minOf { it.quad.minY },
                right = band.maxOf { it.quad.maxX },
                bottom = band.maxOf { it.quad.maxY }
            )
        }

    private fun render(
        region: LayoutRegion,
        lines: List<PositionedLine>,
        tableOf: (LayoutRegion) -> TableStructure?
    ): String? = when (region.label) {
        LayoutLabel.HEADER, LayoutLabel.FOOTER, LayoutLabel.ASIDE_TEXT,
        LayoutLabel.NUMBER, LayoutLabel.FORMULA_NUMBER, LayoutLabel.SEAL -> furniture(lines)

        LayoutLabel.IMAGE, LayoutLabel.HEADER_IMAGE, LayoutLabel.FOOTER_IMAGE -> "*(image)*"

        // No on-device chart model: degrade to whatever text the OCR read out of it.
        LayoutLabel.CHART -> paragraph(lines) ?: "*(chart)*"

        LayoutLabel.DOC_TITLE -> heading(1, lines)
        LayoutLabel.PARAGRAPH_TITLE -> heading(2, lines)

        LayoutLabel.FIGURE_TITLE, LayoutLabel.TABLE_TITLE, LayoutLabel.CHART_TITLE ->
            lines.joinToString(" ") { it.text.trim() }
                .takeIf { it.isNotBlank() }
                ?.let { "*${Markdown.escape(it)}*" }

        LayoutLabel.TABLE -> tableOf(region)
            ?.let { table -> tableMarkdown(table, lines) }
            ?: geometricTable(lines)
            ?: paragraph(lines)

        else -> paragraph(lines)
    }

    /**
     * A pipe table recovered from the cell boxes alone, for when SLANet is not installed.
     * It cannot see merged cells the way SLANet does, but a plain grid is far closer to the
     * page than the flat run of lines this used to degrade to.
     */
    private fun geometricTable(lines: List<PositionedLine>): String? {
        if (lines.size < MIN_TABLE_CELLS) return null

        val rows = groupRows(lines)
        val columns = columnBounds(lines)
        if (rows.size < 2 || columns.size < 2) return null

        val grid = rows.map { row ->
            val cells = MutableList(columns.size) { "" }
            for (line in row) {
                val center = (line.quad.minX + line.quad.maxX) / 2f
                val index = columns.indices.minBy { i ->
                    val (left, right) = columns[i]
                    maxOf(left - center, center - right, 0f)
                }
                cells[index] = "${cells[index]} ${line.text.trim()}".trim()
            }
            cells
        }

        if (grid.all { row -> row.count { it.isNotBlank() } < 2 }) return null
        return Markdown.table(grid).trimEnd()
    }

    /** Lines banded into table rows by vertical overlap with the band so far. */
    private fun groupRows(lines: List<PositionedLine>): List<List<PositionedLine>> {
        val rows = mutableListOf<MutableList<PositionedLine>>()
        for (line in lines.sortedBy { it.quad.minY }) {
            val center = (line.quad.minY + line.quad.maxY) / 2f
            val row = rows.lastOrNull()?.takeIf { current ->
                val ref = current.first().quad
                val refCenter = (ref.minY + ref.maxY) / 2f
                val height = maxOf(line.quad.maxY - line.quad.minY, ref.maxY - ref.minY)
                abs(center - refCenter) < height * ROW_BAND
            }
            if (row == null) rows.add(mutableListOf(line)) else row.add(line)
        }
        return rows.map { row -> row.sortedBy { it.quad.minX } }
    }

    /**
     * Column x-ranges, from every cell box projected onto the x axis and overlapping
     * projections merged. Columns in a real table are separated by whitespace, so the
     * merged runs are the columns.
     */
    private fun columnBounds(lines: List<PositionedLine>): List<Pair<Float, Float>> {
        val spans = lines.map { it.quad.minX to it.quad.maxX }.sortedBy { it.first }
        val merged = mutableListOf<Pair<Float, Float>>()
        for ((left, right) in spans) {
            val last = merged.lastOrNull()
            if (last != null && left <= last.second + COLUMN_SLACK) {
                merged[merged.size - 1] = last.first to maxOf(last.second, right)
            } else {
                merged.add(left to right)
            }
        }
        return merged
    }

    /**
     * A running head, page number or stamp is noise, and PP-StructureV3 leaves it out of the
     * document body. But the layout model also labels the first heading on a page a HEADER,
     * and dropping that loses text the user scanned. Real furniture is a short label, so only
     * short text is suppressed — anything longer is content and is kept.
     */
    private fun furniture(lines: List<PositionedLine>): String? {
        val text = lines.joinToString(" ") { it.text.trim() }.trim()
        return if (text.length <= FURNITURE_MAX_CHARS) null else paragraph(lines)
    }

    private fun heading(level: Int, lines: List<PositionedLine>): String? {
        val text = lines.joinToString(" ") { it.text.trim() }.trim()
        if (text.isBlank()) return null
        return "#".repeat(level) + " " + Markdown.escape(text)
    }

    private fun paragraph(lines: List<PositionedLine>): String? {
        if (lines.isEmpty()) return null
        // Trailing spaces keep the OCR's own line breaks once rendered.
        return lines.joinToString("  \n") { Markdown.escape(it.text.trim()) }
            .takeIf { it.isNotBlank() }
    }

    private fun tableMarkdown(
        table: TableStructure,
        lines: List<PositionedLine>
    ): String? {
        if (table.rows == 0 || table.columns == 0) return null
        val cells = table.cells.filter { it.row < table.rows && it.column < table.columns }
        if (cells.isEmpty()) return null

        val buckets = cells.associateWith { mutableListOf<PositionedLine>() }
        val remaining = lines.toMutableList()

        // Cell boxes and lines are both in page space.
        for (cell in cells) {
            val matched = remaining.filter { line ->
                val cx = (line.quad.minX + line.quad.maxX) / 2f
                val cy = (line.quad.minY + line.quad.maxY) / 2f
                cx in cell.left..cell.right && cy in cell.top..cell.bottom
            }
            remaining.removeAll(matched)
            buckets.getValue(cell).addAll(matched.sortedBy { it.quad.minX })
        }

        // SLANet's boxes are tight: a line whose centre just misses every box must not
        // vanish from the document, so it joins the nearest cell instead.
        for (line in remaining.sortedWith(compareBy({ it.quad.minY }, { it.quad.minX }))) {
            val cx = (line.quad.minX + line.quad.maxX) / 2f
            val cy = (line.quad.minY + line.quad.maxY) / 2f
            val nearest = cells.minBy { cell ->
                val dx = maxOf(cell.left - cx, cx - cell.right, 0f)
                val dy = maxOf(cell.top - cy, cy - cell.bottom, 0f)
                dx * dx + dy * dy
            }
            buckets.getValue(nearest).add(line)
        }

        val grid = MutableList(table.rows) { MutableList(table.columns) { "" } }
        for (cell in cells) {
            grid[cell.row][cell.column] = buckets.getValue(cell)
                .joinToString(" ") { it.text.trim() }
                .trim()
        }

        if (grid.all { row -> row.all { it.isBlank() } }) return null
        return Markdown.table(grid).trimEnd()
    }

    /**
     * Recursive XY-cut: split on full-width horizontal gaps first (headers, footers,
     * stacked sections), then on vertical gaps (columns, read left-to-right), and
     * recurse inside each part. A column therefore stays contiguous even when it is
     * made of several stacked regions, which greedy row-banding got wrong.
     *
     * The slack absorbs the few px layout boxes routinely overlap a neighbour by,
     * so a centred title never sorts after the indented text below it. When neither
     * axis has a gap the boxes genuinely interleave and top-then-left is all that's left.
     */
    private fun readingOrder(regions: List<LayoutRegion>): List<LayoutRegion> {
        if (regions.size <= 1) return regions

        val bands = splitByGaps(regions, { it.top }, { it.bottom })
        if (bands.size > 1) return bands.flatMap { readingOrder(it) }

        val columns = splitByGaps(regions, { it.left }, { it.right })
        if (columns.size > 1) return columns.flatMap { readingOrder(it) }

        return regions.sortedWith(compareBy({ it.top }, { it.left }))
    }

    /** Groups regions between gaps along one axis; a gap must clear [OVERLAP_SLACK]. */
    private fun splitByGaps(
        regions: List<LayoutRegion>,
        min: (LayoutRegion) -> Float,
        max: (LayoutRegion) -> Float
    ): List<List<LayoutRegion>> {
        val sorted = regions.sortedBy(min)
        val groups = mutableListOf(mutableListOf(sorted.first()))
        var reach = max(sorted.first())

        for (region in sorted.drop(1)) {
            if (min(region) >= reach - OVERLAP_SLACK) groups.add(mutableListOf())
            groups.last().add(region)
            reach = maxOf(reach, max(region))
        }
        return groups
    }

    private const val OVERLAP_SLACK = 8f
    private const val MIN_TABLE_CELLS = 4
    private const val FURNITURE_MAX_CHARS = 24
    private const val ROW_BAND = 0.6f
    private const val COLUMN_SLACK = 4f
}
