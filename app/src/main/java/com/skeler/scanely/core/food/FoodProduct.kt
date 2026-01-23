package com.skeler.scanely.core.food

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Product data from Open Food Facts API.
 */
@Serializable
data class FoodProduct(
    val barcode: String,
    val name: String,
    val brand: String?,
    val imageUrl: String?,
    val nutriScore: String?,
    val novaGroup: Int?,
    val ingredients: String?,
    val calories: String?,
    val fat: String?,
    val carbs: String?,
    val proteins: String?,
    val sugar: String?,
    val salt: String?,
    val categories: String?
)

/**
 * API response wrapper.
 */
@Serializable
data class OpenFoodFactsResponse(
    val status: Int,
    val product: ProductDto?
)

@Serializable
data class ProductDto(
    @SerialName("code") val barcode: String? = null,
    @SerialName("product_name") val name: String? = null,
    @SerialName("brands") val brand: String? = null,
    @SerialName("image_front_url") val imageUrl: String? = null,
    @SerialName("image_url") val imageFallbackUrl: String? = null,
    @SerialName("nutriscore_grade") val nutriScore: String? = null,
    @SerialName("nova_group") val novaGroup: Int? = null,
    @SerialName("ingredients_text") val ingredients: String? = null,
    @SerialName("categories") val categories: String? = null,
    val nutriments: NutrimentsDto? = null
)

@Serializable
data class NutrimentsDto(
    @SerialName("energy-kcal_100g") val calories: Double? = null,
    @SerialName("fat_100g") val fat: Double? = null,
    @SerialName("carbohydrates_100g") val carbs: Double? = null,
    @SerialName("proteins_100g") val proteins: Double? = null,
    @SerialName("sugars_100g") val sugar: Double? = null,
    @SerialName("salt_100g") val salt: Double? = null
)

/**
 * Map API DTO to domain model.
 */
fun ProductDto.toDomain(): FoodProduct? {
    val productName = name?.takeIf { it.isNotBlank() } ?: return null
    
    return FoodProduct(
        barcode = barcode ?: "",
        name = productName,
        brand = brand?.takeIf { it.isNotBlank() },
        imageUrl = imageUrl ?: imageFallbackUrl,
        nutriScore = nutriScore?.uppercase(),
        novaGroup = novaGroup,
        ingredients = ingredients?.takeIf { it.isNotBlank() },
        calories = nutriments?.calories?.let { "${it.toInt()} kcal" },
        fat = nutriments?.fat?.let { "%.1fg".format(it) },
        carbs = nutriments?.carbs?.let { "%.1fg".format(it) },
        proteins = nutriments?.proteins?.let { "%.1fg".format(it) },
        sugar = nutriments?.sugar?.let { "%.1fg".format(it) },
        salt = nutriments?.salt?.let { "%.2fg".format(it) },
        categories = categories?.split(",")?.firstOrNull()?.trim()
    )
}
