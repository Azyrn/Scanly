package com.skeler.scanely.settings.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skeler.scanely.settings.data.SettingsKeys
import com.skeler.scanely.settings.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for App Settings (Theme, Languages).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    fun setBoolean(key: SettingsKeys, value: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.setBoolean(key, value)
        }
    }

    fun getBoolean(key: SettingsKeys): Flow<Boolean> = settingsRepository.getBoolean(key)

    fun setInt(key: SettingsKeys, value: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.setInt(key, value)
        }
    }

    fun getInt(key: SettingsKeys): Flow<Int> = settingsRepository.getInt(key)

    fun setFloat(key: SettingsKeys, value: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.setFloat(key, value)
        }
    }

    fun getFloat(key: SettingsKeys): Flow<Float> = settingsRepository.getFloat(key)

    fun setString(key: SettingsKeys, value: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.setString(key, value)
        }
    }

    fun getString(key: SettingsKeys): Flow<String> = settingsRepository.getString(key)

    fun setStringSet(key: SettingsKeys, value: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.setStringSet(key, value)
        }
    }

    fun getStringSet(key: SettingsKeys): Flow<Set<String>> = settingsRepository.getStringSet(key)

    fun toggleOcrLanguage(langCode: String, current: Set<String>) {
        val updated = current.toMutableSet()

        if (updated.contains(langCode)) {
            updated.remove(langCode)
        } else {
            updated.add(langCode)
        }

        // Safety: never allow empty set
        if (updated.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            settingsRepository.setStringSet(
                SettingsKeys.OCR_LANGUAGES,
                updated
            )
        }
    }
}
