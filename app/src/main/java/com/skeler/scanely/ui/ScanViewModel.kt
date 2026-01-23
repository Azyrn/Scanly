package com.skeler.scanely.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skeler.scanely.core.network.NetworkObserver
import com.skeler.scanely.core.ocr.PdfRendererHelper
import com.skeler.scanely.ui.ratelimit.RateLimitManager
import com.skeler.scanely.ui.ratelimit.RateLimitState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ScanViewModel"

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
 * - Delegates rate limiting to RateLimitManager
 * - StateFlow-based reactive UI
 * - Network awareness for hiding offline-dependent actions
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stateHolder: ScanStateHolder,
    private val networkObserver: NetworkObserver,
    private val pdfRendererHelper: PdfRendererHelper,
    private val rateLimitManager: RateLimitManager
) : ViewModel() {

    val uiState: StateFlow<ScanUiState> = stateHolder.uiState

    // ========== Network State ==========

    val isOnline: StateFlow<Boolean> = networkObserver.isOnline
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    // ========== Rate Limiting (delegated to RateLimitManager) ==========

    val rateLimitState: StateFlow<RateLimitState> = rateLimitManager.rateLimitState
    val showRateLimitSheet: StateFlow<Boolean> = rateLimitManager.showRateLimitSheet
    val isRateLimited: Boolean get() = rateLimitManager.isRateLimited

    private var currentProcessingJob: Job? = null

    init {
        viewModelScope.launch {
            rateLimitManager.restoreState()
        }
    }

    fun dismissRateLimitSheet() {
        rateLimitManager.dismissRateLimitSheet()
    }

    /**
     * Execute an AI request with rate limiting.
     * @param onAllowed Callback executed only if rate limit allows
     * @return true if request was allowed, false if rate limited
     */
    fun triggerAiWithRateLimit(onAllowed: () -> Unit): Boolean {
        return rateLimitManager.triggerWithRateLimit(viewModelScope, onAllowed)
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
                pdfThumbnail = null,
                isProcessing = false,
                progressMessage = "",
                historyText = null
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
                progressMessage = "Opening PDF...",
                historyText = null
            )
        }
        
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
        rateLimitManager.cancelCooldown()
        Log.d(TAG, "ScanViewModel cleared")
    }
}
