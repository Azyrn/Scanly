package com.skeler.scanely.core.ocr.paddle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 180-flip decision. The bug it guards against: a wrong-script pack scores confident nonsense
 * on the *flipped* orientation of an Arabic page, and if it alone decides the vote it turns an
 * upright page over. Every pack's read is therefore weighed together.
 */
class OrientationDecisionTest {

    @Test
    fun wrongScriptFlippedGarbageDoesNotTurnAnUprightPage() {
        // Measured on 65eb711: universal reads upright as nothing but the flipped page as confident
        // CJC (0.0 vs 3.4); the Arabic pack reads it upright (46.4 vs 14.1) and must win.
        val reads = listOf(
            OrientationRead(upright = 0.0f, flipped = 3.4f),   // universal (wrong script)
            OrientationRead(upright = 46.4f, flipped = 14.1f), // arabic (reads the page)
        )
        assertFalse(isPageUpsideDown(reads))
    }

    @Test
    fun genuinelyInvertedPageIsTurnedOver() {
        val reads = listOf(
            OrientationRead(upright = 3.0f, flipped = 2.0f),
            OrientationRead(upright = 14.0f, flipped = 46.0f),
        )
        assertTrue(isPageUpsideDown(reads))
    }

    @Test
    fun uprightPageIsLeftAlone() {
        assertFalse(isPageUpsideDown(listOf(OrientationRead(upright = 94.1f, flipped = 18.6f))))
    }

    @Test
    fun anUnreadablePageIsLeftAsShot() {
        // Below the score floor in both orientations: no basis to act.
        val reads = listOf(
            OrientationRead(upright = 1.0f, flipped = 1.0f),
            OrientationRead(upright = 2.0f, flipped = 2.5f),
        )
        assertFalse(isPageUpsideDown(reads))
    }

    @Test
    fun noReadsIsNotUpsideDown() {
        assertFalse(isPageUpsideDown(emptyList()))
    }
}
