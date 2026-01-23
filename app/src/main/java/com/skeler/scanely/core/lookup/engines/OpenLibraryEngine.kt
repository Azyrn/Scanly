package com.skeler.scanely.core.lookup.engines

import android.util.Log
import com.skeler.scanely.core.lookup.BookData
import com.skeler.scanely.core.lookup.LookupEngine
import com.skeler.scanely.core.lookup.LookupResult
import com.skeler.scanely.core.lookup.ProductCategory
import com.skeler.scanely.core.lookup.ProductInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "OpenLibraryEngine"

/**
 * Fallback lookup engine for books using Open Library API.
 * 
 * Supports: ISBN-10, ISBN-13 barcodes
 * Data: Title, authors, publisher, cover
 * 
 * Used as fallback when Google Books doesn't have the book.
 */
@Singleton
class OpenLibraryEngine @Inject constructor(
    private val okHttpClient: OkHttpClient
) : LookupEngine {
    
    override val name = "Open Library"
    override val priority = 2
    override val category = ProductCategory.BOOK
    
    private val json = Json { ignoreUnknownKeys = true }
    
    override fun supports(barcode: String): Boolean {
        val cleaned = barcode.replace("-", "").uppercase()
        return when {
            cleaned.length == 10 && cleaned.take(9).all { it.isDigit() } && 
                (cleaned.last().isDigit() || cleaned.last() == 'X') -> true
            cleaned.length == 13 && cleaned.all { it.isDigit() } && 
                (cleaned.startsWith("978") || cleaned.startsWith("979")) -> true
            else -> false
        }
    }
    
    override suspend fun lookup(barcode: String): LookupResult = withContext(Dispatchers.IO) {
        try {
            val cleaned = barcode.replace("-", "")
            val url = "https://openlibrary.org/api/books?bibkeys=ISBN:$cleaned&format=json&jscmd=data"
            
            Log.d(TAG, "Looking up: $cleaned")
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Scanly Android App")
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext LookupResult.NotFound(name)
            
            val jsonObject = json.parseToJsonElement(body).jsonObject
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
        
        // Extract publishers as list of names
        val publishers = extractNames(bookJson["publishers"])
        
        // Extract authors as list of names
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
    
    /**
     * Extract list of names from a JSON array of objects with "name" field.
     */
    private fun extractNames(element: kotlinx.serialization.json.JsonElement?): List<String> {
        return try {
            when (element) {
                is JsonArray -> element.mapNotNull { item ->
                    item.jsonObject["name"]?.jsonPrimitive?.contentOrNull
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
