package com.skeler.scanely.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPromptsTest {

    @Test
    fun `extraction modes get the transcription rules`() {
        assertEquals(AiPrompts.TRANSCRIBE_SYSTEM, AiPrompts.systemFor(AiMode.EXTRACT_TEXT))
        assertEquals(AiPrompts.TRANSCRIBE_SYSTEM, AiPrompts.systemFor(AiMode.EXTRACT_PDF_TEXT))
    }

    /** The transcription rules forbid translating, which is the whole point of this mode. */
    @Test
    fun `translate mode does not get the transcription rules`() {
        assertEquals(AiPrompts.TRANSLATE_SYSTEM, AiPrompts.systemFor(AiMode.ICON_TRANSLATE))
    }

    @Test
    fun `summary lengths produce distinct prompts containing the source text`() {
        val source = "Invoice 0042 is due on 2026-08-01."
        val prompts = SummaryLength.entries.map { AiPrompts.summarize(source, it) }

        assertEquals(SummaryLength.entries.size, prompts.toSet().size)
        assertTrue(prompts.all { it.contains(source) })
    }

    @Test
    fun `summary system prompt forbids translating the source language`() {
        assertTrue(AiPrompts.SUMMARIZE_SYSTEM.contains("Never translate"))
        assertTrue(AiPrompts.SUMMARIZE_SYSTEM.contains("same language"))
    }

    @Test
    fun `detailed summary asks for dates amounts and reference numbers`() {
        val prompt = AiPrompts.summarize("Invoice text", SummaryLength.DETAILED)

        assertTrue(prompt.contains("date"))
        assertTrue(prompt.contains("amount"))
        assertTrue(prompt.contains("reference number"))
    }

    @Test
    fun `transcription rules use the page separator the offline engines emit`() {
        assertTrue(AiPrompts.TRANSCRIBE_SYSTEM.contains("--- Page N ---"))
    }
}
