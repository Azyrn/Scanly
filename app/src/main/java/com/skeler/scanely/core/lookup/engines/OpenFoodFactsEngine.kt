package com.skeler.scanely.core.lookup.engines

import android.util.Log
import com.skeler.scanely.core.food.FoodProduct
import com.skeler.scanely.core.food.OpenFoodFactsApi
import com.skeler.scanely.core.food.toDomain
import com.skeler.scanely.core.lookup.FoodData
import com.skeler.scanely.core.lookup.LookupEngine
import com.skeler.scanely.core.lookup.LookupResult
import com.skeler.scanely.core.lookup.ProductCategory
import com.skeler.scanely.core.lookup.ProductInfo
import com.skeler.scanely.core.lookup.isEanUpc
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "OpenFoodFactsEngine"
private const val HTTP_NOT_FOUND = 404

@Singleton
class OpenFoodFactsEngine @Inject constructor(
    private val api: OpenFoodFactsApi
) : LookupEngine {

    override val name = "Open Food Facts"
    override val priority = 0
    override val category = ProductCategory.FOOD

    override fun supports(barcode: String): Boolean = isEanUpc(barcode)

    override suspend fun lookup(barcode: String): LookupResult {
        return try {
            Log.d(TAG, "Looking up: $barcode")
            val response = api.getProduct(barcode)

            if (response.isFound) {
                val food = response.product?.toDomain()
                if (food != null) {
                    val product = mapToProductInfo(barcode, food)
                    Log.d(TAG, "Found: ${product.name}")
                    LookupResult.Found(product, name)
                } else {
                    LookupResult.NotFound(name)
                }
            } else {
                Log.d(TAG, "Not found: $barcode")
                LookupResult.NotFound(name)
            }
        } catch (e: HttpException) {
            // v3 answers an absent product with 404, not a 200 "failure" body.
            if (e.code() == HTTP_NOT_FOUND) {
                Log.d(TAG, "Not found: $barcode")
                LookupResult.NotFound(name)
            } else {
                Log.e(TAG, "Error looking up $barcode", e)
                LookupResult.Error(name, e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up $barcode", e)
            LookupResult.Error(name, e)
        }
    }

    private fun mapToProductInfo(barcode: String, food: FoodProduct): ProductInfo {
        return ProductInfo(
            barcode = barcode,
            source = name,
            category = ProductCategory.FOOD,
            name = food.name,
            brand = food.brand,
            description = null,
            imageUrl = food.imageUrl,
            foodData = FoodData(
                nutriScore = food.nutriScore,
                novaGroup = food.novaGroup,
                ingredients = food.ingredients,
                allergens = emptyList(), // allergens/fiber/servingSize absent from FoodProduct
                calories = food.calories,
                fat = food.fat,
                carbs = food.carbs,
                protein = food.proteins,
                salt = food.salt,
                sugar = food.sugar,
                fiber = null,
                servingSize = null
            )
        )
    }
}
