package com.skeler.scanely.core.di

import android.content.Context
import com.skeler.scanely.settings.data.datastore.SettingsDataStore
import com.skeler.scanely.settings.data.repository.SettingsRepositoryImpl
import com.skeler.scanely.settings.domain.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    @Provides
    fun provideSettingsDataStore(@ApplicationContext context: Context): SettingsDataStore =
        SettingsDataStore(context)

    @Provides
    fun provideSettingsRepository(dataStore: SettingsDataStore): SettingsRepository =
        SettingsRepositoryImpl(dataStore)


    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }
}