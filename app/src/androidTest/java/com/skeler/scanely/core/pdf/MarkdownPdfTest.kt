package com.skeler.scanely.core.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkdownPdfTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val markdown = """
        # Quarterly Report

        Revenue is **up**, costs are *flat*.

        | Region | Revenue | Growth |
        | --- | --- | --- |
        | North | 1,200 | 12% |
        | South | 900 | 4% |

        - [x] Ship the export
        - [ ] Write it up

        > Numbers are provisional.

        ```
        total = revenue - costs
        ```
    """.trimIndent()

    @Test
    fun markdownPdfDrawsTheRenderedPage() {
        val pixels = render(MarkdownPdf.render(markdown))

        assertEquals(1, pixels.size)
        // A rendered page is neither blank nor a wall of text: it has the table's rules on it.
        assertTrue(inked(pixels.first()) > 0.01f)
        assertTrue(horizontalRules(pixels.first()) >= 3)
    }

    @Test
    fun longMarkdownFlowsOntoMoreThanOnePage() {
        val rows = (1..80).joinToString("\n") { "| Row $it | value $it |" }
        val pixels = render(MarkdownPdf.render("| A | B |\n| --- | --- |\n$rows"))

        assertTrue(pixels.size > 1)
        // The header is carried onto every page of the grid.
        pixels.forEach { assertTrue(horizontalRules(it) >= 2) }
    }

    @Test
    fun emptyMarkdownStillProducesAPage() {
        assertEquals(1, render(MarkdownPdf.render("")).size)
    }

    /** Rasterizes every page so the assertions look at what a PDF viewer would show. */
    private fun render(document: android.graphics.pdf.PdfDocument): List<Bitmap> {
        val file = File.createTempFile("markdown", ".pdf", context.cacheDir)
        try {
            file.outputStream().use { document.writeTo(it) }
            document.close()

            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    return (0 until renderer.pageCount).map { index ->
                        renderer.openPage(index).use { page ->
                            Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                                .also { page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                        }
                    }
                }
            }
        } finally {
            file.delete()
        }
    }

    private fun inked(page: Bitmap): Float {
        var dark = 0
        for (y in 0 until page.height) {
            for (x in 0 until page.width) {
                if (isDark(page.getPixel(x, y))) dark++
            }
        }
        return dark.toFloat() / (page.width * page.height)
    }

    /** Rows that are mostly dark across the content width — a table rule or a divider. */
    private fun horizontalRules(page: Bitmap): Int {
        var rules = 0
        for (y in 0 until page.height) {
            var dark = 0
            for (x in 0 until page.width) {
                if (isDark(page.getPixel(x, y))) dark++
            }
            if (dark > page.width * 0.6f) rules++
        }
        return rules
    }

    private fun isDark(pixel: Int): Boolean =
        Color.red(pixel) < 200 && Color.green(pixel) < 200 && Color.blue(pixel) < 200
}
