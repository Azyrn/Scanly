package com.skeler.scanely.settings.data

import androidx.appcompat.app.AppCompatDelegate
import com.skeler.scanely.ocr.OcrHelper.Companion.SUPPORTED_LANGUAGES_MAP


enum class SettingsKeys(val default: Any?) {
    THEME_MODE(AppCompatDelegate.MODE_NIGHT_YES),
    IS_OLED_MODE_ENABLED(false),
    OCR_LANGUAGES(SUPPORTED_LANGUAGES_MAP.keys),
    USE_DYNAMIC_COLORS(true),
    SEED_COLOR_INDEX(5) // Index into SeedPalettes.ALL (0-5), default = Color06
}