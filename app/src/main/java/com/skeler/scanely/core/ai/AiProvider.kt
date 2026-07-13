package com.skeler.scanely.core.ai

enum class ProviderKind {
    OPENAI_COMPAT, // OpenRouter, OpenAI, most self-hosted / custom
    ANTHROPIC,
    GEMINI,
    MISTRAL_OCR // non-chat, non-streaming
}

enum class AiProvider(val displayName: String, val kind: ProviderKind) {
    GEMINI("Gemini", ProviderKind.GEMINI),
    MISTRAL("Mistral OCR", ProviderKind.MISTRAL_OCR),
    OPENROUTER("OpenRouter", ProviderKind.OPENAI_COMPAT),
    HUGGINGFACE("Hugging Face", ProviderKind.OPENAI_COMPAT),
    NVIDIA("NVIDIA", ProviderKind.OPENAI_COMPAT),
    GROQ("Groq", ProviderKind.OPENAI_COMPAT),
    CEREBRAS("Cerebras", ProviderKind.OPENAI_COMPAT),
    CLOUDFLARE("Cloudflare", ProviderKind.OPENAI_COMPAT),
    OPENAI("OpenAI", ProviderKind.OPENAI_COMPAT),
    CLAUDE("Claude", ProviderKind.ANTHROPIC),
    CUSTOM("Custom", ProviderKind.OPENAI_COMPAT);

    companion object {
        val DEFAULT = GEMINI

        fun fromName(name: String?): AiProvider =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

enum class OcrEngine(val id: String, val displayName: String, val model: String) {
    QWEN("qwen", "Qwen OCR", "Qwen/Qwen3-VL-30B-A3B-Instruct");

    companion object {
        val DEFAULT = QWEN
        fun fromId(id: String?): OcrEngine = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
