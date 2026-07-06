package com.skeler.scanely.settings.presentation.viewmodel

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skeler.scanely.core.ai.AiProvider
import com.skeler.scanely.core.ai.KeyVerifier
import com.skeler.scanely.core.ai.VerificationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** UI-facing verification state for a single provider's key field. */
sealed interface VerifyState {
    /** No key entered. */
    data object NotConfigured : VerifyState

    /** A key is present but has not been verified. */
    data object Entered : VerifyState

    /** A validation request is in flight. */
    data object Verifying : VerifyState

    /** The key was accepted by the provider. */
    data object Verified : VerifyState

    /** The provider rejected the key. */
    data class Invalid(val message: String) : VerifyState

    /** Validity couldn't be determined (offline / outage). Retryable. */
    data class Failed(val message: String) : VerifyState
}

/** A cached verification result, pinned to the exact key it was produced for. */
data class VerificationEntry(val key: String, val state: VerifyState)

/**
 * Owns key-verification state for the AI Providers screen. Results are cached
 * per provider against the exact key that produced them; editing the key
 * cancels any in-flight check and drops the cache so the key must be verified
 * again. Verification runs on [viewModelScope] (off the main thread) and rapid
 * taps are debounced.
 */
@HiltViewModel
class ProviderVerificationViewModel @Inject constructor(
    private val verifier: KeyVerifier
) : ViewModel() {

    private val _states = MutableStateFlow<Map<AiProvider, VerificationEntry>>(emptyMap())
    val states: StateFlow<Map<AiProvider, VerificationEntry>> = _states.asStateFlow()

    private val jobs = mutableMapOf<AiProvider, Job>()
    private val lastRequestAt = mutableMapOf<AiProvider, Long>()

    /**
     * Resolve the display state for [provider] given the field's current [key].
     * Pure over ([entry], [key]) so callers recompute reactively from [states].
     */
    fun resolve(entry: VerificationEntry?, key: String): VerifyState {
        val trimmed = key.trim()
        return when {
            trimmed.isEmpty() -> VerifyState.NotConfigured
            entry != null && entry.key == trimmed -> entry.state
            else -> VerifyState.Entered
        }
    }

    fun verify(provider: AiProvider, key: String, customUrl: String? = null) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return

        // Debounce rapid taps.
        val now = SystemClock.elapsedRealtime()
        if (now - (lastRequestAt[provider] ?: 0L) < DEBOUNCE_MS) return
        lastRequestAt[provider] = now

        // Skip if this exact key is already being checked or already settled.
        val current = _states.value[provider]
        if (current?.key == trimmed && current.state !is VerifyState.Failed) return

        jobs[provider]?.cancel()
        put(provider, trimmed, VerifyState.Verifying)
        val job = viewModelScope.launch {
            val state = when (val result = verifier.verify(provider, trimmed, customUrl)) {
                is VerificationResult.Valid -> VerifyState.Verified
                is VerificationResult.Invalid -> VerifyState.Invalid(result.message)
                is VerificationResult.Failed -> VerifyState.Failed(result.message)
            }
            // Ignore late results if the key moved on while we were in flight.
            if (_states.value[provider]?.key == trimmed) {
                put(provider, trimmed, state)
            }
        }
        jobs[provider] = job
        // Drop the entry once done so finished jobs don't accumulate; guard against
        // a newer job having already replaced this one.
        job.invokeOnCompletion { if (jobs[provider] === job) jobs.remove(provider) }
    }

    /** On every key edit: invalidate a stale cache and cancel any running check. */
    fun onKeyChanged(provider: AiProvider, key: String) {
        val trimmed = key.trim()
        if (_states.value[provider]?.key == trimmed) return
        invalidate(provider)
    }

    /** Drop any cached/in-flight verification for [provider] (e.g. its URL changed). */
    fun invalidate(provider: AiProvider) {
        jobs.remove(provider)?.cancel()
        if (_states.value.containsKey(provider)) {
            _states.update { it - provider }
        }
    }

    private fun put(provider: AiProvider, key: String, state: VerifyState) {
        _states.update { it + (provider to VerificationEntry(key, state)) }
    }

    private companion object {
        const val DEBOUNCE_MS = 500L
    }
}
