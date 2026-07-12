package com.skeler.scanely.core.ocr.paddle

/** PP-DocLayout-S class list, in model index order. */
enum class LayoutLabel {
    PARAGRAPH_TITLE, IMAGE, TEXT, NUMBER, ABSTRACT, CONTENT, FIGURE_TITLE, FORMULA,
    TABLE, TABLE_TITLE, REFERENCE, DOC_TITLE, FOOTNOTE, HEADER, ALGORITHM, FOOTER,
    SEAL, CHART_TITLE, CHART, FORMULA_NUMBER, HEADER_IMAGE, FOOTER_IMAGE, ASIDE_TEXT;

    companion object {
        fun fromIndex(index: Int): LayoutLabel? = entries.getOrNull(index)
    }
}

data class LayoutRegion(
    val label: LayoutLabel,
    val score: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun contains(q: Quad): Boolean {
        val cx = (q.minX + q.maxX) / 2f
        val cy = (q.minY + q.maxY) / 2f
        return cx in left..right && cy in top..bottom
    }
}

/** One SLANet cell: grid span plus the text box the model predicted for it. */
data class TableCell(
    val row: Int,
    val column: Int,
    val rowSpan: Int,
    val colSpan: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class TableStructure(val rows: Int, val columns: Int, val cells: List<TableCell>)
