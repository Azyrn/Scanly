package com.skeler.scanely.core.common

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.compositionLocalOf
import com.skeler.scanely.settings.domain.model.SettingsState
import com.skeler.scanely.ui.theme.SeedPalettes

val LocalSettings = compositionLocalOf {
    SettingsState(
        themeMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
        isOledModeEnabled = false,
        ocrLanguages = emptySet(),
        useDynamicColors = true,
        seedColorIndex = SeedPalettes.DEFAULT_INDEX
    )
}