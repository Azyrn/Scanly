package com.skeler.scanely.core.ocr.paddle

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

/**
 * Guards the LUT rewrite: every tensor value must be the bit-identical float the
 * original inline expressions produced, for all 256 channel values and both
 * channel orders. Raw-bit comparison so -0.0f vs 0.0f would fail too.
 */
@RunWith(AndroidJUnit4::class)
class ImageOpsLutTest {

    @After
    fun restoreChannelOrder() {
        ImageOps.bgrInput = false
    }

    // Each channel sweeps all 256 values (7 is coprime to 256 for blue).
    private fun allValuesBitmap(w: Int, h: Int): Bitmap {
        val px = IntArray(w * h) { i ->
            val r = i % 256
            val g = 255 - (i % 256)
            val b = (i * 7 + 3) % 256
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun channels(p: Int, bgr: Boolean): IntArray {
        val c0 = if (bgr) p and 0xFF else p shr 16 and 0xFF
        val c2 = if (bgr) p shr 16 and 0xFF else p and 0xFF
        return intArrayOf(c0, p shr 8 and 0xFF, c2)
    }

    private fun assertBits(message: String, expected: Float, actual: Float) {
        assertEquals(message, expected.toRawBits(), actual.toRawBits())
    }

    @Test
    fun imagenetTensor_matchesInlineMath_bothOrders() {
        val bmp = allValuesBitmap(16, 16)
        val px = IntArray(256).also { bmp.getPixels(it, 0, 16, 0, 0, 16, 16) }
        for (bgr in booleanArrayOf(false, true)) {
            ImageOps.bgrInput = bgr
            val buf = ImageOps.imagenetTensor(bmp)
            for (i in px.indices) {
                val c = channels(px[i], bgr)
                for (ch in 0..2) {
                    assertBits(
                        "imagenet px=$i ch=$ch bgr=$bgr",
                        ((c[ch] / 255f) - MEAN[ch]) / STD[ch],
                        buf.get(ch * 256 + i)
                    )
                }
            }
        }
    }

    @Test
    fun recBatchTensor_matchesInlineMath_bothOrders() {
        // 16x48 at REC height 48 keeps its width, so no resampling happens.
        val bmp = allValuesBitmap(16, 48)
        val px = IntArray(16 * 48).also { bmp.getPixels(it, 0, 16, 0, 0, 16, 48) }
        for (bgr in booleanArrayOf(false, true)) {
            ImageOps.bgrInput = bgr
            val (buf, batchW) = ImageOps.recBatchTensor(listOf(bmp), 48, 3200)
            assertEquals(16, batchW)
            val plane = 48 * batchW
            for (i in px.indices) {
                val c = channels(px[i], bgr)
                for (ch in 0..2) {
                    assertBits(
                        "rec px=$i ch=$ch bgr=$bgr",
                        (c[ch] / 127.5f) - 1f,
                        buf.get(ch * plane + i)
                    )
                }
            }
        }
    }

    @Test
    fun paddedImagenetTensor_matchesInlineMath_andPadsWithZero() {
        val side = 32
        val bmp = allValuesBitmap(16, 16)
        val px = IntArray(256).also { bmp.getPixels(it, 0, 16, 0, 0, 16, 16) }
        for (bgr in booleanArrayOf(false, true)) {
            ImageOps.bgrInput = bgr
            val buf = ImageOps.paddedImagenetTensor(bmp, side)
            val plane = side * side
            for (y in 0 until side) {
                for (x in 0 until side) {
                    val inside = x < 16 && y < 16
                    val c = if (inside) channels(px[y * 16 + x], bgr) else null
                    for (ch in 0..2) {
                        val expected = if (c != null) ((c[ch] / 255f) - MEAN[ch]) / STD[ch] else 0f
                        assertBits(
                            "padded x=$x y=$y ch=$ch bgr=$bgr",
                            expected,
                            buf.get(ch * plane + y * side + x)
                        )
                    }
                }
            }
        }
    }
}
