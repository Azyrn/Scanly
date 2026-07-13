package com.skeler.scanely.core.text

import com.skeler.scanely.core.pdf.ScanExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The offline engine writes its Markdown through [Markdown], which escapes every cell and line.
 * The exports have to read that back out as the preview renders it — not with the backslashes.
 */
class OfflineMarkdownExportTest {

    /** Shaped like [com.skeler.scanely.core.ocr.paddle.DocumentStructure] emits a page. */
    private val offlineMarkdown = buildString {
        append("## ").append(Markdown.escape("Invoice #42 (final)")).append("\n\n")
        append(Markdown.escape("Issued 2026-07-13")).append("  \n")
        append(Markdown.escape("- not a bullet, an OCR line")).append("\n\n")
        append(
            Markdown.table(
                listOf(
                    listOf("Item", "Cost"),
                    listOf("Pen * blue", "2"),
                    listOf("Pad | wide", "10")
                )
            )
        )
    }

    @Test
    fun `offline table exports to csv as the cells the preview shows`() {
        val csv = TextTables.toCsv(TextTables.rows(offlineMarkdown, asMarkdown = true))

        // The escaped pipe comes back as a pipe inside its own cell; only commas force quoting.
        assertEquals("Item,Cost\r\nPen * blue,2\r\nPad | wide,10\r\n", csv)
    }

    @Test
    fun `offline markdown exports to word without its escapes`() {
        val document = ScanExporter.wordMarkdownDocument(MarkdownParser.parse(offlineMarkdown))

        assertTrue(document.contains(">Invoice #42 (final)</w:t>"))
        assertTrue(document.contains(">Pen * blue</w:t>"))
        assertTrue(document.contains("<w:tbl>"))
        // An escaped OCR line stays a line, not a Word bullet.
        assertTrue(document.contains(">- not a bullet, an OCR line</w:t>"))
        assertFalse(document.contains("\\"))
    }

    @Test
    fun `the plain view of an offline scan is not mistaken for a table`() {
        val plain = "Invoice #42\nIssued 2026-07-13\nThank you"

        assertFalse(TextTables.hasTables(plain, asMarkdown = false))
    }
}
