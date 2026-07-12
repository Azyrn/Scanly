package com.skeler.scanely.core.lookup

enum class ProductCategory {
    FOOD,
    BOOK,
    MEDICINE,
    COSMETICS,
    PET_FOOD,
    GENERIC
}

data class ProductInfo(
    val barcode: String,
    val source: String,
    val category: ProductCategory,
    
    val name: String?,
    val brand: String?,
    val description: String?,
    val imageUrl: String?,
    
    val foodData: FoodData? = null,
    
    val bookData: BookData? = null,
    
    val medicineData: MedicineData? = null,
    
    val cosmeticsData: CosmeticsData? = null,
    
    val rawMetadata: Map<String, String> = emptyMap()
)

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

data class CosmeticsData(
    val ingredients: String?,
    val allergens: List<String>,
    val labels: List<String>,
    val categories: List<String>
)
