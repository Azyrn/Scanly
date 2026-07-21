package com.skeler.scanely.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilenameSuggesterTest {

    @Test
    fun `uses the markdown heading over the body`() {
        val text = """
            Some intro line that came before nothing important
            # Quarterly Revenue Report
            The board reviewed the numbers on Tuesday.
        """.trimIndent()

        assertEquals("Quarterly Revenue Report", FilenameSuggester.suggest(text))
    }

    @Test
    fun `drops stop words and keeps at most four words`() {
        val text = "The Report of the Annual General Meeting for all of our members"

        assertEquals("Report Annual General Meeting", FilenameSuggester.suggest(text))
    }

    @Test
    fun `tops up a thin title from the following lines`() {
        val text = """
            Invoice
            Acme Industries
            Total due: 1200
        """.trimIndent()

        assertEquals("Invoice Acme Industries", FilenameSuggester.suggest(text))
    }

    @Test
    fun `skips ocr page markers`() {
        val text = """
            --- Page 1 ---
            Medical Discharge Summary
        """.trimIndent()

        assertEquals("Medical Discharge Summary", FilenameSuggester.suggest(text))
    }

    @Test
    fun `strips list markers and emphasis`() {
        assertEquals("Shopping List Groceries", FilenameSuggester.suggest("- **Shopping list** groceries"))
    }

    @Test
    fun `falls back when there is nothing meaningful`() {
        assertEquals(FilenameSuggester.FALLBACK, FilenameSuggester.suggest("   \n *** \n --- \n"))
        assertEquals(FilenameSuggester.FALLBACK, FilenameSuggester.suggest(""))
    }

    @Test
    fun `keeps non latin scripts`() {
        assertEquals(
            "تقرير الاداء السنوي الشركة",
            FilenameSuggester.suggest("تقرير الاداء السنوي في الشركة")
        )
    }

    @Test
    fun `never emits characters that are invalid in a filename`() {
        val suggested = FilenameSuggester.suggest("Report: Q1/Q2 <draft> \"final\" | v2")

        assertTrue(suggested.none { it in "\\/:*?\"<>|" })
    }

    @Test
    fun `sanitize cleans a user edited name`() {
        assertEquals("My Report v2", FilenameSuggester.sanitize("  My/Report:  v2.  "))
    }

    @Test
    fun `sanitize drops a duplicate extension`() {
        assertEquals("Invoice", FilenameSuggester.sanitize("Invoice.PDF", "pdf"))
        assertEquals("Invoice.pdf", FilenameSuggester.sanitize("Invoice.pdf", "docx"))
    }

    @Test
    fun `sanitize falls back on an empty name`() {
        assertEquals(FilenameSuggester.FALLBACK, FilenameSuggester.sanitize("///"))
    }

    @Test
    fun `caps the length of very long titles`() {
        val suggested = FilenameSuggester.suggest("Extraordinarily Comprehensive Documentation Compendium")

        assertTrue(suggested.length <= 60)
    }
}
