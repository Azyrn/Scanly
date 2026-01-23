package com.skeler.scanely.ui.ratelimit

import android.util.Log
import com.skeler.scanely.settings.data.SettingsKeys
import com.skeler.scanely.settings.data.datastore.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RateLimitManager"

/**
 * Rate limit configuration.
 * 2 requests allowed, then 60 seconds cooldown.
 * Example: Extract (1) + Translate (2) = cooldown starts.
 */
private const val MAX_REQUESTS_BEFORE_COOLDOWN = 2
private const val RATE_LIMIT_MS = 60_000L
private const val RATE_LIMIT_SECONDS = 60

/**
 * Rate limiting state for gamified UI feedback.
 *
 * Deep Reasoning (ULTRATHINK):
 * - StateFlow chosen over Handler/MutableState for lifecycle-aware, 
 *   thread-safe emissions that survive configuration changes
 * - Progress as 0.0-1.0 Float allows smooth LinearProgressIndicator animation
 * - Separate from ScanUiState to avoid unnecessary recomposition of unrelated UI
 */
data class RateLimitState(
    /** Remaining seconds until next AI request allowed (60 → 0) */
    val remainingSeconds: Int = 0,
    /** Progress from 0.0 (just started) to 1.0 (ready) */
    val progress: Float = 1.0f,
    /** Whether the "ready" haptic has been triggered */
    val justBecameReady: Boolean = false,
    /** Number of requests made in current window (0-2) */
    val requestCount: Int = 0
)

/**
 * Manages AI rate limiting with persistence across app restarts.
 * 
 * Features:
 * - 2-request rate limiting (Extract + Translate, then cooldown)
 * - StateFlow-based countdown for reactive UI
 * - Haptic feedback trigger on cooldown completion
 * - State persistence via DataStore
 */
@Singleton
class RateLimitManager @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) {
    private val _rateLimitState = MutableStateFlow(RateLimitState())
    val rateLimitState: StateFlow<RateLimitState> = _rateLimitState.asStateFlow()

    private val _showRateLimitSheet = MutableStateFlow(false)
    val showRateLimitSheet: StateFlow<Boolean> = _showRateLimitSheet.asStateFlow()

    private var cooldownStartTimestamp: Long = 0L
    private var cooldownJob: Job? = null

    /**
     * Check if currently rate limited.
     */
    val isRateLimited: Boolean
        get() = _rateLimitState.value.remainingSeconds > 0

    /**
     * Restore rate limit state from DataStore on app startup.
     * If mid-cooldown, resume the timer from where it left off.
     */
    suspend fun restoreState() {
        try {
            val savedTimestamp = settingsDataStore.longFlow(SettingsKeys.LAST_AI_REQUEST_TIMESTAMP).first()
            val savedCount = settingsDataStore.intFlow(SettingsKeys.AI_REQUEST_COUNT).first()

            val now = System.currentTimeMillis()
            val elapsed = now - savedTimestamp

            when {
                // Still in cooldown - resume timer
                savedTimestamp > 0 && elapsed < RATE_LIMIT_MS -> {
                    cooldownStartTimestamp = savedTimestamp
                    val remainingMs = RATE_LIMIT_MS - elapsed
                    Log.d(TAG, "Restored cooldown: ${remainingMs / 1000}s remaining")
                }
                // Cooldown expired - reset state
                savedTimestamp > 0 && elapsed >= RATE_LIMIT_MS -> {
                    persistState(0L, 0)
                    Log.d(TAG, "Cooldown expired, state reset")
                }
                // No active cooldown, but restore request count if valid
                savedCount > 0 && savedCount < MAX_REQUESTS_BEFORE_COOLDOWN -> {
                    _rateLimitState.value = RateLimitState(requestCount = savedCount)
                    Log.d(TAG, "Restored request count: $savedCount")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore rate limit state", e)
        }
    }

    /**
     * Start cooldown timer with a given coroutine scope.
     */
    fun startCooldown(scope: CoroutineScope) {
        cooldownJob?.cancel()
        cooldownStartTimestamp = System.currentTimeMillis()
        persistState(cooldownStartTimestamp, MAX_REQUESTS_BEFORE_COOLDOWN)

        cooldownJob = scope.launch(Dispatchers.Main) {
            var remaining = RATE_LIMIT_SECONDS

            _rateLimitState.value = RateLimitState(
                remainingSeconds = remaining,
                progress = 0f,
                justBecameReady = false,
                requestCount = MAX_REQUESTS_BEFORE_COOLDOWN
            )

            while (remaining > 0) {
                delay(1000L)
                remaining--

                val progress = 1.0f - (remaining.toFloat() / RATE_LIMIT_SECONDS)

                _rateLimitState.value = RateLimitState(
                    remainingSeconds = remaining,
                    progress = progress,
                    justBecameReady = remaining == 0,
                    requestCount = if (remaining == 0) 0 else MAX_REQUESTS_BEFORE_COOLDOWN
                )
            }

            // Reset after cooldown
            delay(100)
            _rateLimitState.value = RateLimitState(
                remainingSeconds = 0,
                progress = 1.0f,
                justBecameReady = false,
                requestCount = 0
            )
            cooldownStartTimestamp = 0L
            persistState(0L, 0)
        }
    }

    /**
     * Execute an AI request with 2-request rate limiting.
     *
     * @param scope CoroutineScope for cooldown timer
     * @param onAllowed Callback executed only if rate limit allows
     * @return true if request was allowed, false if rate limited
     */
    fun triggerWithRateLimit(scope: CoroutineScope, onAllowed: () -> Unit): Boolean {
        val now = System.currentTimeMillis()

        // Check if currently in cooldown
        if (_rateLimitState.value.remainingSeconds > 0) {
            _showRateLimitSheet.value = true
            return false
        }

        // Check if cooldown has expired (reset counter if past cooldown period)
        if (cooldownStartTimestamp > 0 && now - cooldownStartTimestamp >= RATE_LIMIT_MS) {
            cooldownStartTimestamp = 0L
            _rateLimitState.value = RateLimitState(requestCount = 0)
            persistState(0L, 0)
        }

        val currentCount = _rateLimitState.value.requestCount
        val newCount = currentCount + 1

        // Allow the request FIRST
        onAllowed()

        // Then update state
        if (newCount >= MAX_REQUESTS_BEFORE_COOLDOWN) {
            _rateLimitState.value = _rateLimitState.value.copy(requestCount = newCount)
            _showRateLimitSheet.value = true
            startCooldown(scope)
        } else {
            _rateLimitState.value = _rateLimitState.value.copy(requestCount = newCount)
            persistState(0L, newCount)
        }

        return true
    }

    fun dismissRateLimitSheet() {
        _showRateLimitSheet.value = false
    }

    fun cancelCooldown() {
        cooldownJob?.cancel()
    }

    private fun persistState(timestamp: Long, count: Int) {
        // Fire-and-forget persistence
        kotlinx.coroutines.GlobalScope.launch {
            settingsDataStore.setLong(SettingsKeys.LAST_AI_REQUEST_TIMESTAMP, timestamp)
            settingsDataStore.setInt(SettingsKeys.AI_REQUEST_COUNT, count)
        }
    }
}
