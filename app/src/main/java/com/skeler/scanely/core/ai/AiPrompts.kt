package com.skeler.scanely.core.ai

internal object AiPrompts {
    const val EXTRACT =
        "Transcribe this image into Markdown that mirrors it exactly — same text, " +
            "same styling, same layout. Output the transcription only."

    const val PDF_EXTRACT = """Transcribe this document into Markdown that mirrors it exactly.
Rules:
1. Every word, number, and character — nothing skipped, nothing summarized
2. Reproduce styling: bold, italic, headings, lists, underline as seen
3. Keep the original layout: line breaks, blank lines, indentation, alignment
4. Include tables (as Markdown tables), headers, footers, and captions
5. Output the transcription only, no descriptions or commentary"""

    const val ICON_TRANSLATE =
        "Extract all visible text from this image and translate it to English."

    const val SYSTEM_INSTRUCTION = """You are a visual-fidelity OCR engine.
You reproduce the image as Markdown so the output reads exactly like the source: same words, same emphasis, same layout.

Formatting rules — map what you SEE to Markdown:
1. Bold text -> **bold**; italic -> *italic*; bold italic -> ***both***
2. Titles and headings -> # / ## / ### by their visual size and weight
3. Underlined text -> <u>text</u>; strikethrough -> ~~text~~
4. Bullet lists -> "- "; numbered lists keep their exact numbers and markers
5. Tables -> Markdown tables, one row per visual row, columns preserved
6. Checkboxes -> [ ] or [x] matching their state

Layout rules — preserve spacing exactly:
7. Keep every line break where the image breaks; never merge or re-wrap lines
8. Keep blank lines between blocks; keep indentation and leading spaces
9. Keep alignment gaps (columns, right-aligned numbers) using spaces
10. Multi-column pages: transcribe column by column, left to right

Content rules:
11. Transcribe ALL visible text with 100% accuracy — never summarize, translate, correct, or omit
12. Keep every language, symbol, number, and punctuation exactly as printed
13. Unreadable text -> best guess marked [unclear]
14. Output ONLY the transcription: no commentary, no code fences around it, no "Here is..." """

    fun forMode(mode: AiMode): String = when (mode) {
        AiMode.EXTRACT_TEXT -> EXTRACT
        AiMode.EXTRACT_PDF_TEXT -> PDF_EXTRACT
        AiMode.ICON_TRANSLATE -> ICON_TRANSLATE
    }

    fun translate(text: String, targetLanguage: String): String =
        "Translate the following text to $targetLanguage. " +
            "Return only the translated text, nothing else:\n\n$text"
}
