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
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GoogleBooksEngine"

// No API key; limited free quota (~1000/day).
@Singleton
class GoogleBooksEngine @Inject constructor(
    private val okHttpClient: OkHttpClient
) : LookupEngine {

    override val name = "Google Books"
    override val priority = 1
    override val category = ProductCategory.BOOK

    override fun supports(barcode: String): Boolean = isIsbn(barcode)

    override suspend fun lookup(barcode: String): LookupResult = withContext(Dispatchers.IO) {
        try {
            val cleaned = barcode.replace("-", "")
            val url = "https://www.googleapis.com/books/v1/volumes?q=isbn:$cleaned"

            Log.d(TAG, "Looking up: $cleaned")

            val request = Request.Builder().url(url).build()
            val body = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext LookupResult.NotFound(name)
                response.body?.string()
            } ?: return@withContext LookupResult.NotFound(name)

            val booksResponse = LookupJson.decodeFromString<GoogleBooksResponse>(body)

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
