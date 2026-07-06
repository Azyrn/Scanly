package com.skeler.scanely.core.food

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Open Food Facts API interface.
 * 
 * API Docs: https://world.openfoodfacts.org/data
 * No authentication required.
 */
interface OpenFoodFactsApi {
    
    /**
     * Get product information by barcode.
     * 
     * @param barcode EAN-13, UPC-A, or other product barcode
     * @return Product data or status=0 if not found
     */
    @GET("api/v2/product/{barcode}.json")
    suspend fun getProduct(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = FIELDS
    ): OpenFoodFactsResponse

    companion object {
        const val BASE_URL = "https://world.openfoodfacts.org/"

        /** Request only the fields the app maps, cutting response size ~20x. */
        const val FIELDS =
            "code,product_name,brands,image_front_url,image_url," +
                "nutriscore_grade,nova_group,ingredients_text,categories,nutriments"
    }
}
