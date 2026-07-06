package com.skeler.scanely.core.ai

internal object AiPrompts {
    const val EXTRACT =
        "Extract all visible text from this image. Return only the extracted text, nothing else."

    const val PDF_EXTRACT = """Extract ALL text content from this document.
Rules:
1. Extract every single word, number, and character
2. Preserve the original formatting and structure
3. Include all tables, headers, footers, and captions
4. Do not summarize or skip any content
5. Return ONLY the extracted text, no descriptions or commentary"""

    const val ICON_TRANSLATE =
        "Extract all visible text from this image and translate it to English."

    const val SYSTEM_INSTRUCTION = """You are a precise document text extraction assistant.
Your task is to extract text exactly as it appears in images and documents.

Rules:
1. Extract ALL visible text with 100% accuracy
2. Preserve original formatting, line breaks, and structure
3. Do NOT summarize, interpret, or modify any content
4. Do NOT add any commentary or descriptions
5. For tables, maintain column alignment using spaces
6. For multi-language documents, preserve all languages as-is
7. If text is unclear, mark it with [unclear] but attempt best guess"""

    fun forMode(mode: AiMode): String = when (mode) {
        AiMode.EXTRACT_TEXT -> EXTRACT
        AiMode.EXTRACT_PDF_TEXT -> PDF_EXTRACT
        AiMode.ICON_TRANSLATE -> ICON_TRANSLATE
    }

    fun translate(text: String, targetLanguage: String): String =
        "Translate the following text to $targetLanguage. " +
            "Return only the translated text, nothing else:\n\n$text"
}
