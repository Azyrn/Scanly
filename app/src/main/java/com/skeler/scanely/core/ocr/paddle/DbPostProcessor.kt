package com.skeler.scanely.core.ocr.paddle

import kotlin.math.ceil

/** DBNet probability map -> text quads. Params come from PP-OCRv6 inference.yml. */
class DbPostProcessor(
    private val thresh: Float = 0.2f,
    private val boxThresh: Float = 0.45f,
    private val unclipRatio: Float = 1.4f,
    private val minSize: Float = 3f,
    private val maxCandidates: Int = 3000
) {

    fun extract(prob: FloatArray, w: Int, h: Int, scaleX: Float, scaleY: Float): List<Quad> {
        val visited = BooleanArray(w * h)
        val out = ArrayList<Quad>()
        val stack = IntArray(w * h)

        for (start in 0 until w * h) {
            if (prob[start] <= thresh || visited[start]) continue
            if (out.size >= maxCandidates) break

            var top = 0
            stack[top++] = start
            visited[start] = true
            val border = ArrayList<Float>(256)
            var pixels = 0

            while (top > 0) {
                val idx = stack[--top]
                val x = idx % w
                val y = idx / w
                pixels++

                var isBorder = false
                for (d in 0 until 4) {
                    val nx = x + NX[d]
                    val ny = y + NY[d]
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                        isBorder = true
                        continue
                    }
                    val n = ny * w + nx
                    if (prob[n] <= thresh) {
                        isBorder = true
                    } else if (!visited[n]) {
                        visited[n] = true
                        stack[top++] = n
                    }
                }
                if (isBorder) {
                    border.add(x.toFloat())
                    border.add(y.toFloat())
                }
            }

            if (pixels < 4 || border.size < 6) continue

            val rect = Geometry.minAreaRect(border.toFloatArray())
            if (rect.shortSide < minSize) continue
            if (polygonScore(prob, w, h, rect.quad) < boxThresh) continue

            val expanded = Geometry.minAreaRect(Geometry.unclip(rect.quad, unclipRatio))
            if (expanded.shortSide < minSize + 2f) continue

            out.add(Geometry.orderQuad(expanded.quad.scaled(scaleX, scaleY)))
        }
        return out
    }

    /** Mean probability inside the quad (scanline point-in-polygon). */
    private fun polygonScore(prob: FloatArray, w: Int, h: Int, q: Quad): Float {
        val x0 = q.minX.toInt().coerceIn(0, w - 1)
        val x1 = ceil(q.maxX).toInt().coerceIn(0, w - 1)
        val y0 = q.minY.toInt().coerceIn(0, h - 1)
        val y1 = ceil(q.maxY).toInt().coerceIn(0, h - 1)
        if (x1 < x0 || y1 < y0) return 0f

        var sum = 0f
        var count = 0
        for (y in y0..y1) {
            val py = y + 0.5f
            for (x in x0..x1) {
                if (!contains(q, x + 0.5f, py)) continue
                sum += prob[y * w + x]
                count++
            }
        }
        return if (count == 0) 0f else sum / count
    }

    private fun contains(q: Quad, x: Float, y: Float): Boolean {
        var inside = false
        var j = 3
        for (i in 0 until 4) {
            val yi = q.y(i)
            val yj = q.y(j)
            if ((yi > y) != (yj > y)) {
                val xi = q.x(i)
                val cross = (q.x(j) - xi) * (y - yi) / (yj - yi) + xi
                if (x < cross) inside = !inside
            }
            j = i
        }
        return inside
    }

    private companion object {
        val NX = intArrayOf(1, -1, 0, 0)
        val NY = intArrayOf(0, 0, 1, -1)
    }
}
