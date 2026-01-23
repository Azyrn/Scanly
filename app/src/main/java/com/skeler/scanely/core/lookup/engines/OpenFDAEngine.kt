package com.skeler.scanely.core.lookup.engines

import android.util.Log
import com.skeler.scanely.core.lookup.LookupEngine
import com.skeler.scanely.core.lookup.LookupResult
import com.skeler.scanely.core.lookup.MedicineData
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

private const val TAG = "OpenFDAEngine"

/**
 * Lookup engine for medication/drugs using OpenFDA API.
 * 
 * Supports: NDC barcodes (National Drug Code)
 * Data: Drug name, ingredients, warnings, dosage, manufacturer
 * 
 * OpenFDA is free and doesn't require an API key.
 */
@Singleton
class OpenFDAEngine @Inject constructor(
    private val okHttpClient: OkHttpClient
) : LookupEngine {
    
    override val name = "OpenFDA"
    override val priority = 3  // After food, before cosmetics
    override val category = ProductCategory.MEDICINE
    
    private val json = Json { ignoreUnknownKeys = true }
    
    override fun supports(barcode: String): Boolean {
        // Try all numeric barcodes - OpenFDA will return not found if no match
        val digits = barcode.filter { it.isDigit() }
        return digits.length in 10..13
    }
    
    override suspend fun lookup(barcode: String): LookupResult = withContext(Dispatchers.IO) {
        try {
            val cleaned = barcode.filter { it.isDigit() }
            
            // Try UPC search first, then NDC
            val urls = listOf(
                "https://api.fda.gov/drug/label.json?search=openfda.upc:\"$cleaned\"&limit=1",
                "https://api.fda.gov/drug/label.json?search=openfda.package_ndc:\"$cleaned\"&limit=1"
            )
            
            for (url in urls) {
                Log.d(TAG, "Trying: $url")
                
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Scanly Android App")
                    .build()
                
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) continue
                
                val body = response.body?.string() ?: continue
                
                try {
                    val fdaResponse = json.decodeFromString<OpenFDAResponse>(body)
                    
                    if (fdaResponse.results?.isNotEmpty() == true) {
                        val result = fdaResponse.results.first()
                        val product = mapToProductInfo(barcode, result)
                        Log.d(TAG, "Found: ${product.name}")
                        return@withContext LookupResult.Found(product, name)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Parse error for $url: ${e.message}")
                    continue
                }
            }
            
            Log.d(TAG, "Not found: $barcode")
            LookupResult.NotFound(name)
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up $barcode", e)
            LookupResult.Error(name, e)
        }
    }
    
    private fun mapToProductInfo(barcode: String, result: DrugLabel): ProductInfo {
        val openFda = result.openfda
        
        return ProductInfo(
            barcode = barcode,
            source = name,
            category = ProductCategory.MEDICINE,
            name = openFda?.brandName?.firstOrNull() ?: result.splProductDataElements?.firstOrNull(),
            brand = openFda?.manufacturerName?.firstOrNull(),
            description = result.description?.firstOrNull()?.take(500),
            imageUrl = null, // OpenFDA doesn't provide images
            medicineData = MedicineData(
                genericName = openFda?.genericName?.firstOrNull(),
                activeIngredients = result.activeIngredient ?: emptyList(),
                dosageForm = openFda?.dosageForm?.firstOrNull(),
                route = openFda?.route?.firstOrNull(),
                manufacturer = openFda?.manufacturerName?.firstOrNull(),
                warnings = result.warnings ?: emptyList(),
                contraindications = result.contraindications ?: emptyList(),
                indications = result.indicationsAndUsage?.firstOrNull(),
                fdaApprovalDate = null,
                isRecalled = false
            )
        )
    }
}

// --- DTOs for OpenFDA API ---

@Serializable
data class OpenFDAResponse(
    val results: List<DrugLabel>? = null
)

@Serializable
data class DrugLabel(
    @SerialName("spl_product_data_elements")
    val splProductDataElements: List<String>? = null,
    val description: List<String>? = null,
    @SerialName("active_ingredient")
    val activeIngredient: List<String>? = null,
    val warnings: List<String>? = null,
    val contraindications: List<String>? = null,
    @SerialName("indications_and_usage")
    val indicationsAndUsage: List<String>? = null,
    val openfda: OpenFDAData? = null
)

@Serializable
data class OpenFDAData(
    @SerialName("brand_name")
    val brandName: List<String>? = null,
    @SerialName("generic_name")
    val genericName: List<String>? = null,
    @SerialName("manufacturer_name")
    val manufacturerName: List<String>? = null,
    @SerialName("dosage_form")
    val dosageForm: List<String>? = null,
    val route: List<String>? = null
)
