package com.skeler.scanely.core.ocr.paddle

import org.junit.Assert.assertEquals
import org.junit.Test

class CtcDecoderTest {

    private val charset = listOf("", "a", "b")

    private fun decode(vararg rows: FloatArray): String {
        val logits = FloatArray(rows.size * 3)
        rows.forEachIndexed { index, row -> row.copyInto(logits, index * 3) }
        return CtcDecoder.decodeBatch(
            logits = logits,
            batch = 1,
            steps = rows.size,
            classes = 3,
            charset = charset
        ).first().text
    }

    @Test
    fun gateRecoversNarrowBlank() {
        val text = decode(
            floatArrayOf(0.6f, 0.4f, 0f),
            floatArrayOf(1f, 0f, 0f)
        )

        assertEquals("a", text)
    }

    @Test
    fun gateInertOnConfidentBlank() {
        val text = decode(floatArrayOf(0.97f, 0.02f, 0.01f))

        assertEquals("", text)
    }

    @Test
    fun gatePreservesDoubleLetter() {
        val text = decode(
            floatArrayOf(0f, 1f, 0f),
            floatArrayOf(0.6f, 0.4f, 0f),
            floatArrayOf(0f, 1f, 0f)
        )

        assertEquals("aa", text)
    }

    @Test
    fun normalDecodeUnchanged() {
        val text = decode(
            floatArrayOf(0.05f, 0.9f, 0.05f),
            floatArrayOf(0.9f, 0.05f, 0.05f),
            floatArrayOf(0.05f, 0.05f, 0.9f)
        )

        assertEquals("ab", text)
    }

    @Test
    fun gateEmitsDistinctChar() {
        val text = decode(
            floatArrayOf(0.05f, 0.9f, 0.05f),
            floatArrayOf(0.55f, 0f, 0.45f)
        )

        assertEquals("ab", text)
    }
}
