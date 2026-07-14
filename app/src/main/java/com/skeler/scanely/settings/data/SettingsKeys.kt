package com.skeler.scanely.settings.data

import androidx.appcompat.app.AppCompatDelegate
import com.skeler.scanely.ui.theme.SeedPalettes

enum class SettingsKeys(val default: Any?) {
    THEME_MODE(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    IS_OLED_MODE_ENABLED(false),
    OCR_LANGUAGES(emptySet<String>()),
    USE_DYNAMIC_COLORS(true),
    SEED_COLOR_INDEX(SeedPalettes.DEFAULT_INDEX), // Index into SeedPalettes.ALL
    LAST_AI_REQUEST_TIMESTAMP(0L), // epoch millis
    AI_REQUEST_COUNT(0),

    // Opt-in cross-provider fallback; default = selected only.
    AI_PROVIDER_FALLBACK(false),
    SELECTED_AI_PROVIDER(""), // AiProvider enum name
    OPENROUTER_API_KEY(""),
    GEMINI_API_KEY(""),
    OPENAI_API_KEY(""),
    CLAUDE_API_KEY(""),
    MISTRAL_API_KEY(""),
    HUGGINGFACE_API_KEY(""),
    NVIDIA_API_KEY(""),
    GROQ_API_KEY(""),
    CEREBRAS_API_KEY(""),
    CLOUDFLARE_API_KEY(""),
    CLOUDFLARE_ACCOUNT_ID(""),
    OCR_ENGINE("qwen"), // AI OcrEngine.id
    BARCODE_ENGINE("mlkit"), // BarcodeEngine.id
    TEXT_OCR_ENGINE("paddle"), // TextOcrEngine.id — offline text engine
    PADDLE_SCRIPT("universal"), // ScriptPack.id
    PADDLE_DOC_ORIENTATION(true),
    PADDLE_DOC_UNWARP(false), // UVDoc; only helps curved/folded pages

    // Near a coin toss on Arabic, and page-level orientation already covers the usual case;
    // only a page with genuinely mixed line directions needs it.
    PADDLE_LINE_ORIENTATION(false),
    PADDLE_STRUCTURE(true), // PP-DocLayout: headings/tables in exported Markdown
    OPENROUTER_MODEL(""),
    GEMINI_MODEL(""),
    OPENAI_MODEL(""),
    CLAUDE_MODEL(""),
    MISTRAL_MODEL(""),
    HUGGINGFACE_MODEL(""),
    NVIDIA_MODEL(""),
    GROQ_MODEL(""),
    CEREBRAS_MODEL(""),
    CLOUDFLARE_MODEL(""),
    CUSTOM_API_KEY(""),
    CUSTOM_BASE_URL(""), // full chat/completions URL, not a base
    CUSTOM_MODEL("")
}
