package com.skeler.scanely.core.lookup

/**
 * Category of product for engine routing and UI rendering.
 */
enum class ProductCategory {
    FOOD,
    BOOK,
    MEDICINE,
    COSMETICS,
    PET_FOOD,
    GENERIC
}

/**
 * Universal product information model.
 * 
 * All lookup engines map their API responses to this common format,
 * enabling consistent UI rendering regardless of data source.
 */
data class ProductInfo(
    // Core identifiers
    val barcode: String,
    val source: String,
    val category: ProductCategory,
    
    // Basic info (universal)
    val name: String?,
    val brand: String?,
    val description: String?,
    val imageUrl: String?,
    
    // Food-specific
    val foodData: FoodData? = null,
    
    // Book-specific
    val bookData: BookData? = null,
    
    // Medicine-specific
    val medicineData: MedicineData? = null,
    
    // Cosmetics-specific
    val cosmeticsData: CosmeticsData? = null,
    
    // Raw metadata for debugging/extensibility
    val rawMetadata: Map<String, String> = emptyMap()
)

/**
 * Food product data from Open Food Facts and similar sources.
 */
data class FoodData(
    val nutriScore: String?,
    val novaGroup: Int?,
    val ingredients: String?,
    val allergens: List<String>,
    val calories: String?,
    val fat: String?,
    val carbs: String?,
    val protein: String?,
    val salt: String?,
    val sugar: String?,
    val fiber: String?,
    val servingSize: String?
)

/**
 * Book data from Google Books, Open Library, etc.
 */
data class BookData(
    val title: String?,
    val authors: List<String>,
    val publisher: String?,
    val publishedDate: String?,
    val pageCount: Int?,
    val categories: List<String>,
    val isbn10: String?,
    val isbn13: String?,
    val previewLink: String?,
    val infoLink: String?,
    val language: String?
)

/**
 * Medicine/drug data from OpenFDA.
 */
data class MedicineData(
    val genericName: String?,
    val activeIngredients: List<String>,
    val dosageForm: String?,
    val route: String?,
    val manufacturer: String?,
    val warnings: List<String>,
    val contraindications: List<String>,
    val indications: String?,
    val fdaApprovalDate: String?,
    val isRecalled: Boolean
)

/**
 * Cosmetics data from Open Beauty Facts.
 */
data class CosmeticsData(
    val ingredients: String?,
    val allergens: List<String>,
    val labels: List<String>,
    val categories: List<String>
)
