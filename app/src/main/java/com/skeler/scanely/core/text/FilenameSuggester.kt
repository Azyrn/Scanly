package com.skeler.scanely.core.text

/**
 * Derives an export filename from the extracted text alone — no AI, no network. Works the same
 * for offline OCR and AI scan results because it only ever reads the finished text.
 */
object FilenameSuggester {

    const val FALLBACK = "Scanned Document"

    private const val MAX_WORDS = 4
    private const val MIN_WORDS = 3
    private const val MAX_LENGTH = 60
    private const val MAX_LINES_SCANNED = 20
    private const val MAX_TITLE_LINE_LENGTH = 90

    private val INVALID_CHARS = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]")
    private val WHITESPACE = Regex("\\s+")
    private val PAGE_MARKER = Regex("^-{3} Page \\d+ -{3}$")
    private val HEADING = Regex("^#{1,6}\\s+")
    private val LIST_MARKER = Regex("^\\s*(?:[-*+•]|\\d+[.)])\\s+(?:\\[[ xX]]\\s+)?")
    private val DECORATION = Regex("[*_`~>|#\\[\\]]")

    private val SEPARATORS = charArrayOf(
        ' ', '\t', ',', ';', ':', '.', '/', '\\', '|', '-', '–', '—', '(', ')', '[', ']',
        '{', '}', '"', '\'', '؛', '،', '!', '?', '؟', '=', '+', '&', '@', '#', '*', '~', '<', '>'
    )

    /** Latin and Arabic function words carry no meaning in a filename. */
    private val STOP_WORDS = setOf(
        "a", "an", "the", "and", "or", "but", "if", "of", "in", "on", "at", "to", "for", "with",
        "from", "by", "as", "is", "are", "was", "were", "be", "been", "am", "this", "that",
        "these", "those", "it", "its", "he", "she", "they", "we", "you", "your", "our", "their",
        "his", "her", "my", "me", "us", "them", "do", "does", "did", "not", "no", "so",
        "than", "then", "there", "here", "all", "any", "each", "into", "over", "under", "about",
        "after", "before", "up", "down", "out", "off", "can", "will", "would", "should", "could",
        "have", "has", "had", "page", "pages",
        "في", "من", "الى", "إلى", "على", "عن", "مع", "هذا", "هذه", "ذلك", "تلك", "التي", "الذي",
        "ان", "أن", "إن", "او", "أو", "ثم", "قد", "كان", "كانت", "لا", "ما", "هو", "هي", "نحن",
        "انت", "أنت", "به", "بها", "له", "لها", "كل", "بعد", "قبل", "بين", "عند", "حتى", "صفحة"
    )

    fun suggest(text: String): String {
        val lines = meaningfulLines(text)
        val titleIndex = lines.indexOfFirst { it.isHeading }
            .takeIf { it >= 0 }
            ?: lines.indexOfFirst { it.text.length <= MAX_TITLE_LINE_LENGTH && hasKeywords(it.text) }

        val words = mutableListOf<String>()
        if (titleIndex >= 0) words += keywords(lines[titleIndex].text)

        // A one-word title is too thin to identify a document; borrow from the lines that follow.
        lines.forEachIndexed { index, line ->
            if (words.size < MIN_WORDS && index != titleIndex) words += keywords(line.text)
        }

        return words
            .distinctBy { it.lowercase() }
            .take(MAX_WORDS)
            .joinToString(" ") { it.capitalizeWord() }
            .trimToLength()
            .ifBlank { FALLBACK }
    }

    /** Cleans a name the user typed or edited so it is always a usable filename. */
    fun sanitize(name: String, extension: String = ""): String {
        val withoutExtension = if (
            extension.isNotEmpty() && name.endsWith(".$extension", ignoreCase = true)
        ) {
            name.dropLast(extension.length + 1)
        } else {
            name
        }

        return withoutExtension
            .replace(INVALID_CHARS, " ")
            .replace(WHITESPACE, " ")
            .trim()
            .trim('.')
            .trim()
            .trimToLength()
            .ifBlank { FALLBACK }
    }

    private class Candidate(val text: String, val isHeading: Boolean)

    private fun meaningfulLines(text: String): List<Candidate> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !PAGE_MARKER.matches(it) }
            .take(MAX_LINES_SCANNED)
            .map { line ->
                Candidate(
                    text = line
                        .replace(HEADING, "")
                        .replace(LIST_MARKER, "")
                        .replace(DECORATION, " ")
                        .trim(),
                    isHeading = HEADING.containsMatchIn(line)
                )
            }
            .filter { it.text.isNotBlank() }
            .toList()

    private fun keywords(line: String): List<String> =
        line.split(*SEPARATORS)
            .map { word -> word.trim { !it.isLetterOrDigit() } }
            .filter { it.isMeaningful() }

    private fun hasKeywords(line: String): Boolean = keywords(line).isNotEmpty()

    private fun String.isMeaningful(): Boolean =
        length in 2..24 && any { it.isLetterOrDigit() } && lowercase() !in STOP_WORDS

    private fun String.capitalizeWord(): String =
        if (any { it.isUpperCase() }) this else replaceFirstChar { it.uppercase() }

    private fun String.trimToLength(): String =
        if (length <= MAX_LENGTH) this else take(MAX_LENGTH).substringBeforeLast(' ').trim()
}
