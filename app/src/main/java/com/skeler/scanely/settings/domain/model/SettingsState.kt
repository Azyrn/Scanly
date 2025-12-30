package com.skeler.scanely.settings.domain.model

data class SettingsState(
    val themeMode: Int,
    val isHighContrastDarkMode: Boolean,
    val ocrLanguages: Set<String>
)