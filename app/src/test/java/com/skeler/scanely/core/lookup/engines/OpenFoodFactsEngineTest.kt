package com.skeler.scanely.core.lookup.engines

import com.skeler.scanely.core.food.OpenFoodFactsApi
import com.skeler.scanely.core.food.OpenFoodFactsResponse
import com.skeler.scanely.core.food.ProductDto
import com.skeler.scanely.core.lookup.LookupResult
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class OpenFoodFactsEngineTest {

    private fun httpException(code: Int) = HttpException(
        Response.error<OpenFoodFactsResponse>(
            code,
            "{}".toResponseBody("application/json".toMediaType())
        )
    )

    private fun engine(block: suspend (String) -> OpenFoodFactsResponse) =
        OpenFoodFactsEngine(object : OpenFoodFactsApi {
            override suspend fun getProduct(barcode: String, fields: String) = block(barcode)
        })

    @Test
    fun `404 is reported as not found rather than an error`() = runBlocking {
        val result = engine { throw httpException(404) }.lookup("4047247981514")

        assertTrue("expected NotFound, got $result", result is LookupResult.NotFound)
    }

    @Test
    fun `server errors are still reported as errors`() = runBlocking {
        val result = engine { throw httpException(500) }.lookup("4047247981514")

        assertTrue("expected Error, got $result", result is LookupResult.Error)
    }

    @Test
    fun `a success payload maps to the product`() = runBlocking {
        val result = engine {
            OpenFoodFactsResponse(
                status = "success",
                product = ProductDto(barcode = it, name = "Test Bar", brand = "Aldi")
            )
        }.lookup("4047247981514")

        assertTrue("expected Found, got $result", result is LookupResult.Found)
        assertEquals("Test Bar", (result as LookupResult.Found).product.name)
    }
}
