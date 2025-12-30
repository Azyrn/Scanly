package com.skeler.scanely.settings.data.repository

import com.skeler.scanely.settings.data.SettingsKeys
import com.skeler.scanely.settings.data.datastore.SettingsDataStore
import com.skeler.scanely.settings.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow

class SettingsRepositoryImpl(
    private val dataStore: SettingsDataStore
) : SettingsRepository {

    override fun getBoolean(key: SettingsKeys): Flow<Boolean> = dataStore.booleanFlow(key)
    override suspend fun setBoolean(key: SettingsKeys, value: Boolean) =
        dataStore.setBoolean(key, value)

    override suspend fun toggleSetting(key: SettingsKeys) = dataStore.toggle(key)

    override fun getInt(key: SettingsKeys): Flow<Int> = dataStore.intFlow(key)
    override suspend fun setInt(key: SettingsKeys, value: Int) = dataStore.setInt(key, value)

    override fun getFloat(key: SettingsKeys): Flow<Float> = dataStore.floatFlow(key)
    override suspend fun setFloat(key: SettingsKeys, value: Float) = dataStore.setFloat(key, value)

    override fun getString(key: SettingsKeys): Flow<String> = dataStore.stringFlow(key)
    override suspend fun setString(key: SettingsKeys, value: String) =
        dataStore.setString(key, value)

    override fun getStringSet(key: SettingsKeys): Flow<Set<String>> = dataStore.stringSetFlow(key)
    override suspend fun setStringSet(key: SettingsKeys, value: Set<String>) =
        dataStore.setStringSet(key, value)
}