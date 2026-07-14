package com.skeler.scanely.ui.ratelimit

import android.util.Log
import com.skeler.scanely.settings.data.SettingsKeys
import com.skeler.scanely.settings.data.datastore.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RateLimitManager"

private const val MAX_REQUESTS_BEFORE_COOLDOWN = 5
private const val RATE_LIMIT_MS = 300_000L
const val RATE_LIMIT_SECONDS = 300

fun formatCountdown(seconds: Int): String =
    if (seconds >= 60) "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}" else "${seconds}s"

// Kept out of ScanUiState so countdown ticks don't recompose unrelated UI.
data class RateLimitState(
    val remainingSeconds: Int = 0,
    val progress: Float = 1.0f,
    val justBecameReady: Boolean = false,
    val requestCount: Int = 0
)

// Cooldown persists across restarts via DataStore.
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

    // One failed write must not cancel later persistence.
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun restoreState(scope: CoroutineScope) {
        scope.launch {
            try {
                val savedTimestamp =
                    settingsDataStore.longFlow(SettingsKeys.LAST_AI_REQUEST_TIMESTAMP).first()
                val savedCount = settingsDataStore.intFlow(SettingsKeys.AI_REQUEST_COUNT).first()

                val elapsed = System.currentTimeMillis() - savedTimestamp

                when {
                    savedTimestamp > 0 && elapsed < RATE_LIMIT_MS -> {
                        cooldownStartTimestamp = savedTimestamp
                        val remaining = ((RATE_LIMIT_MS - elapsed) / 1000L).toInt().coerceAtLeast(1)
                        runCooldown(scope, remaining)
                        Log.d(TAG, "Restored cooldown: ${remaining}s remaining")
                    }
                    savedTimestamp > 0 -> {
                        persistState(0L, 0)
                        Log.d(TAG, "Cooldown expired, state reset")
                    }
                    savedCount in 1 until MAX_REQUESTS_BEFORE_COOLDOWN -> {
                        _rateLimitState.value = RateLimitState(requestCount = savedCount)
                        Log.d(TAG, "Restored request count: $savedCount")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore rate limit state", e)
            }
        }
    }

    fun startCooldown(scope: CoroutineScope) {
        cooldownStartTimestamp = System.currentTimeMillis()
        persistState(cooldownStartTimestamp, MAX_REQUESTS_BEFORE_COOLDOWN)
        runCooldown(scope, RATE_LIMIT_SECONDS)
    }

    private fun runCooldown(scope: CoroutineScope, startRemaining: Int) {
        cooldownJob?.cancel()
        cooldownJob = scope.launch(Dispatchers.Main) {
            var remaining = startRemaining

            _rateLimitState.value = RateLimitState(
                remainingSeconds = remaining,
                progress = 1.0f - (remaining.toFloat() / RATE_LIMIT_SECONDS),
                justBecameReady = false,
                requestCount = MAX_REQUESTS_BEFORE_COOLDOWN
            )

            while (remaining > 0) {
                delay(1000L)
                remaining--

                _rateLimitState.value = RateLimitState(
                    remainingSeconds = remaining,
                    progress = 1.0f - (remaining.toFloat() / RATE_LIMIT_SECONDS),
                    justBecameReady = remaining == 0,
                    requestCount = if (remaining == 0) 0 else MAX_REQUESTS_BEFORE_COOLDOWN
                )
            }

            delay(100)
            _rateLimitState.value = RateLimitState(
                remainingSeconds = 0,
                progress = 1.0f,
                justBecameReady = false,
                requestCount = 0
            )
            _showRateLimitSheet.value = false
            cooldownStartTimestamp = 0L
            persistState(0L, 0)
        }
    }

    fun triggerWithRateLimit(scope: CoroutineScope, onAllowed: () -> Unit): Boolean {
        if (_rateLimitState.value.remainingSeconds > 0) {
            _showRateLimitSheet.value = true
            return false
        }

        if (cooldownStartTimestamp > 0 &&
            System.currentTimeMillis() - cooldownStartTimestamp >= RATE_LIMIT_MS
        ) {
            cooldownStartTimestamp = 0L
            _rateLimitState.value = RateLimitState(requestCount = 0)
            persistState(0L, 0)
        }

        val newCount = _rateLimitState.value.requestCount + 1

        onAllowed()

        _rateLimitState.value = _rateLimitState.value.copy(requestCount = newCount)
        if (newCount >= MAX_REQUESTS_BEFORE_COOLDOWN) {
            _showRateLimitSheet.value = true
            startCooldown(scope)
        } else {
            persistState(0L, newCount)
        }

        return true
    }

    fun dismissRateLimitSheet() {
        _showRateLimitSheet.value = false
    }

    fun grantExtraScan() {
        cooldownJob?.cancel()
        cooldownStartTimestamp = 0L
        _showRateLimitSheet.value = false
        _rateLimitState.value = RateLimitState(
            remainingSeconds = 0,
            progress = 1.0f,
            justBecameReady = true,
            requestCount = MAX_REQUESTS_BEFORE_COOLDOWN - 1
        )
        persistState(0L, MAX_REQUESTS_BEFORE_COOLDOWN - 1)
    }

    fun cancelCooldown() {
        cooldownJob?.cancel()
    }

    private fun persistState(timestamp: Long, count: Int) {
        persistenceScope.launch {
            settingsDataStore.setLong(SettingsKeys.LAST_AI_REQUEST_TIMESTAMP, timestamp)
            settingsDataStore.setInt(SettingsKeys.AI_REQUEST_COUNT, count)
        }
    }
}
