package com.skeler.scanely

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skeler.scanely.navigation.ScanelyNavigation
import com.skeler.scanely.ui.SettingsViewModel
import com.skeler.scanely.ui.theme.ScanelyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Scope SettingsViewModel to the Activity to persist across screens
            val settingsViewModel: SettingsViewModel = viewModel()
            val settingsState by settingsViewModel.uiState.collectAsState()
            
            ScanelyTheme(themeMode = settingsState.themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ScanelyNavigation(
                        currentTheme = settingsState.themeMode,
                        onThemeChanged = settingsViewModel::updateTheme,
                        ocrLanguages = settingsState.ocrLanguages,
                        onOcrLanguagesChanged = settingsViewModel::updateLanguages
                    )
                }
            }
        }
    }
}