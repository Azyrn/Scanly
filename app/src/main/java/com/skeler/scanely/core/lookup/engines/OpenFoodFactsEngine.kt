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
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "OpenFoodFactsEngine"

/**
 * Lookup engine for food products using Open Food Facts API.
 * 
 * Supports: EAN-8, EAN-13, UPC-A, UPC-E barcodes
 * Data: Nutrition info, Nutri-Score, NOVA group, ingredients, allergens
 */
@Singleton
class OpenFoodFactsEngine @Inject constructor(
    private val api: OpenFoodFactsApi
) : LookupEngine {
    
    override val name = "Open Food Facts"
    override val priority = 0  // Highest priority for food products
    override val category = ProductCategory.FOOD
    
    override fun supports(barcode: String): Boolean {
        // Food barcodes are typically 8-13 digit EAN/UPC codes
        return barcode.all { it.isDigit() } && barcode.length in 8..13
    }
    
    override suspend fun lookup(barcode: String): LookupResult {
        return try {
            Log.d(TAG, "Looking up: $barcode")
            val response = api.getProduct(barcode)
            
            if (response.status == 1 && response.product != null) {
                val food = response.product.toDomain()
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
                allergens = emptyList(), // Not in current FoodProduct model
                calories = food.calories,
                fat = food.fat,
                carbs = food.carbs,
                protein = food.proteins,
                salt = food.salt,
                sugar = food.sugar,
                fiber = null, // Not in current FoodProduct model
                servingSize = null // Not in current FoodProduct model
            )
        )
    }
}
