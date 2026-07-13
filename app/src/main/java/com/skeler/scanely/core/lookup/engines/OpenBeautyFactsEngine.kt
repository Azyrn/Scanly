package com.skeler.scanely.core.lookup.engines

import android.util.Log
import com.skeler.scanely.core.food.OFF_STATUS_SUCCESS
import com.skeler.scanely.core.lookup.CosmeticsData
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

private const val TAG = "OpenBeautyFactsEngine"
private const val FIELDS =
    "product_name,brands,image_front_url,ingredients_text,allergens_tags,labels_tags,categories_tags"

@Singleton
class OpenBeautyFactsEngine @Inject constructor(
    private val okHttpClient: OkHttpClient
) : LookupEngine {

    override val name = "Open Beauty Facts"
    override val priority = 5
    override val category = ProductCategory.COSMETICS

    override fun supports(barcode: String): Boolean = isEanUpc(barcode)

    override suspend fun lookup(barcode: String): LookupResult = withContext(Dispatchers.IO) {
        try {
            val url = "https://world.openbeautyfacts.org/api/v3/product/$barcode.json?fields=$FIELDS"
            Log.d(TAG, "Looking up: $barcode")

            val request = Request.Builder().url(url).build()
            val body = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext LookupResult.NotFound(name)
                response.body?.string()
            } ?: return@withContext LookupResult.NotFound(name)

            val productResponse = LookupJson.decodeFromString<BeautyProductResponse>(body)

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
    val status: String = "",
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
