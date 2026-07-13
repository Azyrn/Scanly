package com.skeler.scanely.settings.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.skeler.scanely.core.security.KeyCipher
import com.skeler.scanely.settings.data.SettingsKeys
import com.skeler.scanely.settings.data.provider.settingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsDataStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val keyCipher: KeyCipher,
) {
    private val ds = context.settingsDataStore

    // Bearer keys/tokens encrypted at rest via KeyCipher; model/url settings stay plain.
    private val secretKeys = setOf(
        SettingsKeys.OPENROUTER_API_KEY,
        SettingsKeys.GEMINI_API_KEY,
        SettingsKeys.OPENAI_API_KEY,
        SettingsKeys.CLAUDE_API_KEY,
        SettingsKeys.MISTRAL_API_KEY,
        SettingsKeys.HUGGINGFACE_API_KEY,
        SettingsKeys.NVIDIA_API_KEY,
        SettingsKeys.GROQ_API_KEY,
        SettingsKeys.CEREBRAS_API_KEY,
        SettingsKeys.CLOUDFLARE_API_KEY,
        SettingsKeys.CUSTOM_API_KEY,
    )

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

    private fun SettingsKeys.toLongKey(): Preferences.Key<Long> =
        longPreferencesKey(this.name)

    fun longFlow(key: SettingsKeys): Flow<Long> {
        val preferencesKey = key.toLongKey()
        val default = key.default as? Long ?: 0L
        return ds.data
            .map { prefs -> prefs[preferencesKey] ?: default }
    }

    suspend fun setLong(key: SettingsKeys, value: Long) {
        val preferencesKey = key.toLongKey()
        ds.edit { prefs ->
            prefs[preferencesKey] = value
        }
    }

    private fun SettingsKeys.toStringKey(): Preferences.Key<String> =
        stringPreferencesKey(this.name)

    // Secrets: decrypt ciphertext; fall back to raw for pre-encryption plaintext keys.
    fun stringFlow(key: SettingsKeys): Flow<String> {
        val preferencesKey = key.toStringKey()
        val default = key.default as? String ?: ""
        return ds.data
            .map { prefs ->
                val stored = prefs[preferencesKey] ?: return@map default
                if (key in secretKeys && stored.isNotBlank()) {
                    keyCipher.decrypt(stored) ?: stored
                } else {
                    stored
                }
            }
    }

    suspend fun setString(key: SettingsKeys, value: String) {
        val preferencesKey = key.toStringKey()
        val toStore = if (key in secretKeys && value.isNotBlank()) keyCipher.encrypt(value) else value
        ds.edit { prefs ->
            prefs[preferencesKey] = toStore
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
