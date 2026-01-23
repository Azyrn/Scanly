package com.skeler.scanely.core.lookup.engines

import android.util.Log
import com.skeler.scanely.core.lookup.CosmeticsData
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

private const val TAG = "OpenBeautyFactsEngine"

/**
 * Lookup engine for cosmetics/beauty products using Open Beauty Facts API.
 * 
 * Supports: EAN-8, EAN-13, UPC-A barcodes
 * Data: Ingredients, allergens, labels, categories
 */
@Singleton
class OpenBeautyFactsEngine @Inject constructor(
    private val okHttpClient: OkHttpClient
) : LookupEngine {
    
    override val name = "Open Beauty Facts"
    override val priority = 5  // Lower priority, fallback after food
    override val category = ProductCategory.COSMETICS
    
    private val json = Json { ignoreUnknownKeys = true }
    
    override fun supports(barcode: String): Boolean {
        return barcode.all { it.isDigit() } && barcode.length in 8..13
    }
    
    override suspend fun lookup(barcode: String): LookupResult = withContext(Dispatchers.IO) {
        try {
            val url = "https://world.openbeautyfacts.org/api/v0/product/$barcode.json"
            
            Log.d(TAG, "Looking up: $barcode")
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Scanly Android App - https://github.com/Azyrn/Scanly")
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext LookupResult.NotFound(name)
            
            val productResponse = json.decodeFromString<BeautyProductResponse>(body)
            
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
    
    private fun mapToProductInfo(barcode: String, product: BeautyProduct): ProductInfo {
        return ProductInfo(
            barcode = barcode,
            source = name,
            category = ProductCategory.COSMETICS,
            name = product.productName,
            brand = product.brands,
            description = null,
            imageUrl = product.imageFrontUrl,
            cosmeticsData = CosmeticsData(
                ingredients = product.ingredientsText,
                allergens = product.allergensTags ?: emptyList(),
                labels = product.labelsTags ?: emptyList(),
                categories = product.categoriesTags ?: emptyList()
            )
        )
    }
}

@Serializable
data class BeautyProductResponse(
    val status: Int = 0,
    val product: BeautyProduct? = null
)

@Serializable
data class BeautyProduct(
    @SerialName("product_name")
    val productName: String? = null,
    val brands: String? = null,
    @SerialName("image_front_url")
    val imageFrontUrl: String? = null,
    @SerialName("ingredients_text")
    val ingredientsText: String? = null,
    @SerialName("allergens_tags")
    val allergensTags: List<String>? = null,
    @SerialName("labels_tags")
    val labelsTags: List<String>? = null,
    @SerialName("categories_tags")
    val categoriesTags: List<String>? = null
)
