package com.skeler.scanely.core.lookup

import android.util.Log
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LookupOrchestrator"
private const val ENGINE_TIMEOUT_MS = 10_000L

/**
 * Orchestrates product lookups across multiple engines with fallback chaining.
 * 
 * Strategy:
 * 1. Filter engines that support the barcode format
 * 2. Query engines in priority order (lower priority number = higher precedence)
 * 3. Return first Found result, or aggregate NotFound/Errors
 * 4. Retry transient failures with exponential backoff
 */
@Singleton
class LookupOrchestrator @Inject constructor(
    private val engines: Set<@JvmSuppressWildcards LookupEngine>
) {
    private val retryConfig = RetryConfig(
        maxAttempts = 2,
        initialDelayMs = 500,
        maxDelayMs = 2000
    )
    
    /**
     * Look up a barcode across all registered engines.
     * 
     * @param barcode The barcode to look up
     * @return LookupResult from the first engine that finds data, or aggregated failure
     */
    suspend fun lookup(barcode: String): LookupResult {
        val supportingEngines = engines
            .filter { it.supports(barcode) }
            .sortedBy { it.priority }
        
        if (supportingEngines.isEmpty()) {
            Log.d(TAG, "No engines support barcode: $barcode")
            return LookupResult.NotFound("No compatible engines")
        }
        
        Log.d(TAG, "Looking up $barcode with ${supportingEngines.size} engines: " +
                supportingEngines.joinToString { it.name })
        
        val errors = mutableListOf<String>()
        
        // Query engines sequentially in priority order
        for (engine in supportingEngines) {
            Log.d(TAG, "Trying engine: ${engine.name}")
            
            val result = lookupWithRetry(engine, barcode)
            
            when (result) {
                is LookupResult.Found -> {
                    Log.d(TAG, "Found in ${engine.name}: ${result.product.name}")
                    return result
                }
                is LookupResult.NotFound -> {
                    Log.d(TAG, "Not found in ${engine.name}, trying next...")
                    continue
                }
                is LookupResult.Error -> {
                    Log.w(TAG, "Error in ${engine.name}: ${result.exception.message}")
                    errors.add("${engine.name}: ${result.exception.message}")
                    continue
                }
            }
        }
        
        return if (errors.isNotEmpty()) {
            LookupResult.NotFound("Searched ${supportingEngines.size} sources. Errors: ${errors.joinToString("; ")}")
        } else {
            LookupResult.NotFound("Searched ${supportingEngines.size} sources")
        }
    }
    
    /**
     * Execute lookup with timeout and retry.
     */
    private suspend fun lookupWithRetry(engine: LookupEngine, barcode: String): LookupResult {
        return try {
            withRetry(retryConfig) {
                withTimeout(ENGINE_TIMEOUT_MS) {
                    engine.lookup(barcode)
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "${engine.name} timed out after ${ENGINE_TIMEOUT_MS}ms")
            LookupResult.Error(engine.name, Exception("Request timed out"))
        } catch (e: Exception) {
            Log.e(TAG, "${engine.name} failed after retries: ${e.message}")
            LookupResult.Error(engine.name, e)
        }
    }
    
    /**
     * Look up a barcode with parallel queries to all supporting engines.
     * Returns the first successful result.
     * 
     * Use this for faster lookups when network latency is a concern.
     */
    suspend fun lookupParallel(barcode: String): LookupResult = coroutineScope {
        val supportingEngines = engines
            .filter { it.supports(barcode) }
            .sortedBy { it.priority }
        
        if (supportingEngines.isEmpty()) {
            return@coroutineScope LookupResult.NotFound("No compatible engines")
        }
        
        Log.d(TAG, "Parallel lookup of $barcode with ${supportingEngines.size} engines")
        
        val results = supportingEngines.map { engine ->
            async {
                lookupWithRetry(engine, barcode)
            }
        }.awaitAll()
        
        // Return first Found result (respecting priority order)
        results.firstOrNull { it is LookupResult.Found }
            ?: results.firstOrNull { it is LookupResult.NotFound }
            ?: results.firstOrNull()
            ?: LookupResult.NotFound("No results")
    }
    
    /**
     * Get list of all registered engine names for debugging.
     */
    fun getRegisteredEngines(): List<String> = engines.map { it.name }
}
