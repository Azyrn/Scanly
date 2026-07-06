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
    // Use unified state flow to wait for data load (prevents violet flash)
    val uiState by settingsViewModel.settingsUiState.collectAsState()

    // If state is null, we are still loading settings from disk.
    // Return early to render nothing (or a splash background) until data is ready.
    val state = uiState ?: return

    val isDarkTheme = state.resolvedDarkTheme(isSystemInDarkTheme())

    CompositionLocalProvider(
        LocalSettings provides state,
        LocalDarkMode provides isDarkTheme,
    ) {
        content()
    }
}
