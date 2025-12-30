package com.skeler.scanely.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.skeler.scanely.ocr.OcrHelper
import com.skeler.scanely.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.System,
    val ocrLanguages: Set<String> = setOf("eng")
)

/**
 * ViewModel for App Settings (Theme, Languages).
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val prefs = application.getSharedPreferences("ScanelyPrefs", Context.MODE_PRIVATE)

    init {
        loadSettings()
    }

    private fun loadSettings() {
        val themeName = prefs.getString("theme_mode", ThemeMode.System.name) ?: ThemeMode.System.name
        val themeMode = try { ThemeMode.valueOf(themeName) } catch (e: Exception) { ThemeMode.System }

        val langSet = prefs.getStringSet("ocr_langs", OcrHelper.SUPPORTED_LANGUAGES_MAP.keys) 
            ?: OcrHelper.SUPPORTED_LANGUAGES_MAP.keys

        _uiState.update { 
            SettingsUiState(
                themeMode = themeMode,
                ocrLanguages = langSet
            )
        }
    }

    fun updateTheme(mode: ThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    fun updateLanguages(languages: Set<String>) {
        _uiState.update { it.copy(ocrLanguages = languages) }
        prefs.edit().putStringSet("ocr_langs", languages).apply()
    }

    fun setOledThemeEnabled(enabled: Boolean) {
        _uiState.update { it.copy(oledThemeEnabled = enabled) }
        prefs.edit().putBoolean("oled_theme_enabled", enabled).apply()
    }

}
