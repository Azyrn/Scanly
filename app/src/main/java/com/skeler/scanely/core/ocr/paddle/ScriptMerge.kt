package com.skeler.scanely.core.ocr.paddle

/**
 * Repairs the Latin words inside a line of another script.
 *
 * A script recognition model reads its own script well and Latin badly: the Arabic model turns
 * "Original" into "أوأر" and drops "Markdown" entirely. Splitting the crop into words and routing
 * each to a different model would mean cutting the image, and Arabic is cursive — a cut lands
 * inside a ligature and destroys the word either side of it.
 *
 * So nothing is cut. Both models read the same whole-line crop, and because CTC scans the crop
 * left to right, each one reports *where* it read every character ([RecChar.x]). The two readings
 * are then merged by position: a stretch that the Latin model reads confidently, and the primary
 * model does not, is a Latin word, and the Latin reading of that stretch wins.
 *
 * The result stays in visual (left-to-right) order, which is what [RtlText.visualToLogical]
 * expects to be handed.
 */
object ScriptMerge {

    // The Latin model must be this sure before it may overrule the primary model. Its confidence
    // on a real Latin word sits at 0.9+; where it hallucinates Latin on Arabic it sits far below.
    private const val LATIN_MIN_CONFIDENCE = 0.85f

    // A single character is never worth overruling the primary for: lone letters are what the
    // Latin model emits as noise over Arabic, while real embedded words ("OCR", "2.5") are longer.
    private const val MIN_RUN = 2

    // Where the primary model is genuinely reading its own script it is sure of it. If most of
    // what it reads under a candidate run is confident script, the run is a hallucination.
    private const val PRIMARY_SURE = 0.9f
    private const val PRIMARY_SURE_SHARE = 0.5f

    // A run breaks when the gap to the next character is this many times the run's own character
    // pitch. Without it a run leaps the Arabic sitting between two Latin words ("Markdown على
    // Original") and swallows it. A space inside "Scanly OCR" is about one pitch wide; the Arabic
    // between two Latin words is many.
    private const val GAP_PITCH_FACTOR = 3f
    private const val MIN_GAP = 0.035f

    private const val DEFAULT_PITCH = 0.03f

    // The primary's kept space can land beside the run's own, and two spaces are never wanted.
    private val DOUBLE_SPACE = Regex(" {2,}")

    /**
     * [primary] is the script model's reading, [latin] the universal model's reading of the same
     * crop. Both must be raw CTC output in visual order.
     */
    fun merge(primary: RecLine, latin: RecLine): RecLine {
        if (primary.chars.isEmpty() || latin.chars.isEmpty()) return primary

        val runs = latinRuns(latin.chars).filter { run -> overrules(run, primary) }
        if (runs.isEmpty()) return primary

        // A space beside the word is the separation between it and the Arabic, and the run's
        // padded span reaches just far enough to swallow it ("تعديل Original" came back as
        // "تعديلOriginal"), so a space is only dropped where the run itself already carries one:
        // inside the word, never at its edge.
        val kept = primary.chars.filterNot { c ->
            if (c.text.isBlank()) runs.any { it.coversCore(c.x) } else runs.any { it.covers(c.x) }
        }
        val merged = (kept + runs.map { it.asChar(latin.chars) }).sortedBy { it.x }
        val text = merged.joinToString("") { it.text }.replace(DOUBLE_SPACE, " ")

        return RecLine(
            text = text,
            confidence = merged.map { it.confidence }.average().toFloat(),
            chars = merged
        )
    }

    private class Run(val chars: List<RecChar>) {
        val start get() = chars.first().x - MIN_GAP / 2f
        val end get() = chars.last().x + MIN_GAP / 2f

        fun covers(x: Float) = x in start..end

        /** The word itself, without the margin either side of it. */
        fun coversCore(x: Float) = x in chars.first().x..chars.last().x

        /**
         * The run's text, spaces included — but only the spaces the model itself emitted inside
         * the run's span. Inventing them from pixel gaps splits "Original" into "O rigin al".
         */
        fun asChar(all: List<RecChar>): RecChar {
            val span = all.filter { it.x >= chars.first().x && it.x <= chars.last().x }
            val text = span.joinToString("") { it.text }.trim()
            return RecChar(
                text = text,
                x = chars.first().x,
                confidence = chars.map { it.confidence }.average().toFloat()
            )
        }
    }

    /** Confident Latin from the universal model, cut wherever the spacing says a new word starts. */
    private fun latinRuns(chars: List<RecChar>): List<Run> {
        val candidates = chars.filter { it.text.isNotBlank() && isLatin(it.text) && it.confidence >= 0.4f }
        if (candidates.size < MIN_RUN) return emptyList()

        val steps = candidates.zipWithNext { a, b -> b.x - a.x }
        val pitch = steps.sorted().getOrElse(steps.size / 2) { DEFAULT_PITCH }
        val limit = maxOf(MIN_GAP, GAP_PITCH_FACTOR * pitch)

        val runs = mutableListOf<MutableList<RecChar>>(mutableListOf(candidates.first()))
        for ((a, b) in candidates.zipWithNext()) {
            if (b.x - a.x > limit) runs.add(mutableListOf())
            runs.last().add(b)
        }
        return runs.filter { it.size >= MIN_RUN }.map { Run(it) }
    }

    /**
     * Whether this run beats the primary model over the same stretch of the line. The primary's
     * confidence cannot be compared directly — CTC is not calibrated, and the Arabic model is
     * happily 0.90 sure of a letter it invented on top of "Original". What does hold is that where
     * it is really reading Arabic, nearly every character comes back at 0.9+.
     */
    private fun overrules(run: Run, primary: RecLine): Boolean {
        if (run.chars.map { it.confidence }.average() < LATIN_MIN_CONFIDENCE) return false

        val under = primary.chars.filter { run.covers(it.x) && it.text.isNotBlank() }
        if (under.isEmpty()) return true

        val sure = under.count { isScript(it.text) && it.confidence >= PRIMARY_SURE }
        return sure.toFloat() / under.size < PRIMARY_SURE_SHARE
    }

    /** Latin, digits and the punctuation that binds them: "Version 2.5", "GPT-5", "Android 15". */
    private fun isLatin(text: String): Boolean =
        text.all { it.code < 0x0590 } && text.any { it.isLetterOrDigit() }

    private fun isScript(text: String): Boolean = text.any { it.code >= 0x0590 }
}
