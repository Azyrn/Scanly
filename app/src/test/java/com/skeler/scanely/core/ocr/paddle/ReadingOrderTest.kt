package com.skeler.scanely.core.ocr.paddle

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingOrderTest {

    private fun box(x: Float, y: Float, w: Float, h: Float) =
        Quad(floatArrayOf(x, y, x + w, y, x + w, y + h, x, y + h))

    @Test
    fun singleColumnTopToBottom() {
        val quads = listOf(
            box(0f, 0f, 300f, 20f),
            box(0f, 40f, 300f, 20f),
            box(0f, 80f, 300f, 20f)
        )

        assertEquals(listOf(0, 1, 2), ReadingOrder.orderedLines(quads, false).flatten())
    }

    @Test
    fun twoColumnsLtr() {
        val quads = listOf(
            box(0f, 0f, 100f, 20f),
            box(0f, 25f, 100f, 20f),
            box(200f, 0f, 100f, 20f),
            box(200f, 25f, 100f, 20f)
        )

        assertEquals(listOf(0, 1, 2, 3), ReadingOrder.orderedLines(quads, false).flatten())
    }

    @Test
    fun twoColumnsRtl() {
        val quads = listOf(
            box(0f, 0f, 100f, 20f),
            box(0f, 25f, 100f, 20f),
            box(200f, 0f, 100f, 20f),
            box(200f, 25f, 100f, 20f)
        )

        assertEquals(listOf(2, 3, 0, 1), ReadingOrder.orderedLines(quads, true).flatten())
    }

    @Test
    fun headerThenTwoColumns() {
        val quads = listOf(
            box(0f, 0f, 300f, 15f),
            box(0f, 40f, 80f, 20f),
            box(0f, 65f, 80f, 20f),
            box(220f, 40f, 80f, 20f),
            box(220f, 65f, 80f, 20f)
        )

        assertEquals(
            listOf(0, 1, 2, 3, 4),
            ReadingOrder.orderedLines(quads, false).flatten()
        )
    }

    @Test
    fun shortTitleDoesNotSplit() {
        val quads = listOf(
            box(0f, 30f, 300f, 20f),
            box(0f, 60f, 300f, 20f),
            box(0f, 90f, 300f, 20f),
            box(110f, 0f, 80f, 15f)
        )
        val ySorted = quads.indices.sortedBy { quads[it].minY }

        assertEquals(ySorted, ReadingOrder.orderedLines(quads, false).flatten())
    }

    @Test
    fun withinLineLeftToRight() {
        val quads = listOf(
            box(200f, 0f, 80f, 20f),
            box(0f, 0f, 80f, 20f)
        )

        assertEquals(listOf(1, 0), ReadingOrder.orderedLines(quads, false).flatten())
    }

    @Test
    fun emptyInput() {
        assertEquals(emptyList<List<Int>>(), ReadingOrder.orderedLines(emptyList(), false))
    }
}
