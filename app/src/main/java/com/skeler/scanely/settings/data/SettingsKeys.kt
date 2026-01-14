package com.skeler.scanely.settings.data

import androidx.appcompat.app.AppCompatDelegate
// OcrHelper import removed


enum class SettingsKeys(val default: Any?) {
    THEME_MODE(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    IS_OLED_MODE_ENABLED(false),
    OCR_LANGUAGES(emptySet<String>()),
    USE_DYNAMIC_COLORS(true),
    SEED_COLOR_INDEX(5), // Index into SeedPalettes.ALL (0-5), default = Color06
    LAST_AI_REQUEST_TIMESTAMP(0L), // Epoch millis for rate limiting
    AI_REQUEST_COUNT(0) // Number of AI requests in current rate limit window (0-2)
}