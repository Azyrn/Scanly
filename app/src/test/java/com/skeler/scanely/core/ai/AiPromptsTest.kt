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
    fun `transcription rules use the page separator the offline engines emit`() {
        assertTrue(AiPrompts.TRANSCRIBE_SYSTEM.contains("--- Page N ---"))
    }
}
