package com.skeler.scanely.core.food

import com.skeler.scanely.core.lookup.LookupJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Payloads trimmed from live api/v3 responses on world.openfoodfacts.org. */
class OpenFoodFactsV3ResponseTest {

    private val found = """
        {
          "code": "3017620422003",
          "errors": [],
          "product": {
            "brands": "Nutella, Ferrero",
            "categories": "Spreads",
            "code": "3017620422003",
            "image_front_url": "https://images.openfoodfacts.org/front_en.400.jpg",
            "image_url": "https://images.openfoodfacts.org/front_en.400.jpg",
            "ingredients_text": "Sugar, palm oil, hazelnuts",
            "nova_group": 4,
            "nutriments": {
              "energy-kcal_100g": 539,
              "fat_100g": 30.9,
              "carbohydrates_100g": 57.5,
              "proteins_100g": 6.3,
              "sugars_100g": 56.3,
              "salt_100g": 0.107
            },
            "nutriscore_grade": "e",
            "product_name": "Nutella"
          },
          "result": { "id": "product_found", "name": "Product found" },
          "status": "success",
          "warnings": []
        }
    """.trimIndent()

    private val notFound = """
        {
          "code": "9999999999994",
          "errors": [
            { "field": { "id": "code" }, "message": { "id": "product_not_found" } }
          ],
          "result": { "id": "product_not_found", "name": "Product not found" },
          "status": "failure",
          "warnings": []
        }
    """.trimIndent()

    @Test
    fun `v3 success payload decodes into a product`() {
        val response = LookupJson.decodeFromString<OpenFoodFactsResponse>(found)

        assertTrue(response.isFound)

        val product = response.product?.toDomain()
        assertEquals("Nutella", product?.name)
        assertEquals("Nutella, Ferrero", product?.brand)
        assertEquals("E", product?.nutriScore)
        assertEquals(4, product?.novaGroup)
        assertEquals("539 kcal", product?.calories)
        assertEquals("30.9g", product?.fat)
        assertEquals("0.11g", product?.salt)
        assertEquals("Spreads", product?.categories)
    }

    @Test
    fun `v3 failure payload has no product and is not reported as found`() {
        val response = LookupJson.decodeFromString<OpenFoodFactsResponse>(notFound)

        assertFalse(response.isFound)
        assertNull(response.product)
    }
}
