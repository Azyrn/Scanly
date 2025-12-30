package com.skeler.scanely.settings.data

import androidx.appcompat.app.AppCompatDelegate
import com.skeler.scanely.ocr.OcrHelper.Companion.SUPPORTED_LANGUAGES_MAP


enum class SettingsKeys(val default: Any?) {
    THEME_MODE(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    HIGH_CONTRAST_DARK_MODE(false),
    OCR_LANGUAGES(SUPPORTED_LANGUAGES_MAP.keys)
}