package com.skeler.scanely.core.lookup

import android.util.Log
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LookupOrchestrator"
private const val ENGINE_TIMEOUT_MS = 10_000L

@Singleton
class LookupOrchestrator @Inject constructor(
    private val engines: Set<@JvmSuppressWildcards LookupEngine>
) {
    private val retryConfig = RetryConfig(
        maxAttempts = 2,
        initialDelayMs = 500,
        maxDelayMs = 2000
    )
    
    suspend fun lookup(barcode: String): LookupResult = coroutineScope {
        val supportingEngines = engines
            .filter { it.supports(barcode) }
            .sortedBy { it.priority }

        if (supportingEngines.isEmpty()) {
            Log.d(TAG, "No engines support barcode: $barcode")
            return@coroutineScope LookupResult.NotFound("No compatible engines")
        }

        Log.d(TAG, "Looking up $barcode with ${supportingEngines.size} engines: " +
                supportingEngines.joinToString { it.name })

        val jobs = supportingEngines.map { engine ->
            async { lookupWithRetry(engine, barcode) }
        }

        val errors = mutableListOf<String>()

        jobs.forEachIndexed { index, job ->
            when (val result = job.await()) {
                is LookupResult.Found -> {
                    Log.d(TAG, "Found in ${supportingEngines[index].name}: ${result.product.name}")
                    jobs.forEach { it.cancel() }
                    return@coroutineScope result
                }
                is LookupResult.NotFound -> Unit
                is LookupResult.Error ->
                    errors.add("${supportingEngines[index].name}: ${result.exception.message}")
            }
        }

        if (errors.isNotEmpty()) {
            LookupResult.NotFound("Searched ${supportingEngines.size} sources. Errors: ${errors.joinToString("; ")}")
        } else {
            LookupResult.NotFound("Searched ${supportingEngines.size} sources")
        }
    }
    
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
}
