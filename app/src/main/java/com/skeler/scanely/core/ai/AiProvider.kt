package com.skeler.scanely.core.ai

enum class ProviderKind {
    OPENAI_COMPAT, // OpenRouter, OpenAI, most self-hosted / custom
    ANTHROPIC,
    GEMINI,
    MISTRAL_OCR // non-chat, non-streaming
}

/**
 * [maxImagesPerRequest] is the documented per-request image cap, since every scan is sent as a
 * single request — exceeding it costs a rejected request against a free-tier daily quota.
 * Undocumented providers stay at 1 rather than risk that. Mistral OCR takes one document per
 * request, but reads a whole PDF from it (see [ProviderKind.MISTRAL_OCR]).
 */
enum class AiProvider(
    val displayName: String,
    val kind: ProviderKind,
    val maxImagesPerRequest: Int
) {
    GEMINI("Gemini", ProviderKind.GEMINI, MANY_IMAGES),
    MISTRAL("Mistral OCR", ProviderKind.MISTRAL_OCR, 1),
    OPENROUTER("OpenRouter", ProviderKind.OPENAI_COMPAT, MANY_IMAGES),
    HUGGINGFACE("Hugging Face", ProviderKind.OPENAI_COMPAT, 5),
    NVIDIA("NVIDIA", ProviderKind.OPENAI_COMPAT, 1),
    GROQ("Groq", ProviderKind.OPENAI_COMPAT, 5),
    CEREBRAS("Cerebras", ProviderKind.OPENAI_COMPAT, 5),
    CLOUDFLARE("Cloudflare", ProviderKind.OPENAI_COMPAT, 1),
    OPENAI("OpenAI", ProviderKind.OPENAI_COMPAT, MANY_IMAGES),
    CLAUDE("Claude", ProviderKind.ANTHROPIC, MANY_IMAGES),
    CUSTOM("Custom", ProviderKind.OPENAI_COMPAT, 1);

    companion object {
        val DEFAULT = GEMINI

        fun fromName(name: String?): AiProvider =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** Above the [MAX_SCAN_PAGES] ceiling, so these providers never truncate a scan. */
private const val MANY_IMAGES = Int.MAX_VALUE

/** Hard ceiling on pages/images per scan, regardless of provider. */
const val MAX_SCAN_PAGES = 20

enum class OcrEngine(val id: String, val displayName: String, val model: String) {
    QWEN("qwen", "Qwen OCR", "Qwen/Qwen3-VL-30B-A3B-Instruct");

    companion object {
        val DEFAULT = QWEN
        fun fromId(id: String?): OcrEngine = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
