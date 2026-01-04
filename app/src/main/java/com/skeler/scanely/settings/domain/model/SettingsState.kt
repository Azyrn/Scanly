package com.skeler.scanely.settings.domain.model

data class SettingsState(
    val themeMode: Int,
    val isOledModeEnabled: Boolean,
    val ocrLanguages: Set<String>,
    val useDynamicColors: Boolean,
    val seedColorIndex: Int
)