package com.skeler.scanely.core.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.skeler.scanely.core.food.OpenFoodFactsApi
import com.skeler.scanely.core.lookup.LookupEngine
import com.skeler.scanely.core.lookup.LookupOrchestrator
import com.skeler.scanely.core.lookup.engines.GoogleBooksEngine
import com.skeler.scanely.core.lookup.engines.OpenBeautyFactsEngine
import com.skeler.scanely.core.lookup.engines.OpenFDAEngine
import com.skeler.scanely.core.lookup.engines.OpenFoodFactsEngine
import com.skeler.scanely.core.lookup.engines.OpenLibraryEngine
import com.skeler.scanely.core.lookup.engines.OpenPetFoodFactsEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module for product lookup engines and network dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object LookupModule {
    
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Scanly Android App - https://github.com/Azyrn/Scanly")
                    .build()
                chain.proceed(request)
            }
            .build()
    }
    
    @Provides
    @Singleton
    fun provideOpenFoodFactsApi(okHttpClient: OkHttpClient): OpenFoodFactsApi {
        return Retrofit.Builder()
            .baseUrl(OpenFoodFactsApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenFoodFactsApi::class.java)
    }
    
    // --- Engine Bindings (Multibinding for LookupOrchestrator) ---
    
    @Provides
    @IntoSet
    fun provideOpenFoodFactsEngine(api: OpenFoodFactsApi): LookupEngine {
        return OpenFoodFactsEngine(api)
    }
    
    @Provides
    @IntoSet
    fun provideGoogleBooksEngine(okHttpClient: OkHttpClient): LookupEngine {
        return GoogleBooksEngine(okHttpClient)
    }
    
    @Provides
    @IntoSet
    fun provideOpenFDAEngine(okHttpClient: OkHttpClient): LookupEngine {
        return OpenFDAEngine(okHttpClient)
    }
    
    @Provides
    @IntoSet
    fun provideOpenBeautyFactsEngine(okHttpClient: OkHttpClient): LookupEngine {
        return OpenBeautyFactsEngine(okHttpClient)
    }
    
    @Provides
    @IntoSet
    fun provideOpenPetFoodFactsEngine(okHttpClient: OkHttpClient): LookupEngine {
        return OpenPetFoodFactsEngine(okHttpClient)
    }
    
    @Provides
    @IntoSet
    fun provideOpenLibraryEngine(okHttpClient: OkHttpClient): LookupEngine {
        return OpenLibraryEngine(okHttpClient)
    }
    
    @Provides
    @Singleton
    fun provideLookupOrchestrator(engines: Set<@JvmSuppressWildcards LookupEngine>): LookupOrchestrator {
        return LookupOrchestrator(engines)
    }
}
