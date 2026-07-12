package com.skeler.scanely.core.lookup

sealed interface LookupResult {
    data class Found(
        val product: ProductInfo,
        val source: String
    ) : LookupResult
    
    data class NotFound(val source: String) : LookupResult
    
    data class Error(
        val source: String,
        val exception: Exception
    ) : LookupResult
}

interface LookupEngine {
    val name: String
    /** Lower = higher priority in fallback order. */
    val priority: Int
    val category: ProductCategory
    
    fun supports(barcode: String): Boolean
    
    suspend fun lookup(barcode: String): LookupResult
}
