package com.skeler.scanely.core.lookup.engines

import android.util.Log
import com.skeler.scanely.core.food.OFF_STATUS_SUCCESS
import com.skeler.scanely.core.lookup.FoodData
import com.skeler.scanely.core.lookup.LookupEngine
import com.skeler.scanely.core.lookup.LookupJson
import com.skeler.scanely.core.lookup.LookupResult
import com.skeler.scanely.core.lookup.ProductCategory
import com.skeler.scanely.core.lookup.ProductInfo
import com.skeler.scanely.core.lookup.isEanUpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "OpenPetFoodFactsEngine"
private const val FIELDS =
    "product_name,brands,image_front_url,ingredients_text,nutriments"

@Singleton
class OpenPetFoodFactsEngine @Inject constructor(
    private val okHttpClient: OkHttpClient
) : LookupEngine {

    override val name = "Open Pet Food Facts"
    override val priority = 6
    override val category = ProductCategory.PET_FOOD

    override fun supports(barcode: String): Boolean = isEanUpc(barcode)

    override suspend fun lookup(barcode: String): LookupResult = withContext(Dispatchers.IO) {
        try {
            val url = "https://world.openpetfoodfacts.org/api/v3/product/$barcode.json?fields=$FIELDS"
            Log.d(TAG, "Looking up: $barcode")

            val request = Request.Builder().url(url).build()
            val body = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext LookupResult.NotFound(name)
                response.body?.string()
            } ?: return@withContext LookupResult.NotFound(name)

            val productResponse = LookupJson.decodeFromString<PetFoodProductResponse>(body)

            val found = productResponse.product?.takeIf { productResponse.status == OFF_STATUS_SUCCESS }

            if (found != null) {
                val product = mapToProductInfo(barcode, found)
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
        val n = product.nutriments
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
                calories = n?.energy?.let { "${it.toInt()} kcal" },
                fat = n?.fat?.let { "%.1fg".format(it) },
                carbs = n?.carbohydrates?.let { "%.1fg".format(it) },
                protein = n?.proteins?.let { "%.1fg".format(it) },
                salt = null,
                sugar = null,
                fiber = n?.fiber?.let { "%.1fg".format(it) },
                servingSize = null
            )
        )
    }
}

@Serializable
data class PetFoodProductResponse(
    val status: String = "",
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
    @SerialName("energy-kcal_100g")
    val energy: Double? = null,
    @SerialName("fat_100g")
    val fat: Double? = null,
    @SerialName("carbohydrates_100g")
    val carbohydrates: Double? = null,
    @SerialName("proteins_100g")
    val proteins: Double? = null,
    @SerialName("fiber_100g")
    val fiber: Double? = null
)
