package com.skeler.scanely.core.food

import com.skeler.scanely.BuildConfig
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface OpenFoodFactsApi {

    @GET("api/v3/product/{barcode}.json")
    suspend fun getProduct(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = FIELDS
    ): OpenFoodFactsResponse

    companion object {
        const val BASE_URL = "https://world.openfoodfacts.org/"

        // Open Food Facts asks for AppName/Version (contact) so clients aren't taken for bots.
        val USER_AGENT = "Scanly/${BuildConfig.VERSION_NAME} (https://github.com/Azyrn/Scanly)"

        // Only mapped fields — ~20x smaller payloads.
        const val FIELDS =
            "code,product_name,brands,image_front_url,image_url," +
                "nutriscore_grade,nova_group,ingredients_text,categories,nutriments"
    }
}
