package com.skeler.scanely.core.lookup

import java.net.SocketTimeoutException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class RetryUtilsTest {
    @Test
    fun `cancellation propagates without retrying`() = runBlocking {
        var callCount = 0

        try {
            withRetry {
                callCount++
                throw CancellationException("connection reset")
            }
            fail("Expected CancellationException")
        } catch (_: CancellationException) {
            assertEquals(1, callCount)
        }
    }

    @Test
    fun `socket timeout retries up to max attempts`() = runBlocking {
        var callCount = 0
        val config = RetryConfig(maxAttempts = 3, initialDelayMs = 0, maxDelayMs = 0)

        try {
            withRetry(config) {
                callCount++
                throw SocketTimeoutException("timeout")
            }
            fail("Expected SocketTimeoutException")
        } catch (_: SocketTimeoutException) {
            assertEquals(config.maxAttempts, callCount)
        }
    }
}
