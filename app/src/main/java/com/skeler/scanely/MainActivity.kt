package com.skeler.scanely

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.skeler.scanely.core.common.CompositionLocals
import com.skeler.scanely.navigation.ScanelyNavigation
import com.skeler.scanely.settings.presentation.viewmodel.SettingsViewModel
import com.skeler.scanely.ui.theme.ScanelyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Hold the splash until settings load so the first visible frame is real
        // content, not the blank pre-settings composition.
        installSplashScreen().setKeepOnScreenCondition {
            settingsViewModel.settingsUiState.value == null
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositionLocals {
                ScanelyTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        ScanelyNavigation()
                    }
                }
            }
        }
    }
}
