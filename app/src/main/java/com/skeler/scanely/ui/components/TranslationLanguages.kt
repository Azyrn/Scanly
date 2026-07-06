package com.skeler.scanely.ui.components

/**
 * Target languages offered for translation. Translation runs through an LLM call
 * (AiScanViewModel.translateResult), so this is purely a display list — no locale
 * codes or extra plumbing. Kept in one place so every screen shares the same set
 * and new languages only ever get added once. Ordered alphabetically.
 */
object TranslationLanguages {
    val ALL: List<String> = listOf(
        "Arabic",
        "Chinese",
        "Dutch",
        "English",
        "French",
        "German",
        "Hindi",
        "Italian",
        "Japanese",
        "Korean",
        "Marathi",
        "Polish",
        "Portuguese",
        "Russian",
        "Spanish",
        "Telugu",
        "Turkish",
        "Urdu"
    )
}
