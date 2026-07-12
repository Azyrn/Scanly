package com.skeler.scanely.core.ocr.paddle

import kotlin.math.abs
import kotlin.math.hypot

/** Axis-aligned or rotated quad, corners clockwise from top-left. */
data class Quad(val pts: FloatArray) {
    init { require(pts.size == 8) }

    fun x(i: Int) = pts[i * 2]
    fun y(i: Int) = pts[i * 2 + 1]

    val minX get() = (0..3).minOf { x(it) }
    val maxX get() = (0..3).maxOf { x(it) }
    val minY get() = (0..3).minOf { y(it) }
    val maxY get() = (0..3).maxOf { y(it) }

    fun scaled(sx: Float, sy: Float) = Quad(FloatArray(8) { i ->
        if (i % 2 == 0) pts[i] * sx else pts[i] * sy
    })

    override fun equals(other: Any?) = other is Quad && pts.contentEquals(other.pts)
    override fun hashCode() = pts.contentHashCode()
}

data class RotatedRect(val quad: Quad, val width: Float, val height: Float) {
    val shortSide get() = minOf(width, height)
}

object Geometry {

    /** Andrew monotone chain. Input packed as x,y pairs. */
    fun convexHull(pts: FloatArray): FloatArray {
        val n = pts.size / 2
        if (n < 3) return pts
        val order = (0 until n).sortedWith(
            compareBy({ pts[it * 2] }, { pts[it * 2 + 1] })
        )
        val hull = FloatArray((2 * n + 1) * 2)
        var k = 0

        fun cross(i: Int): Float {
            val ox = hull[(k - 2) * 2]; val oy = hull[(k - 2) * 2 + 1]
            val ax = hull[(k - 1) * 2]; val ay = hull[(k - 1) * 2 + 1]
            return (ax - ox) * (pts[i * 2 + 1] - oy) - (ay - oy) * (pts[i * 2] - ox)
        }
        fun push(i: Int, floor: Int) {
            while (k >= floor && k >= 2 && cross(i) <= 0f) k--
            hull[k * 2] = pts[i * 2]
            hull[k * 2 + 1] = pts[i * 2 + 1]
            k++
        }

        for (i in order) push(i, 2)
        val upperFloor = k + 1
        for (idx in n - 2 downTo 0) push(order[idx], upperFloor)

        val size = k - 1 // closing point repeats the first
        return if (size < 3) pts else hull.copyOf(size * 2)
    }

    /** Rotating calipers minimum-area rectangle. */
    fun minAreaRect(pts: FloatArray): RotatedRect {
        val hull = convexHull(pts)
        val n = hull.size / 2
        if (n < 3) {
            var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE
            var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
            for (i in 0 until n) {
                x0 = minOf(x0, hull[i * 2]); x1 = maxOf(x1, hull[i * 2])
                y0 = minOf(y0, hull[i * 2 + 1]); y1 = maxOf(y1, hull[i * 2 + 1])
            }
            return RotatedRect(
                Quad(floatArrayOf(x0, y0, x1, y0, x1, y1, x0, y1)),
                x1 - x0, y1 - y0
            )
        }

        var bestArea = Float.MAX_VALUE
        var bux = 1f; var buy = 0f
        var bp0 = 0f; var bp1 = 0f; var bq0 = 0f; var bq1 = 0f

        for (i in 0 until n) {
            val j = (i + 1) % n
            val ex = hull[j * 2] - hull[i * 2]
            val ey = hull[j * 2 + 1] - hull[i * 2 + 1]
            val len = hypot(ex, ey)
            if (len < 1e-6f) continue
            val ux = ex / len; val uy = ey / len
            var p0 = Float.MAX_VALUE; var p1 = -Float.MAX_VALUE
            var q0 = Float.MAX_VALUE; var q1 = -Float.MAX_VALUE
            for (h in 0 until n) {
                val hx = hull[h * 2]; val hy = hull[h * 2 + 1]
                val p = hx * ux + hy * uy
                val q = -hx * uy + hy * ux
                if (p < p0) p0 = p; if (p > p1) p1 = p
                if (q < q0) q0 = q; if (q > q1) q1 = q
            }
            val area = (p1 - p0) * (q1 - q0)
            if (area < bestArea) {
                bestArea = area; bux = ux; buy = uy
                bp0 = p0; bp1 = p1; bq0 = q0; bq1 = q1
            }
        }

        val axx = bux; val axy = buy
        val ayx = -buy; val ayy = bux
        fun corner(p: Float, q: Float) = floatArrayOf(axx * p + ayx * q, axy * p + ayy * q)
        val c0 = corner(bp0, bq0); val c1 = corner(bp1, bq0)
        val c2 = corner(bp1, bq1); val c3 = corner(bp0, bq1)
        return RotatedRect(
            Quad(floatArrayOf(c0[0], c0[1], c1[0], c1[1], c2[0], c2[1], c3[0], c3[1])),
            bp1 - bp0, bq1 - bq0
        )
    }

    /** DB unclip: offset each edge outward by area*ratio/perimeter, re-intersect. */
    fun unclip(q: Quad, ratio: Float): FloatArray {
        var area = 0f
        var perim = 0f
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            area += q.x(i) * q.y(j) - q.x(j) * q.y(i)
            perim += hypot(q.x(j) - q.x(i), q.y(j) - q.y(i))
        }
        area = abs(area) / 2f
        if (perim < 1e-6f) return q.pts
        val d = area * ratio / perim

        val ax = FloatArray(4); val ay = FloatArray(4)
        val bx = FloatArray(4); val by = FloatArray(4)
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            val ex = q.x(j) - q.x(i)
            val ey = q.y(j) - q.y(i)
            val len = maxOf(hypot(ex, ey), 1e-6f)
            val nx = ey / len * d
            val ny = -ex / len * d
            ax[i] = q.x(i) + nx; ay[i] = q.y(i) + ny
            bx[i] = q.x(j) + nx; by[i] = q.y(j) + ny
        }

        val out = FloatArray(8)
        for (i in 0 until 4) {
            val p = (i + 3) % 4
            val d1x = bx[p] - ax[p]; val d1y = by[p] - ay[p]
            val d2x = bx[i] - ax[i]; val d2y = by[i] - ay[i]
            val den = d1x * d2y - d1y * d2x
            if (abs(den) < 1e-9f) {
                out[i * 2] = ax[i]; out[i * 2 + 1] = ay[i]
            } else {
                val t = ((ax[i] - ax[p]) * d2y - (ay[i] - ay[p]) * d2x) / den
                out[i * 2] = ax[p] + d1x * t
                out[i * 2 + 1] = ay[p] + d1y * t
            }
        }
        return out
    }

    /** Corners → tl, tr, br, bl. */
    fun orderQuad(q: Quad): Quad {
        var tl = 0; var br = 0; var tr = 0; var bl = 0
        var minSum = Float.MAX_VALUE; var maxSum = -Float.MAX_VALUE
        var minDiff = Float.MAX_VALUE; var maxDiff = -Float.MAX_VALUE
        for (i in 0 until 4) {
            val s = q.x(i) + q.y(i)
            val d = q.y(i) - q.x(i)
            if (s < minSum) { minSum = s; tl = i }
            if (s > maxSum) { maxSum = s; br = i }
            if (d < minDiff) { minDiff = d; tr = i }
            if (d > maxDiff) { maxDiff = d; bl = i }
        }
        return Quad(
            floatArrayOf(
                q.x(tl), q.y(tl), q.x(tr), q.y(tr),
                q.x(br), q.y(br), q.x(bl), q.y(bl)
            )
        )
    }
}
