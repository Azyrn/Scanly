package com.skeler.scanely.core.ocr.paddle

/**
 * CTC recognition scans crop pixels left-to-right, so RTL lines come out in visual
 * order — the logical string reversed, with embedded Latin/digit runs still LTR
 * (verified on device in PaddleArabicRtlTest). Converts back to logical order.
 */
object RtlText {

    private val MIRROR = mapOf(
        '(' to ')', ')' to '(', '[' to ']', ']' to '[',
        '{' to '}', '}' to '{', '<' to '>', '>' to '<', '«' to '»', '»' to '«'
    )

    private fun isRtl(c: Char): Boolean {
        val d = Character.getDirectionality(c)
        return d == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
            d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
    }

    private fun isLtrStrong(c: Char): Boolean =
        Character.getDirectionality(c) == Character.DIRECTIONALITY_LEFT_TO_RIGHT || c.isDigit()

    private fun isRunJoiner(c: Char): Boolean = c in ".,:/-+"

    /**
     * Whether a line reads right-to-left. Digits are deliberately not counted: they are
     * direction-neutral, so a numeric table cell must never drag a line into RTL order.
     */
    fun isRtlDominant(s: String): Boolean {
        var rtl = 0
        var ltr = 0
        for (c in s) {
            if (isRtl(c)) rtl++
            else if (Character.getDirectionality(c) == Character.DIRECTIONALITY_LEFT_TO_RIGHT) ltr++
        }
        return rtl > ltr
    }

    fun visualToLogical(s: String): String {
        if (s.none { isRtl(it) }) return s
        val out = CharArray(s.length) { i ->
            val c = s[s.length - 1 - i]
            MIRROR[c] ?: c
        }
        // Whole-line reversal also reversed the LTR runs; restore them, letting a
        // single joiner between two LTR chars extend the run (3.14, A-4471).
        var i = 0
        while (i < out.size) {
            if (!isLtrStrong(out[i])) {
                i++
                continue
            }
            var end = i
            var j = i + 1
            while (j < out.size) {
                if (isLtrStrong(out[j])) {
                    end = j
                    j++
                } else if (isRunJoiner(out[j]) && j + 1 < out.size && isLtrStrong(out[j + 1])) {
                    j++
                } else {
                    break
                }
            }
            var lo = i
            var hi = end
            while (lo < hi) {
                val t = out[lo]; out[lo] = out[hi]; out[hi] = t
                lo++; hi--
            }
            i = end + 1
        }
        return String(out)
    }
}
