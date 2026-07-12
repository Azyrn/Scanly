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

sealed interface VerifyState {
    data object NotConfigured : VerifyState

    data object Entered : VerifyState

    data object Verifying : VerifyState

    data object Verified : VerifyState

    data class Invalid(val message: String) : VerifyState

    data class Failed(val message: String) : VerifyState
}

data class VerificationEntry(val key: String, val state: VerifyState)

@HiltViewModel
class ProviderVerificationViewModel @Inject constructor(
    private val verifier: KeyVerifier
) : ViewModel() {

    private val _states = MutableStateFlow<Map<AiProvider, VerificationEntry>>(emptyMap())
    val states: StateFlow<Map<AiProvider, VerificationEntry>> = _states.asStateFlow()

    private val jobs = mutableMapOf<AiProvider, Job>()
    private val lastRequestAt = mutableMapOf<AiProvider, Long>()

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

        val now = SystemClock.elapsedRealtime()
        if (now - (lastRequestAt[provider] ?: 0L) < DEBOUNCE_MS) return
        lastRequestAt[provider] = now

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
            if (_states.value[provider]?.key == trimmed) {
                put(provider, trimmed, state)
            }
        }
        jobs[provider] = job
        job.invokeOnCompletion { if (jobs[provider] === job) jobs.remove(provider) }
    }

    /** On every key edit: invalidate a stale cache and cancel any running check. */
    fun onKeyChanged(provider: AiProvider, key: String) {
        val trimmed = key.trim()
        if (_states.value[provider]?.key == trimmed) return
        invalidate(provider)
    }

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
