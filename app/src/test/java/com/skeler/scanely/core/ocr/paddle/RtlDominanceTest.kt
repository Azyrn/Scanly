package com.skeler.scanely.core.ocr.paddle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reading-order direction must follow the recognized text, never the installed pack. */
class RtlDominanceTest {

    @Test
    fun `latin table row is left-to-right`() {
        assertFalse(RtlText.isRtlDominant("Moni 80 85 88 82"))
        assertFalse(RtlText.isRtlDominant("Name Tamil English Maths Science Total"))
    }

    @Test
    fun `digit-only cells never force right-to-left`() {
        assertFalse(RtlText.isRtlDominant("80"))
        assertFalse(RtlText.isRtlDominant("90 98 98 90"))
        assertFalse(RtlText.isRtlDominant(""))
    }

    @Test
    fun `arabic line is right-to-left`() {
        assertTrue(RtlText.isRtlDominant("مرحبا بالعالم"))
    }

    @Test
    fun `arabic line with digits stays right-to-left`() {
        assertTrue(RtlText.isRtlDominant("الصفحة 12 من 34"))
    }

    @Test
    fun `mostly-latin line with one arabic word stays left-to-right`() {
        assertFalse(RtlText.isRtlDominant("Total score عربي for the term"))
    }
}
