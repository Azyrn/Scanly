package com.skeler.scanely.core.ai

/**
 * How a provider's HTTP request is shaped. Several providers share the
 * OpenAI-compatible chat-completions wire format and only differ by base URL,
 * model and auth, so they collapse into one [ProviderKind].
 */
enum class ProviderKind {
    OPENAI_COMPAT, // OpenRouter, OpenAI, most self-hosted / custom endpoints
    ANTHROPIC,     // Claude Messages API
    GEMINI,        // Google Generative Language API
    MISTRAL_OCR    // Mistral dedicated OCR API (non-chat, non-streaming)
}

/**
 * Selectable AI backend for text extraction / translation, chosen per-scan in
 * the AI mode sheet.
 *
 * [GEMINI] ships with a bundled key so scans work out of the box; every other
 * provider uses a key the user enters in Settings. [CUSTOM] additionally reads
 * its base URL and model from Settings.
 */
enum class AiProvider(val displayName: String, val kind: ProviderKind) {
    GEMINI("Gemini", ProviderKind.GEMINI),
    MISTRAL("Mistral OCR", ProviderKind.MISTRAL_OCR),
    OPENROUTER("OpenRouter", ProviderKind.OPENAI_COMPAT),
    OPENAI("OpenAI", ProviderKind.OPENAI_COMPAT),
    CLAUDE("Claude", ProviderKind.ANTHROPIC),
    CUSTOM("Custom", ProviderKind.OPENAI_COMPAT);

    companion object {
        /** Default provider — backed by the bundled key. */
        val DEFAULT = GEMINI
    }
}
