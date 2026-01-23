package com.skeler.scanely.core.food

import android.util.Log
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FoodRepository"

/**
 * Repository for food product lookups.
 */
@Singleton
class FoodRepository @Inject constructor() {
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        coerceInputValues = true
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Scanly Android App - https://github.com/Azyrn/Scanly")
                .build()
            chain.proceed(request)
        }
        .build()
    
    private val api: OpenFoodFactsApi = Retrofit.Builder()
        .baseUrl(OpenFoodFactsApi.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(OpenFoodFactsApi::class.java)
    
    /**
     * Lookup product by barcode.
     * 
     * @param barcode Product barcode (EAN-13, UPC-A, etc.)
     * @return FoodProduct if found, null otherwise
     */
    suspend fun lookupProduct(barcode: String): Result<FoodProduct?> {
        return try {
            Log.d(TAG, "Looking up product: $barcode")
            val response = api.getProduct(barcode)
            
            if (response.status == 1 && response.product != null) {
                val product = response.product.toDomain()
                Log.d(TAG, "Found product: ${product?.name}")
                Result.success(product)
            } else {
                Log.d(TAG, "Product not found for barcode: $barcode")
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to lookup product: $barcode", e)
            Result.failure(e)
        }
    }
    
    /**
     * Check if barcode is a potential product barcode.
     * Product barcodes are typically 8-13 digits.
     */
    fun isProductBarcode(barcode: String): Boolean {
        val digits = barcode.filter { it.isDigit() }
        return digits.length in 8..13 && digits == barcode
    }
}
