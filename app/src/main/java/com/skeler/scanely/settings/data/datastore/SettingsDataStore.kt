package com.skeler.scanely.settings.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.skeler.scanely.settings.data.SettingsKeys
import com.skeler.scanely.settings.data.provider.settingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsDataStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val ds = context.settingsDataStore

    private fun SettingsKeys.toBooleanKey(): Preferences.Key<Boolean> =
        booleanPreferencesKey(this.name)

    fun booleanFlow(key: SettingsKeys): Flow<Boolean> {
        val preferencesKey = key.toBooleanKey()
        val default = key.default as? Boolean == true

        return ds.data.map { prefs ->
            prefs[preferencesKey] ?: default
        }
    }

    suspend fun setBoolean(key: SettingsKeys, value: Boolean) {
        val preferencesKey = key.toBooleanKey()
        ds.edit { prefs ->
            prefs[preferencesKey] = value
        }
    }

    suspend fun toggle(key: SettingsKeys) {
        val preferencesKey = key.toBooleanKey()
        ds.edit { prefs ->
            val current = prefs[preferencesKey] == true
            prefs[preferencesKey] = !current
        }
    }

    private fun SettingsKeys.toIntKey(): Preferences.Key<Int> =
        intPreferencesKey(this.name)

    fun intFlow(key: SettingsKeys): Flow<Int> {
        val preferencesKey = key.toIntKey()
        val default = key.default as? Int ?: 0
        return ds.data
            .map { prefs -> prefs[preferencesKey] ?: default }
    }

    suspend fun setInt(key: SettingsKeys, value: Int) {
        val preferencesKey = key.toIntKey()
        ds.edit { prefs ->
            prefs[preferencesKey] = value
        }
    }

    private fun SettingsKeys.toFloatKey(): Preferences.Key<Float> =
        floatPreferencesKey(this.name)

    fun floatFlow(key: SettingsKeys): Flow<Float> {
        val preferencesKey = key.toFloatKey()
        val default = key.default as? Float ?: 0f
        return ds.data
            .map { prefs -> prefs[preferencesKey] ?: default }
    }

    suspend fun setFloat(key: SettingsKeys, value: Float) {
        val preferencesKey = key.toFloatKey()
        ds.edit { prefs ->
            prefs[preferencesKey] = value
        }
    }

    private fun SettingsKeys.toStringKey(): Preferences.Key<String> =
        stringPreferencesKey(this.name)

    fun stringFlow(key: SettingsKeys): Flow<String> {
        val preferencesKey = key.toStringKey()
        val default = key.default as? String ?: ""
        return ds.data
            .map { prefs -> prefs[preferencesKey] ?: default }
    }

    suspend fun setString(key: SettingsKeys, value: String) {
        val preferencesKey = key.toStringKey()
        ds.edit { prefs ->
            prefs[preferencesKey] = value
        }
    }

    private fun SettingsKeys.toStringSetKey(): Preferences.Key<Set<String>> =
        stringSetPreferencesKey(this.name)

    fun stringSetFlow(
        key: SettingsKeys,
    ): Flow<Set<String>> {
        val preferencesKey = key.toStringSetKey()
        val default = key.default as? Set<String> ?: setOf("")

        return ds.data.map { prefs ->
            prefs[preferencesKey] ?: default
        }
    }

    suspend fun setStringSet(
        key: SettingsKeys,
        value: Set<String>
    ) {
        val preferencesKey = key.toStringSetKey()
        ds.edit { prefs ->
            prefs[preferencesKey] = value
        }
    }
}