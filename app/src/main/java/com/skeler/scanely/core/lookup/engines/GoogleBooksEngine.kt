package com.skeler.scanely.core.lookup.engines

import android.util.Log
import com.skeler.scanely.core.lookup.BookData
import com.skeler.scanely.core.lookup.LookupEngine
import com.skeler.scanely.core.lookup.LookupResult
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

private const val TAG = "GoogleBooksEngine"

/**
 * Lookup engine for books using Google Books API.
 * 
 * Supports: ISBN-10, ISBN-13 barcodes
 * Data: Title, authors, publisher, description, cover, preview links
 * 
 * Note: Works without API key for limited quota (~1000 requests/day).
 */
@Singleton
class GoogleBooksEngine @Inject constructor(
    private val okHttpClient: OkHttpClient
) : LookupEngine {
    
    override val name = "Google Books"
    override val priority = 1
    override val category = ProductCategory.BOOK
    
    private val json = Json { ignoreUnknownKeys = true }
    
    override fun supports(barcode: String): Boolean {
        // ISBN-10: 10 digits (may include X at end)
        // ISBN-13: 13 digits starting with 978 or 979
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
            val url = "https://www.googleapis.com/books/v1/volumes?q=isbn:$cleaned"
            
            Log.d(TAG, "Looking up: $cleaned")
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Scanly Android App")
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext LookupResult.NotFound(name)
            
            val booksResponse = json.decodeFromString<GoogleBooksResponse>(body)
            
            if (booksResponse.totalItems > 0 && booksResponse.items?.isNotEmpty() == true) {
                val volume = booksResponse.items.first().volumeInfo
                val product = mapToProductInfo(barcode, volume)
                Log.d(TAG, "Found: ${product.name}")
                LookupResult.Found(product, name)
            } else {
                Log.d(TAG, "Not found: $barcode")
                LookupResult.NotFound(name)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up $barcode", e)
            LookupResult.Error(name, e)
        }
    }
    
    private fun mapToProductInfo(barcode: String, volume: VolumeInfo): ProductInfo {
        // Find ISBNs
        val isbn10 = volume.industryIdentifiers
            ?.find { it.type == "ISBN_10" }?.identifier
        val isbn13 = volume.industryIdentifiers
            ?.find { it.type == "ISBN_13" }?.identifier
        
        return ProductInfo(
            barcode = barcode,
            source = name,
            category = ProductCategory.BOOK,
            name = volume.title,
            brand = volume.publisher,
            description = volume.description,
            imageUrl = volume.imageLinks?.thumbnail?.replace("http:", "https:"),
            bookData = BookData(
                title = volume.title,
                authors = volume.authors ?: emptyList(),
                publisher = volume.publisher,
                publishedDate = volume.publishedDate,
                pageCount = volume.pageCount,
                categories = volume.categories ?: emptyList(),
                isbn10 = isbn10,
                isbn13 = isbn13,
                previewLink = volume.previewLink,
                infoLink = volume.infoLink,
                language = volume.language
            )
        )
    }
}

// --- DTOs for Google Books API ---

@Serializable
data class GoogleBooksResponse(
    val totalItems: Int = 0,
    val items: List<BookItem>? = null
)

@Serializable
data class BookItem(
    val volumeInfo: VolumeInfo
)

@Serializable
data class VolumeInfo(
    val title: String? = null,
    val authors: List<String>? = null,
    val publisher: String? = null,
    val publishedDate: String? = null,
    val description: String? = null,
    val pageCount: Int? = null,
    val categories: List<String>? = null,
    val imageLinks: ImageLinks? = null,
    val language: String? = null,
    val previewLink: String? = null,
    val infoLink: String? = null,
    val industryIdentifiers: List<IndustryIdentifier>? = null
)

@Serializable
data class ImageLinks(
    val thumbnail: String? = null,
    val smallThumbnail: String? = null
)

@Serializable
data class IndustryIdentifier(
    val type: String,
    val identifier: String
)
