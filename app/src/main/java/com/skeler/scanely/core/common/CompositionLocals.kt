package com.skeler.scanely.core.common

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.skeler.scanely.settings.presentation.viewmodel.SettingsViewModel

val LocalDarkMode = staticCompositionLocalOf<Boolean> {
    error("No dark mode provided")
}

@Composable
fun CompositionLocals(
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    content: @Composable () -> Unit
) {
    val uiState by settingsViewModel.settingsUiState.collectAsState()
    // Wait for settings so first frame isn't default/flash.
    val state = uiState ?: return

    val isDarkTheme = state.resolvedDarkTheme(isSystemInDarkTheme())

    CompositionLocalProvider(
        LocalSettings provides state,
        LocalDarkMode provides isDarkTheme,
    ) {
        content()
    }
}
