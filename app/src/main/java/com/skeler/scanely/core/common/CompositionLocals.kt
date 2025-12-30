package com.skeler.scanely.core.common

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.skeler.scanely.settings.data.SettingsKeys
import com.skeler.scanely.settings.domain.model.SettingsState
import com.skeler.scanely.settings.presentation.viewmodel.SettingsViewModel

val LocalDarkMode = staticCompositionLocalOf<Boolean> {
    error("No dark mode provided")
}

@Composable
fun CompositionLocals(
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    content: @Composable () -> Unit
) {
    val themeMode by settingsViewModel.intState(SettingsKeys.THEME_MODE)
    val isHighContrastDarkMode by settingsViewModel.booleanState(SettingsKeys.HIGH_CONTRAST_DARK_MODE)
    val ocrLanguages by settingsViewModel.stringSetState(SettingsKeys.OCR_LANGUAGES)

    val state =
        remember(
            themeMode,
            isHighContrastDarkMode,
            ocrLanguages
        ) {
            SettingsState(
                themeMode = themeMode,
                isHighContrastDarkMode = isHighContrastDarkMode,
                ocrLanguages = ocrLanguages
            )
        }

    val isDarkTheme = when (themeMode) {
        AppCompatDelegate.MODE_NIGHT_YES -> true
        AppCompatDelegate.MODE_NIGHT_NO -> false
        else -> isSystemInDarkTheme()
    }

    CompositionLocalProvider(
        LocalSettings provides state,
        LocalDarkMode provides isDarkTheme,
    ) {
        content()
    }
}

@Composable
private fun SettingsViewModel.booleanState(key: SettingsKeys): State<Boolean> {
    return getBoolean(key).collectAsState(initial = key.default as Boolean)
}

@Composable
private fun SettingsViewModel.intState(key: SettingsKeys): State<Int> {
    return getInt(key).collectAsState(initial = key.default as Int)
}

@Composable
private fun SettingsViewModel.floatState(key: SettingsKeys): State<Float> {
    return getFloat(key).collectAsState(initial = key.default as Float)
}

@Composable
private fun SettingsViewModel.stringState(key: SettingsKeys): State<String> {
    return getString(key).collectAsState(initial = key.default as String)
}

@Composable
private fun SettingsViewModel.stringSetState(key: SettingsKeys): State<Set<String>> {
    return getStringSet(key).collectAsState(initial = key.default as Set<String>)
}
