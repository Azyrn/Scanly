package com.skeler.scanely.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanBudgetTest {

    private fun payload(pageCount: Int, pdfBase64: String? = null) = PayloadFactory.Payload(
        prompt = "prompt",
        images = List(pageCount) { "page$it" },
        pdfBase64 = pdfBase64
    )

    @Test
    fun `groq is trimmed to its documented five-image cap`() {
        val sent = ScanBudget.trimFor(AiProvider.GROQ, payload(20))

        assertEquals(5, sent.images.size)
        assertEquals(15, sent.dropped)
        assertEquals(listOf("page0", "page1", "page2", "page3", "page4"), sent.images)
    }

    @Test
    fun `cerebras is trimmed to its documented five-image cap`() {
        assertEquals(5, ScanBudget.trimFor(AiProvider.CEREBRAS, payload(20)).images.size)
    }

    @Test
    fun `undocumented providers send a single image`() {
        listOf(AiProvider.NVIDIA, AiProvider.CLOUDFLARE, AiProvider.CUSTOM).forEach { provider ->
            val sent = ScanBudget.trimFor(provider, payload(20))
            assertEquals("${provider.displayName} should send 1 image", 1, sent.images.size)
            assertEquals(19, sent.dropped)
        }
    }

    /** 20 is asserted literally: comparing against MAX_SCAN_PAGES would pass at any cap. */
    @Test
    fun `high-cap providers carry the full twenty pages`() {
        listOf(AiProvider.GEMINI, AiProvider.OPENROUTER, AiProvider.CLAUDE, AiProvider.OPENAI)
            .forEach { provider ->
                val sent = ScanBudget.trimFor(provider, payload(20))
                assertEquals("${provider.displayName} should send all 20 pages", 20, sent.images.size)
                assertEquals(0, sent.dropped)
            }
    }

    @Test
    fun `the scan ceiling is twenty pages`() {
        assertEquals(20, MAX_SCAN_PAGES)
    }

    @Test
    fun `nothing is dropped when the page count is under the cap`() {
        val sent = ScanBudget.trimFor(AiProvider.GROQ, payload(3))

        assertEquals(3, sent.images.size)
        assertEquals(0, sent.dropped)
    }

    @Test
    fun `mistral sends the source pdf whole instead of rendered pages`() {
        val sent = ScanBudget.trimFor(AiProvider.MISTRAL, payload(20, pdfBase64 = "PDFDATA"))

        assertEquals("PDFDATA", sent.pdfBase64)
        assertTrue("rendered pages are redundant once the PDF is sent", sent.images.isEmpty())
        assertEquals("a whole PDF loses no pages", 0, sent.dropped)
    }

    @Test
    fun `mistral falls back to its one-image cap without a source pdf`() {
        val sent = ScanBudget.trimFor(AiProvider.MISTRAL, payload(4))

        assertNull(sent.pdfBase64)
        assertEquals(1, sent.images.size)
        assertEquals(3, sent.dropped)
    }

    @Test
    fun `truncation notice names the provider, its cap, and the page total`() {
        val notice = ScanBudget.truncationNotice(AiProvider.GROQ, total = 20)

        assertTrue(notice, notice.contains("Groq"))
        assertTrue(notice, notice.contains("5 pages"))
        assertTrue(notice, notice.contains("20"))
    }

    @Test
    fun `no provider cap silently exceeds the scan ceiling`() {
        AiProvider.entries.forEach { provider ->
            val sent = ScanBudget.trimFor(provider, payload(MAX_SCAN_PAGES))
            assertTrue(
                "${provider.displayName} sent ${sent.images.size} images",
                sent.images.size <= MAX_SCAN_PAGES
            )
        }
    }
}
