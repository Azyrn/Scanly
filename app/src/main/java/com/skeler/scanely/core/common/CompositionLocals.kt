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
    // Use unified state flow to wait for data load (prevents violet flash)
    val uiState by settingsViewModel.settingsUiState.collectAsState()

    // If state is null, we are still loading settings from disk.
    // Return early to render nothing (or a splash background) until data is ready.
    val state = uiState ?: return

    val isDarkTheme = when (state.themeMode) {
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


