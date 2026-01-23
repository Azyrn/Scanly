package com.skeler.scanely.core.lookup

import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.pow

/**
 * Retry configuration for network operations.
 */
data class RetryConfig(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 500,
    val maxDelayMs: Long = 5000,
    val backoffMultiplier: Double = 2.0
)

/**
 * Execute a suspending block with exponential backoff retry.
 * 
 * @param config Retry configuration
 * @param shouldRetry Predicate to determine if exception is retryable
 * @param block The suspending block to execute
 * @return Result of the block
 * @throws Exception if all retries exhausted
 */
suspend fun <T> withRetry(
    config: RetryConfig = RetryConfig(),
    shouldRetry: (Exception) -> Boolean = { it.isRetryable() },
    block: suspend () -> T
): T {
    var lastException: Exception? = null
    var currentDelay = config.initialDelayMs
    
    repeat(config.maxAttempts) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            lastException = e
            
            if (!shouldRetry(e) || attempt == config.maxAttempts - 1) {
                throw e
            }
            
            delay(currentDelay)
            currentDelay = min(
                (currentDelay * config.backoffMultiplier).toLong(),
                config.maxDelayMs
            )
        }
    }
    
    throw lastException ?: IllegalStateException("Retry failed")
}

/**
 * Check if an exception is likely transient and worth retrying.
 */
fun Exception.isRetryable(): Boolean {
    val message = message?.lowercase() ?: ""
    return when {
        this is java.net.SocketTimeoutException -> true
        this is java.net.UnknownHostException -> true
        this is java.net.ConnectException -> true
        this is java.io.InterruptedIOException -> true
        message.contains("timeout") -> true
        message.contains("connection") -> true
        message.contains("reset") -> true
        message.contains("refused") -> true
        else -> false
    }
}
