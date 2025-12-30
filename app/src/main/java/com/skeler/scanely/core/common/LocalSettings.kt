package com.skeler.scanely.core.common

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.compositionLocalOf
import com.skeler.scanely.ocr.OcrHelper
import com.skeler.scanely.settings.domain.model.SettingsState

val LocalSettings = compositionLocalOf {
    SettingsState(
        themeMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
        isHighContrastDarkMode = false,
        ocrLanguages = OcrHelper.SUPPORTED_LANGUAGES_MAP.keys
    )
}