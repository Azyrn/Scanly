package com.skeler.scanely.core.ocr.paddle

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The merge runs on raw CTC output in visual order. Positions here are the normalized timestep
 * each character was read at, which is what both models report for the same crop.
 */
class ScriptMergeTest {

    /** Lays characters out left to right at a regular pitch, starting at [from]. */
    private fun line(text: String, from: Float = 0.05f, pitch: Float = 0.03f, confidence: Float = 0.95f) =
        RecLine(
            text = text,
            confidence = confidence,
            chars = text.mapIndexed { i, c ->
                RecChar(c.toString(), from + i * pitch, confidence)
            }
        )

    private fun chars(vararg c: Triple<String, Float, Float>) = RecLine(
        text = c.joinToString("") { it.first },
        confidence = c.map { it.third }.average().toFloat(),
        chars = c.map { RecChar(it.first, it.second, it.third) }
    )

    @Test
    fun latinWordReplacesTheScriptModelsGuessAtIt() {
        // Arabic model over "Original": low-confidence nonsense, as it is on a real page.
        val primary = chars(
            Triple("أ", 0.05f, 0.44f), Triple("و", 0.08f, 0.36f), Triple("أ", 0.11f, 0.43f),
            Triple("ر", 0.14f, 0.66f), Triple(" ", 0.26f, 0.90f),
            Triple("ى", 0.30f, 0.99f), Triple("ل", 0.33f, 1.0f), Triple("ع", 0.36f, 0.99f)
        )
        val latin = line("Original", from = 0.05f, pitch = 0.02f, confidence = 0.96f)

        assertEquals("Original ىلع", ScriptMerge.merge(primary, latin).text)
    }

    /** The failure that killed the naive version: the run must not leap the Arabic between words. */
    @Test
    fun twoLatinWordsDoNotSwallowTheScriptBetweenThem() {
        val primary = chars(
            Triple("M", 0.05f, 0.79f), Triple("r", 0.11f, 0.27f), Triple("ا", 0.13f, 0.26f),
            Triple(" ", 0.28f, 0.90f),
            Triple("ع", 0.31f, 0.99f), Triple("ل", 0.34f, 1.0f), Triple("ى", 0.37f, 0.99f),
            Triple(" ", 0.39f, 0.90f),
            Triple("ن", 0.42f, 0.43f), Triple("ر", 0.45f, 0.36f), Triple("ا", 0.48f, 0.66f)
        )
        // "Markdown" at 0.05-0.26, then a wide gap over the Arabic, then "Original" at 0.42-0.56.
        val latin = RecLine(
            text = "MarkdownOriginal",
            confidence = 0.95f,
            chars = "Markdown".mapIndexed { i, c -> RecChar(c.toString(), 0.05f + i * 0.03f, 0.95f) } +
                "Original".mapIndexed { i, c -> RecChar(c.toString(), 0.42f + i * 0.02f, 0.95f) }
        )

        assertEquals("Markdown على Original", ScriptMerge.merge(primary, latin).text)
    }

    @Test
    fun hallucinatedLatinOverConfidentScriptIsRejected() {
        // A wholly Arabic line the primary model is sure of.
        val primary = chars(
            Triple("ه", 0.10f, 0.99f), Triple("ذ", 0.20f, 1.0f), Triple("ا", 0.30f, 0.98f),
            Triple("م", 0.50f, 0.99f), Triple("ق", 0.60f, 1.0f), Triple("ص", 0.70f, 0.99f)
        )
        // The universal model reads confident nonsense on top of it.
        val latin = chars(
            Triple("J", 0.10f, 0.90f), Triple("e", 0.20f, 0.88f), Triple("a", 0.30f, 0.92f)
        )

        assertEquals("هذامقص", ScriptMerge.merge(primary, latin).text)
    }

    @Test
    fun aLoneLatinCharacterNeverOverrulesTheScriptModel() {
        val primary = chars(Triple("و", 0.10f, 0.55f), Triple("ل", 0.20f, 0.60f))
        val latin = chars(Triple("J", 0.10f, 0.99f))

        assertEquals("ول", ScriptMerge.merge(primary, latin).text)
    }

    /** The mixed tokens that must survive intact rather than fragment. */
    @Test
    fun mixedTokensKeepTheirDigitsPunctuationAndInternalSpaces() {
        for (token in listOf("Scanly OCR", "Version 2.5", "Android 15", "GPT-5")) {
            val latin = line(token, from = 0.05f, confidence = 0.95f)
            val primary = chars(
                Triple("ذ", 0.60f, 0.45f), Triple("ه", 0.64f, 0.50f)
            )
            assertEquals(token, ScriptMerge.merge(primary, latin).text.substringBefore("ذ").trim())
        }
    }

    /** The Instagram overlay line that came back as "RRepost": the primary read the "R" too, but
     *  reported it further left than the Latin model, just outside the run's padded span. */
    @Test
    fun primaryEchoOfARunsEdgeLetterDoesNotDoubleIt() {
        val primary = chars(
            Triple("R", 0.03f, 0.70f), Triple(" ", 0.25f, 0.90f),
            Triple("ب", 0.30f, 0.50f), Triple("س", 0.34f, 0.45f)
        )
        val latin = line("Repost", from = 0.06f, pitch = 0.02f, confidence = 0.96f)

        assertEquals("Repost بس", ScriptMerge.merge(primary, latin).text.trim())
    }

    @Test
    fun aLineWithNoLatinIsReturnedUntouched() {
        val primary = line("مرحبا", confidence = 0.98f)
        val latin = RecLine("", 0f, emptyList())

        assertEquals(primary.text, ScriptMerge.merge(primary, latin).text)
    }

    /** A pure-Latin line read by the Latin model itself must not be disturbed. */
    @Test
    fun pureLatinLineSurvives() {
        val primary = line("Invoice 4471", confidence = 0.97f)
        val latin = line("Invoice 4471", confidence = 0.98f)

        assertEquals("Invoice 4471", ScriptMerge.merge(primary, latin).text)
    }
}
