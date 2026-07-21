package com.skeler.scanely.core.lookup

import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min

data class RetryConfig(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 500,
    val maxDelayMs: Long = 5000,
    val backoffMultiplier: Double = 2.0
)

suspend fun <T> withRetry(
    config: RetryConfig = RetryConfig(),
    shouldRetry: (Exception) -> Boolean = { it.isRetryable() },
    block: suspend () -> T
): T {
    var currentDelay = config.initialDelayMs

    repeat(config.maxAttempts) { attempt ->
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
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

    // Reachable only with maxAttempts <= 0; the last attempt always rethrows.
    error("Retry failed")
}

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
