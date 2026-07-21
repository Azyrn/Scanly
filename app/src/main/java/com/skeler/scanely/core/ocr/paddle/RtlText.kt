package com.skeler.scanely.core.ocr.paddle

/**
 * CTC recognition scans crop pixels left-to-right, so RTL lines come out in visual
 * order — the logical string reversed, with embedded Latin/digit runs still LTR
 * (verified on device in PaddleArabicRtlTest). Converts back to logical order.
 */
object RtlText {

    // A blank run wider than this many times the line's own letter pitch is a word gap, not the
    // space between two joined letters. Arabic is cursive: letters within a word sit a pitch apart,
    // words a multiple of it — the inverse of Latin, where the intra-word gaps are the wide ones,
    // which is why this runs only between two Arabic-script neighbours.
    private const val WORD_GAP_FACTOR = 2.3f

    private const val ARABIC_BLOCK_START = 0x0600
    private const val ARABIC_BLOCK_END = 0x06FF

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

    // A space joins too, so a multi-word Latin phrase inside an RTL line stays one run: without
    // it "Version 2.5" is reversed as two runs and comes back out as "2.5 Version". A joiner only
    // extends a run when another LTR character follows, so Arabic after a space is unaffected.
    private fun isRunJoiner(c: Char): Boolean = c in ".,:/-+ "

    /**
     * Whether a line reads right-to-left. Digits are deliberately not counted: they are
     * direction-neutral, so a numeric table cell must never drag a line into RTL order.
     */
    fun isRtlDominant(s: String): Boolean {
        var rtl = 0
        var ltr = 0
        for (c in s) {
            if (isRtl(c)) {
                rtl++
            } else if (Character.getDirectionality(c) == Character.DIRECTIONALITY_LEFT_TO_RIGHT) ltr++
        }
        return rtl > ltr
    }

    /**
     * Share of a string's directional characters that are right-to-left. Digits and neutrals are
     * not counted (as in [isRtlDominant]), so a Latin-with-numbers header does not read as an RTL
     * page and a bilingual ID card stays well below a full Arabic page.
     */
    fun rtlFraction(s: String): Float {
        var rtl = 0
        var strong = 0
        for (c in s) {
            when {
                isRtl(c) -> { rtl++; strong++ }
                Character.getDirectionality(c) == Character.DIRECTIONALITY_LEFT_TO_RIGHT -> strong++
            }
        }
        return if (strong == 0) 0f else rtl.toFloat() / strong
    }

    private fun isArabicChar(c: Char): Boolean = c.code in ARABIC_BLOCK_START..ARABIC_BLOCK_END

    /**
     * Rebuilds a line's visual-order text from its CTC characters, restoring the word spaces the
     * recognizer dropped. The recognizer emits a space glyph at some word boundaries and not others;
     * where it skipped one it still left the blank timesteps behind, so a gap wider than the line's
     * typical letter pitch is a missing space. Only ever inserted between two Arabic-script letters,
     * never beside an existing space or a Latin run, and a no-op on a line of two characters or
     * fewer. Returns [fallback] when there are no positions to reason about.
     */
    fun recoverWordSpaces(chars: List<RecChar>, fallback: String, factor: Float = WORD_GAP_FACTOR): String {
        if (chars.size < 3) return fallback
        val ordered = chars.sortedBy { it.x }
        val gaps = ordered.zipWithNext { a, b -> b.x - a.x }.filter { it > 0f }.sorted()
        if (gaps.isEmpty()) return fallback
        val pitch = gaps[gaps.size / 2]
        val limit = pitch * factor

        val sb = StringBuilder()
        for (i in ordered.indices) {
            val c = ordered[i]
            if (i > 0) {
                val prev = ordered[i - 1]
                val split = c.x - prev.x > limit &&
                    prev.text.isNotBlank() && c.text.isNotBlank() &&
                    prev.text.all { isArabicChar(it) } && c.text.all { isArabicChar(it) }
                if (split) sb.append(' ')
            }
            sb.append(c.text)
        }
        return sb.toString().replace(Regex(" {2,}"), " ")
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
                val t = out[lo];
                out[lo] = out[hi];
                out[hi] = t
                lo++;
                hi--
            }
            i = end + 1
        }
        return String(out)
    }
}
