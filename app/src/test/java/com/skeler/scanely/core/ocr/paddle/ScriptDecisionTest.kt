package com.skeler.scanely.core.ocr.paddle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wrong-script decision the service keys the on-device Arabic pack off. The bug it guards
 * against only shows on real photos — the universal model reads a photographed Arabic page as
 * *confident* Latin or CJK, which a line count cannot tell from a real Latin page — so the logic
 * is exercised here on hand-built reads rather than rendered pages.
 */
class ScriptDecisionTest {

    private fun line(text: String, confidence: Float = 0.9f) = RecLine(text, confidence)

    @Test
    fun rtlFractionIgnoresDigitsAndNeutrals() {
        assertEquals(1.0f, RtlText.rtlFraction("مرحبا بك"), 0.001f)
        assertEquals(0.0f, RtlText.rtlFraction("Invoice 4471 total"), 0.001f)
        // A Latin-with-digits header is not an RTL page.
        assertEquals(0.0f, RtlText.rtlFraction("CR 5860008190 Membership 432"), 0.001f)
    }

    @Test
    fun denseLatinPageIsAcceptedAsIs() {
        val english = List(40) { line("This is a full English sentence of some length") }
        assertTrue(isDenseLatinRead(english, detectedLines = 40))
    }

    @Test
    fun arabicMisreadAsConfidentLatinIsNotDense() {
        // The failure shape: the universal pack emits short, confident Latin/CJC nonsense.
        val garbage = List(38) { line("iday", confidence = 0.7f) }
        assertFalse(isDenseLatinRead(garbage, detectedLines = 38))
    }

    @Test
    fun arabicReadReplacesConfidentGarbage() {
        val garbage = List(38) { line("iday l pw", confidence = 0.7f) }
        val arabic = List(38) { line("عقد عمل الطرف الأول شركة بكرى للمقاولات") }
        assertTrue(isBetterScriptRead(current = garbage, candidate = arabic))
    }

    @Test
    fun arabicBodyWithLatinHeaderStillWins() {
        val garbage = List(34) { line("plall l iday", confidence = 0.7f) }
        val mixed = List(34) { line("شركة بكرى أحمد الصعب للمقاولات العامة BAKKRI AHMED") }
        assertTrue(isBetterScriptRead(current = garbage, candidate = mixed))
    }

    @Test
    fun englishOnlyCandidateNeverWinsHoweverLong() {
        val english = listOf(line("Invoice number 4471"), line("Total amount due"))
        val longerEnglish = List(20) { line("A much longer run of purely Latin sentence text") }
        assertFalse(isBetterScriptRead(current = english, candidate = longerEnglish))
    }

    @Test
    fun bilingualIdCardIsNotFlippedOntoArabic() {
        // Real trap: the Arabic pack reads *more* characters (it captures the card's Arabic side),
        // but the read is only ~half RTL, so it must not displace the correct Latin read.
        val latinCard = List(11) { line("United Arab Emirates 784-2004") }
        val bilingual = List(11) { line("عبدالله Saeed Salem Ali الهاشمي") }
        assertTrue("card read must be under the RTL bar",
            RtlText.rtlFraction("عبدالله Saeed Salem Ali الهاشمي") < 0.6f)
        assertTrue(recognizedChars(bilingual) > recognizedChars(latinCard))
        assertFalse(isBetterScriptRead(current = latinCard, candidate = bilingual))
    }
}
