package com.skeler.scanely.core.ocr.paddle

import kotlin.math.abs

private const val V_GAP_MIN_FRAC = 0.015f
private const val H_GAP_MIN_FRAC = 0.02f
private const val V_SIDE_MIN_VEXTENT_FRAC = 0.40f
private const val V_SIDE_MIN_HEXTENT_FRAC = 0.20f
private const val V_SIDE_MIN_QUADS = 2
private const val ABS_MIN_GAP_PX = 6f
private const val TIE_FRAC = 0.02f
private const val MAX_DEPTH = 14
private const val LINE_Y_FACTOR = 0.6f

object ReadingOrder {

    fun orderedLines(quads: List<Quad>, rtl: Boolean): List<List<Int>> {
        if (quads.isEmpty()) return emptyList()
        return order(quads.indices.toList(), quads, rtl, 0)
    }

    private fun order(
        indices: List<Int>,
        quads: List<Quad>,
        rtl: Boolean,
        depth: Int
    ): List<List<Int>> {
        if (indices.size <= 2 || depth >= MAX_DEPTH) return leafLines(indices, quads)

        val minX = indices.minOf { quads[it].minX }
        val maxX = indices.maxOf { quads[it].maxX }
        val minY = indices.minOf { quads[it].minY }
        val maxY = indices.maxOf { quads[it].maxY }
        val groupWidth = maxX - minX
        val groupHeight = maxY - minY

        val vertical = widestGap(indices, quads, horizontal = false)
        val horizontal = widestGap(indices, quads, horizontal = true)
        val left = indices.filter { centerX(quads[it]) < vertical.cut }
        val right = indices.filter { centerX(quads[it]) >= vertical.cut }
        val top = indices.filter { centerY(quads[it]) < horizontal.cut }
        val bottom = indices.filter { centerY(quads[it]) >= horizontal.cut }

        val verticalValid =
            vertical.width >= maxOf(ABS_MIN_GAP_PX, V_GAP_MIN_FRAC * groupWidth) &&
                left.size >= V_SIDE_MIN_QUADS &&
                right.size >= V_SIDE_MIN_QUADS &&
                verticalExtent(left, quads) >= V_SIDE_MIN_VEXTENT_FRAC * groupHeight &&
                verticalExtent(right, quads) >= V_SIDE_MIN_VEXTENT_FRAC * groupHeight &&
                horizontalExtent(left, quads) >= V_SIDE_MIN_HEXTENT_FRAC * groupWidth &&
                horizontalExtent(right, quads) >= V_SIDE_MIN_HEXTENT_FRAC * groupWidth
        val horizontalValid =
            horizontal.width >= maxOf(ABS_MIN_GAP_PX, H_GAP_MIN_FRAC * groupHeight) &&
                top.isNotEmpty() &&
                bottom.isNotEmpty()

        if (!verticalValid && !horizontalValid) return leafLines(indices, quads)

        val takeHorizontal = when {
            !verticalValid -> true
            !horizontalValid -> false
            else -> {
                val verticalFraction = if (groupWidth > 0f) vertical.width / groupWidth else 0f
                val horizontalFraction =
                    if (groupHeight > 0f) horizontal.width / groupHeight else 0f
                // Prefer a header/body split when the normalized gaps are effectively tied.
                abs(verticalFraction - horizontalFraction) <= TIE_FRAC ||
                    horizontalFraction > verticalFraction
            }
        }

        return if (takeHorizontal) {
            order(top, quads, rtl, depth + 1) + order(bottom, quads, rtl, depth + 1)
        } else {
            val first = if (rtl) right else left
            val second = if (rtl) left else right
            order(first, quads, rtl, depth + 1) + order(second, quads, rtl, depth + 1)
        }
    }

    private fun widestGap(
        indices: List<Int>,
        quads: List<Quad>,
        horizontal: Boolean
    ): Gap {
        val intervals = indices
            .map {
                if (horizontal) {
                    Interval(quads[it].minY, quads[it].maxY)
                } else {
                    Interval(quads[it].minX, quads[it].maxX)
                }
            }
            .sortedBy { it.start }

        var mergedEnd = intervals.first().end
        var widest = 0f
        var cut = intervals.first().start
        for (interval in intervals.drop(1)) {
            if (interval.start <= mergedEnd) {
                mergedEnd = maxOf(mergedEnd, interval.end)
            } else {
                val width = interval.start - mergedEnd
                if (width > widest) {
                    widest = width
                    cut = (mergedEnd + interval.start) / 2f
                }
                mergedEnd = interval.end
            }
        }
        return Gap(widest, cut)
    }

    private fun verticalExtent(indices: List<Int>, quads: List<Quad>): Float {
        if (indices.isEmpty()) return 0f
        return indices.maxOf { quads[it].maxY } - indices.minOf { quads[it].minY }
    }

    private fun horizontalExtent(indices: List<Int>, quads: List<Quad>): Float {
        if (indices.isEmpty()) return 0f
        return indices.maxOf { quads[it].maxX } - indices.minOf { quads[it].minX }
    }

    private fun leafLines(indices: List<Int>, quads: List<Quad>): List<List<Int>> {
        val sorted = indices.sortedBy { quads[it].minY }
        val lines = mutableListOf<MutableList<Int>>()

        for (index in sorted) {
            val quad = quads[index]
            val quadCenterY = centerY(quad)
            val height = quad.maxY - quad.minY
            val line = lines.lastOrNull()?.takeIf { current ->
                val ref = quads[current.first()]
                val refCenter = centerY(ref)
                abs(quadCenterY - refCenter) <
                    maxOf(height, ref.maxY - ref.minY) * LINE_Y_FACTOR
            }
            if (line == null) lines.add(mutableListOf(index)) else line.add(index)
        }

        return lines.map { line -> line.sortedBy { quads[it].minX } }
    }

    private fun centerX(quad: Quad) = (quad.minX + quad.maxX) / 2f

    private fun centerY(quad: Quad) = (quad.minY + quad.maxY) / 2f

    private data class Interval(val start: Float, val end: Float)

    private data class Gap(val width: Float, val cut: Float)
}
