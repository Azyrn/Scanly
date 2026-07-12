package com.skeler.scanely.core.ocr.paddle

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exercises the real PP-DocLayout-S and SLANet graphs. These catch preprocessing drift
 * (normalisation, padding, scale factors) that a pure decoder test cannot.
 */
class PaddleStructureTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var store: PaddleModelStore
    private lateinit var engine: PaddleOcrEngine

    private val tableLeft = 80
    private val tableTop = 620
    private val columnWidth = 220
    private val rowHeight = 45
    private val columns = 4
    private val rows = 5

    @Before
    fun setUp() {
        store = PaddleModelStore(context, OkHttpClient())
        engine = PaddleOcrEngine(store)
    }

    @After
    fun tearDown() {
        engine.close()
    }

    /** Title, body paragraphs and a ruled 4x5 table — the layout the model should recover. */
    private fun document(): Bitmap {
        val bitmap = Bitmap.createBitmap(1000, 1400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val text = Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
        }

        text.textSize = 38f
        canvas.drawText("Quarterly Financial Report", 260f, 90f, text)

        text.textSize = 28f
        canvas.drawText("1. Summary", 80f, 170f, text)

        text.textSize = 19f
        repeat(8) { i ->
            canvas.drawText(
                "Revenue grew steadily across all regions during the quarter, with strong",
                80f, 220f + i * 30f, text
            )
        }

        val rule = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val headers = listOf("Item", "Qty", "Price", "Total")
        for (r in 0 until rows) {
            for (c in 0 until columns) {
                val left = (tableLeft + c * columnWidth).toFloat()
                val top = (tableTop + r * rowHeight).toFloat()
                canvas.drawRect(left, top, left + columnWidth, top + rowHeight, rule)
                text.textSize = 18f
                val label = if (r == 0) headers[c] else "${(r * c + 3) * 7}"
                canvas.drawText(label, left + 12f, top + 30f, text)
            }
        }

        text.textSize = 19f
        repeat(6) { i ->
            canvas.drawText(
                "Costs remained flat while margins improved year over year.",
                80f, 900f + i * 30f, text
            )
        }
        return bitmap
    }

    @Test
    fun layoutModelFindsTitlesTextAndTheTable() {
        val page = document()
        val regions = engine.detectLayout(page)

        assertTrue("expected regions, got none", regions.isNotEmpty())
        assertTrue(
            "expected a title region, got ${regions.map { it.label }}",
            regions.any {
                it.label == LayoutLabel.DOC_TITLE || it.label == LayoutLabel.PARAGRAPH_TITLE
            }
        )
        assertTrue(
            "expected a text region, got ${regions.map { it.label }}",
            regions.any { it.label == LayoutLabel.TEXT }
        )

        val table = regions.filter { it.label == LayoutLabel.TABLE }
        assertEquals("expected exactly one table region", 1, table.size)

        // The detected box should sit on the ruled grid, give or take a small margin.
        val found = table.single()
        val expectedRight = tableLeft + columns * columnWidth
        val expectedBottom = tableTop + rows * rowHeight
        assertTrue("table left ${found.left}", found.left in (tableLeft - 40f)..(tableLeft + 40f))
        assertTrue("table top ${found.top}", found.top in (tableTop - 40f)..(tableTop + 40f))
        assertTrue(
            "table right ${found.right}",
            found.right in (expectedRight - 40f)..(expectedRight + 40f)
        )
        assertTrue(
            "table bottom ${found.bottom}",
            found.bottom in (expectedBottom - 40f)..(expectedBottom + 40f)
        )
        page.recycle()
    }

    @Test
    fun tableModelRecoversTheGrid() {
        runBlocking { store.downloadTable() }
        org.junit.Assume.assumeTrue("SLANet not installed", store.hasTable())

        val page = document()
        val crop = Bitmap.createBitmap(
            page, tableLeft, tableTop, columns * columnWidth, rows * rowHeight
        )
        val structure = engine.recognizeTable(crop)

        assertEquals("rows", rows, structure.rows)
        assertEquals("columns", columns, structure.columns)
        assertEquals("cells", rows * columns, structure.cells.size)

        // Boxes are normalised against the padded square: getting that wrong scales them by
        // crop width / height (~4x here), so a row-sized tolerance still catches it. The model
        // predicts the cell boundary, which can sit a few px outside the ruled line.
        val slack = rowHeight.toFloat()
        val cell = structure.cells.first { it.row == 1 && it.column == 0 }
        assertTrue(
            "row 1 cell top ${cell.top} should sit near y=$rowHeight",
            cell.top in (rowHeight - slack)..(rowHeight + slack)
        )
        assertTrue(
            "row 1 cell bottom ${cell.bottom} should sit near y=${2 * rowHeight}",
            cell.bottom in (2f * rowHeight - slack)..(2f * rowHeight + slack)
        )
        assertTrue("cell left ${cell.left}", cell.left in 0f..columnWidth.toFloat())

        // Last row's box must still be inside the crop — the sharpest check on the scale.
        val last = structure.cells.first { it.row == rows - 1 && it.column == columns - 1 }
        assertTrue(
            "last cell bottom ${last.bottom} vs crop height ${rows * rowHeight}",
            last.bottom > (rows - 1) * rowHeight - slack &&
                last.bottom <= rows * rowHeight + slack
        )

        crop.recycle()
        page.recycle()
    }
}
