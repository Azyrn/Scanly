package com.skeler.scanely.core.ocr.paddle

import org.junit.Assert.assertEquals
import org.junit.Test

/** Word-space recovery from CTC character gaps (visual order, Arabic-only). */
class RtlWordSpaceTest {

    private fun chars(vararg pairs: Pair<String, Float>): List<RecChar> =
        pairs.map { (t, x) -> RecChar(t, x, 1f) }

    @Test
    fun `wide gap between two arabic letters inserts a space`() {
        val c = chars("م" to 0.0f, "م" to 0.02f, "م" to 0.04f, "م" to 0.10f, "م" to 0.12f, "م" to 0.14f)
        assertEquals("ممم ممم", RtlText.recoverWordSpaces(c, "مممممم"))
    }

    @Test
    fun `narrow intra-word gaps stay joined`() {
        val c = chars("م" to 0.0f, "م" to 0.02f, "م" to 0.04f, "م" to 0.06f, "م" to 0.08f)
        assertEquals("ممممم", RtlText.recoverWordSpaces(c, "ممممم"))
    }

    @Test
    fun `no space is inserted beside one the model already emitted`() {
        val c = chars("م" to 0.0f, "م" to 0.02f, " " to 0.04f, "م" to 0.10f, "م" to 0.12f)
        assertEquals("مم مم", RtlText.recoverWordSpaces(c, "مم مم"))
    }

    @Test
    fun `latin neighbours are left alone`() {
        val c = chars("a" to 0.0f, "b" to 0.02f, "c" to 0.04f, "d" to 0.10f, "e" to 0.12f, "f" to 0.14f)
        assertEquals("abcdef", RtlText.recoverWordSpaces(c, "abcdef"))
    }

    @Test
    fun `two characters fall back to the stored text`() {
        val c = chars("م" to 0.0f, "م" to 0.5f)
        assertEquals("مم", RtlText.recoverWordSpaces(c, "مم"))
    }
}
