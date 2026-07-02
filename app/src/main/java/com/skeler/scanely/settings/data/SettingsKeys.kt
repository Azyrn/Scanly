package com.skeler.scanely.settings.data

import androidx.appcompat.app.AppCompatDelegate
import com.skeler.scanely.ui.theme.SeedPalettes


enum class SettingsKeys(val default: Any?) {
    THEME_MODE(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    IS_OLED_MODE_ENABLED(false),
    OCR_LANGUAGES(emptySet<String>()),
    USE_DYNAMIC_COLORS(true),
    SEED_COLOR_INDEX(SeedPalettes.DEFAULT_INDEX), // Index into SeedPalettes.ALL
    LAST_AI_REQUEST_TIMESTAMP(0L), // Epoch millis for rate limiting
    AI_REQUEST_COUNT(0), // Number of AI requests in current rate limit window (0-2)
    OPENROUTER_API_KEY(""), // User-supplied OpenRouter key (empty = not configured)
    GEMINI_API_KEY(""), // User-supplied Google AI (Gemini/Gemma) key; falls back to bundled key
    OPENAI_API_KEY(""), // User-supplied OpenAI key
    CLAUDE_API_KEY(""), // User-supplied Anthropic (Claude) key
    MISTRAL_API_KEY(""), // User-supplied Mistral key; falls back to bundled key
    CUSTOM_API_KEY(""), // Custom OpenAI-compatible endpoint key
    CUSTOM_BASE_URL(""), // Custom endpoint URL (full chat/completions URL)
    CUSTOM_MODEL("") // Custom endpoint model id
}