package com.skeler.scanely.core.lookup.engines

import android.util.Log
import com.skeler.scanely.core.lookup.BookData
import com.skeler.scanely.core.lookup.LookupEngine
import com.skeler.scanely.core.lookup.LookupJson
import com.skeler.scanely.core.lookup.LookupResult
import com.skeler.scanely.core.lookup.ProductCategory
import com.skeler.scanely.core.lookup.ProductInfo
import com.skeler.scanely.core.lookup.isIsbn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "OpenLibraryEngine"

// ISBN fallback when Google Books misses (priority 2).
@Singleton
class OpenLibraryEngine @Inject constructor(
    private val okHttpClient: OkHttpClient
) : LookupEngine {

    override val name = "Open Library"
    override val priority = 2
    override val category = ProductCategory.BOOK

    override fun supports(barcode: String): Boolean = isIsbn(barcode)

    override suspend fun lookup(barcode: String): LookupResult = withContext(Dispatchers.IO) {
        try {
            val cleaned = barcode.replace("-", "")
            val url = "https://openlibrary.org/api/books?bibkeys=ISBN:$cleaned&format=json&jscmd=data"

            Log.d(TAG, "Looking up: $cleaned")

            val request = Request.Builder().url(url).build()
            val body = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext LookupResult.NotFound(name)
                response.body?.string()
            } ?: return@withContext LookupResult.NotFound(name)

            val jsonObject = LookupJson.parseToJsonElement(body).jsonObject
            val key = "ISBN:$cleaned"

            if (jsonObject.containsKey(key)) {
                val bookJson = jsonObject[key]?.jsonObject
                if (bookJson != null) {
                    val product = mapToProductInfo(barcode, bookJson)
                    Log.d(TAG, "Found: ${product.name}")
                    return@withContext LookupResult.Found(product, name)
                }
            }

            Log.d(TAG, "Not found: $barcode")
            LookupResult.NotFound(name)
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up $barcode", e)
            LookupResult.Error(name, e)
        }
    }

    private fun mapToProductInfo(barcode: String, bookJson: JsonObject): ProductInfo {
        val title = bookJson["title"]?.jsonPrimitive?.contentOrNull
        val publishers = extractNames(bookJson["publishers"])
        val authors = extractNames(bookJson["authors"])
        val coverUrl = bookJson["cover"]?.jsonObject?.get("medium")?.jsonPrimitive?.contentOrNull
        val publishDate = bookJson["publish_date"]?.jsonPrimitive?.contentOrNull
        val pageCount = bookJson["number_of_pages"]?.jsonPrimitive?.intOrNull
        val infoLink = bookJson["url"]?.jsonPrimitive?.contentOrNull

        return ProductInfo(
            barcode = barcode,
            source = name,
            category = ProductCategory.BOOK,
            name = title,
            brand = publishers.firstOrNull(),
            description = null,
            imageUrl = coverUrl?.replace("http:", "https:"),
            bookData = BookData(
                title = title,
                authors = authors,
                publisher = publishers.firstOrNull(),
                publishedDate = publishDate,
                pageCount = pageCount,
                categories = emptyList(),
                isbn10 = if (barcode.length == 10) barcode else null,
                isbn13 = if (barcode.length == 13) barcode else null,
                previewLink = null,
                infoLink = infoLink,
                language = null
            )
        )
    }

    private fun extractNames(element: kotlinx.serialization.json.JsonElement?): List<String> {
        return try {
            when (element) {
                is JsonArray -> element.mapNotNull { item ->
                    item.jsonObject["name"]?.jsonPrimitive?.contentOrNull
                }
                else -> emptyList()
            }
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }
}
