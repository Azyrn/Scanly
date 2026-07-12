package com.skeler.scanely.core.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.skeler.scanely.core.network.ClaudeApi
import com.skeler.scanely.core.network.GeminiApi
import com.skeler.scanely.core.network.KeyValidationApi
import com.skeler.scanely.core.network.MistralApi
import com.skeler.scanely.core.network.OpenAiCompatApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GenerativeAiModule {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false // omit null request fields (e.g. reasoning_effort) OpenAI-compat servers may reject
        classDiscriminator = "type"
    }

    private val claudeJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "type"
    }

    private val geminiJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private fun defaultClient(readTimeoutSeconds: Long = 60): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideOpenAiCompatApi(): OpenAiCompatApi =
        Retrofit.Builder()
            .baseUrl(OpenAiCompatApi.BASE_URL)
            // 90s read: non-stream multi-image; 60s undercuts attempt budget.
            .client(defaultClient(90))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenAiCompatApi::class.java)

    @Provides
    @Singleton
    fun provideClaudeApi(): ClaudeApi =
        Retrofit.Builder()
            .baseUrl(ClaudeApi.BASE_URL)
            .client(defaultClient(90))
            .addConverterFactory(claudeJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ClaudeApi::class.java)

    @Provides
    @Singleton
    fun provideMistralApi(): MistralApi =
        Retrofit.Builder()
            .baseUrl(MistralApi.BASE_URL)
            .client(defaultClient(90))
            .addConverterFactory(geminiJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MistralApi::class.java)

    @Provides
    @Singleton
    fun provideKeyValidationApi(): KeyValidationApi =
        Retrofit.Builder()
            .baseUrl(KeyValidationApi.BASE_URL)
            .client(defaultClient(20))
            .build()
            .create(KeyValidationApi::class.java)

    @Provides
    @Singleton
    fun provideGeminiApi(): GeminiApi =
        Retrofit.Builder()
            .baseUrl(GeminiApi.BASE_URL)
            .client(defaultClient(90))
            .addConverterFactory(geminiJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GeminiApi::class.java)
}
