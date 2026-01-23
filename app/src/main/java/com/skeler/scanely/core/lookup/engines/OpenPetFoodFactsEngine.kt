package com.skeler.scanely.core.lookup.engines

import android.util.Log
import com.skeler.scanely.core.lookup.FoodData
import com.skeler.scanely.core.lookup.LookupEngine
import com.skeler.scanely.core.lookup.LookupResult
import com.skeler.scanely.core.lookup.ProductCategory
import com.skeler.scanely.core.lookup.ProductInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "OpenPetFoodFactsEngine"

/**
 * Lookup engine for pet food products using Open Pet Food Facts API.
 * 
 * Supports: EAN-8, EAN-13, UPC-A barcodes
 * Data: Pet food nutrition, ingredients
 */
@Singleton
class OpenPetFoodFactsEngine @Inject constructor(
    private val okHttpClient: OkHttpClient
) : LookupEngine {
    
    override val name = "Open Pet Food Facts"
    override val priority = 6  // Lower priority, rare use case
    override val category = ProductCategory.PET_FOOD
    
    private val json = Json { ignoreUnknownKeys = true }
    
    override fun supports(barcode: String): Boolean {
        return barcode.all { it.isDigit() } && barcode.length in 8..13
    }
    
    override suspend fun lookup(barcode: String): LookupResult = withContext(Dispatchers.IO) {
        try {
            val url = "https://world.openpetfoodfacts.org/api/v0/product/$barcode.json"
            
            Log.d(TAG, "Looking up: $barcode")
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Scanly Android App - https://github.com/Azyrn/Scanly")
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext LookupResult.NotFound(name)
            
            val productResponse = json.decodeFromString<PetFoodProductResponse>(body)
            
            if (productResponse.status == 1 && productResponse.product != null) {
                val product = mapToProductInfo(barcode, productResponse.product)
                Log.d(TAG, "Found: ${product.name}")
                LookupResult.Found(product, name)
            } else {
                Log.d(TAG, "Not found: $barcode")
                LookupResult.NotFound(name)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up $barcode", e)
            LookupResult.Error(name, e)
        }
    }
    
    private fun mapToProductInfo(barcode: String, product: PetFoodProduct): ProductInfo {
        return ProductInfo(
            barcode = barcode,
            source = name,
            category = ProductCategory.PET_FOOD,
            name = product.productName,
            brand = product.brands,
            description = null,
            imageUrl = product.imageFrontUrl,
            foodData = FoodData(
                nutriScore = null,
                novaGroup = null,
                ingredients = product.ingredientsText,
                allergens = emptyList(),
                calories = product.nutriments?.energy,
                fat = product.nutriments?.fat,
                carbs = product.nutriments?.carbohydrates,
                protein = product.nutriments?.proteins,
                salt = null,
                sugar = null,
                fiber = product.nutriments?.fiber,
                servingSize = null
            )
        )
    }
}

@Serializable
data class PetFoodProductResponse(
    val status: Int = 0,
    val product: PetFoodProduct? = null
)

@Serializable
data class PetFoodProduct(
    @SerialName("product_name")
    val productName: String? = null,
    val brands: String? = null,
    @SerialName("image_front_url")
    val imageFrontUrl: String? = null,
    @SerialName("ingredients_text")
    val ingredientsText: String? = null,
    val nutriments: PetFoodNutriments? = null
)

@Serializable
data class PetFoodNutriments(
    @SerialName("energy_100g")
    val energy: String? = null,
    @SerialName("fat_100g")
    val fat: String? = null,
    @SerialName("carbohydrates_100g")
    val carbohydrates: String? = null,
    @SerialName("proteins_100g")
    val proteins: String? = null,
    @SerialName("fiber_100g")
    val fiber: String? = null
)
