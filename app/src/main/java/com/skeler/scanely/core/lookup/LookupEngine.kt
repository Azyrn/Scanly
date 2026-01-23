package com.skeler.scanely.core.lookup

/**
 * Result of a product lookup from any engine.
 */
sealed interface LookupResult {
    /**
     * Product was found with complete or partial data.
     */
    data class Found(
        val product: ProductInfo,
        val source: String
    ) : LookupResult
    
    /**
     * Barcode was searched but no product found.
     */
    data class NotFound(val source: String) : LookupResult
    
    /**
     * Lookup failed due to network or other error.
     */
    data class Error(
        val source: String,
        val exception: Exception
    ) : LookupResult
}

/**
 * Base interface for all product lookup engines.
 * 
 * Each engine connects to a specific data source (Open Food Facts, Google Books, etc.)
 * and provides a consistent interface for the LookupOrchestrator to query.
 */
interface LookupEngine {
    /**
     * Human-readable name of this engine (e.g., "Open Food Facts").
     */
    val name: String
    
    /**
     * Priority for fallback ordering. Lower = higher priority.
     */
    val priority: Int
    
    /**
     * The category of products this engine handles.
     */
    val category: ProductCategory
    
    /**
     * Check if this engine can potentially handle the given barcode.
     * 
     * @param barcode The barcode string to check
     * @return true if this engine should be queried for this barcode
     */
    fun supports(barcode: String): Boolean
    
    /**
     * Perform the actual product lookup.
     * 
     * @param barcode The barcode to look up
     * @return LookupResult indicating success, not found, or error
     */
    suspend fun lookup(barcode: String): LookupResult
}
