package com.skeler.scanely.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skeler.scanely.core.network.NetworkObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import com.skeler.scanely.core.ocr.PdfRendererHelper

private const val TAG = "ScanViewModel"

/**
 * Rate limit configuration.
 * 2 requests allowed, then 60 seconds cooldown.
 * Example: Extract (1) + Translate (2) = cooldown starts.
 */
private const val MAX_REQUESTS_BEFORE_COOLDOWN = 2
private const val RATE_LIMIT_MS = 60_000L
private const val RATE_LIMIT_SECONDS = 60

/**
 * UI State for scanning operations.
 */
data class ScanUiState(
    val currentLanguages: List<String> = emptyList(),
    val isProcessing: Boolean = false,
    val progressMessage: String = "",
    val progressPercent: Float = 0f,
    val selectedImageUri: Uri? = null,
    val pdfThumbnail: Bitmap? = null,
    val error: String? = null,
    /** Text restored from history (no re-extraction needed) */
    val historyText: String? = null
)

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

@Singleton
class ScanStateHolder @Inject constructor() {
    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    fun update(transform: (ScanUiState) -> ScanUiState) {
        _uiState.update(transform)
    }

    fun reset() {
        _uiState.value = ScanUiState()
    }
}

/**
 * ViewModel for scanning operations.
 *
 * Features:
 * - 2-request rate limiting (Extract + Translate, then cooldown)
 * - StateFlow-based countdown for reactive UI
 * - Haptic feedback trigger on cooldown completion
 * - Network awareness for hiding offline-dependent actions
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stateHolder: ScanStateHolder,
    private val networkObserver: NetworkObserver,
    private val pdfRendererHelper: PdfRendererHelper
) : ViewModel() {

    val uiState: StateFlow<ScanUiState> = stateHolder.uiState

    // ========== Network State ==========

    /**
     * Reactive online state from NetworkObserver.
     * UI should hide Translate button when false.
     */
    val isOnline: StateFlow<Boolean> = networkObserver.isOnline
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true // Optimistic default
        )

    // ========== Gamified Rate Limiting State ==========

    private val _rateLimitState = MutableStateFlow(RateLimitState())
    val rateLimitState: StateFlow<RateLimitState> = _rateLimitState.asStateFlow()

    /**
     * Controls visibility of RateLimitSheet modal.
     */
    private val _showRateLimitSheet = MutableStateFlow(false)
    val showRateLimitSheet: StateFlow<Boolean> = _showRateLimitSheet.asStateFlow()

    /**
     * Timestamp when cooldown started.
     */
    private var cooldownStartTimestamp: Long = 0L

    /**
     * Active countdown coroutine job.
     */
    private var cooldownJob: Job? = null

    private var currentProcessingJob: Job? = null

    // ========== Rate Limit Sheet Control ==========

    fun dismissRateLimitSheet() {
        _showRateLimitSheet.value = false
    }

    // ========== Rate Limited Request Trigger ==========

    /**
     * Execute an AI request with 2-request rate limiting.
     *
     * FIXED Logic:
     * - 2 FREE requests allowed (extract + translate)
     * - AFTER 2nd request completes, start 60s cooldown
     * - During cooldown, block all requests
     *
     * @param onAllowed Callback executed only if rate limit allows
     * @return true if request was allowed, false if rate limited
     */
    fun triggerAiWithRateLimit(onAllowed: () -> Unit): Boolean {
        val now = System.currentTimeMillis()

        // Check if currently in cooldown
        if (_rateLimitState.value.remainingSeconds > 0) {
            _showRateLimitSheet.value = true
            return false
        }

        // Check if cooldown has expired (reset counter if past cooldown period)
        if (cooldownStartTimestamp > 0 && now - cooldownStartTimestamp >= RATE_LIMIT_MS) {
            // Cooldown expired, reset
            cooldownStartTimestamp = 0L
            _rateLimitState.value = RateLimitState(requestCount = 0)
        }

        // Read current count AFTER potential reset
        val currentCount = _rateLimitState.value.requestCount
        val newCount = currentCount + 1

        // Allow the request FIRST
        onAllowed()

        // Then update state
        if (newCount >= MAX_REQUESTS_BEFORE_COOLDOWN) {
            // Hit the limit, start cooldown AFTER the request
            cooldownStartTimestamp = now
            _rateLimitState.value = _rateLimitState.value.copy(requestCount = newCount)
            _showRateLimitSheet.value = true
            startCooldown()
        } else {
            // Still under limit, just increment
            _rateLimitState.value = _rateLimitState.value.copy(requestCount = newCount)
        }

        return true
    }

    /**
     * Check if currently rate limited.
     */
    val isRateLimited: Boolean
        get() = _rateLimitState.value.remainingSeconds > 0

    /**
     * Start countdown from full cooldown period.
     *
     * Deep Reasoning (ULTRATHINK):
     * - viewModelScope ensures automatic cancellation on ViewModel clear
     * - Dispatchers.Main for UI-safe state emissions
     * - Progress = 1.0 - (remaining / total) for filling animation
     * - justBecameReady flag triggers haptic exactly once
     * - Reset requestCount to 0 after cooldown completes
     */
    private fun startCooldown() {
        cooldownJob?.cancel()

        cooldownJob = viewModelScope.launch(Dispatchers.Main) {
            var remaining = RATE_LIMIT_SECONDS

            // Initial state
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
        }
    }

    // ========== Image/PDF Selection ==========

    fun updateLanguages(languages: Set<String>) {
        if (languages.isEmpty()) return
        stateHolder.update {
            it.copy(currentLanguages = languages.toList())
        }
    }

    fun onImageSelected(uri: Uri) {
        currentProcessingJob?.cancel()
        stateHolder.update {
            it.copy(
                selectedImageUri = uri,
                isProcessing = false,
                progressMessage = "",
                historyText = null // Clear any previous history text
            )
        }
    }

    /**
     * Set text from history (skips re-extraction).
     */
    fun setHistoryText(text: String) {
        stateHolder.update {
            it.copy(historyText = text)
        }
    }

    fun onPdfSelected(uri: Uri) {
        currentProcessingJob?.cancel()
        stateHolder.update {
            it.copy(
                selectedImageUri = uri,
                isProcessing = true,
                progressMessage = "Opening PDF..."
            )
        }
        
        // Generate thumbnail from first page
        viewModelScope.launch {
            val thumbnail = pdfRendererHelper.renderPage(uri, 0)
            stateHolder.update {
                it.copy(
                    pdfThumbnail = thumbnail,
                    isProcessing = false
                )
            }
        }
    }

    fun cancelProcessing() {
        currentProcessingJob?.cancel()
        stateHolder.update {
            it.copy(
                isProcessing = false,
                progressMessage = "",
                error = "Processing cancelled"
            )
        }
    }

    fun clearState() {
        cancelProcessing()
        stateHolder.reset()
    }

    override fun onCleared() {
        super.onCleared()
        cancelProcessing()
        cooldownJob?.cancel()
        Log.d(TAG, "ScanViewModel cleared")
    }
}
