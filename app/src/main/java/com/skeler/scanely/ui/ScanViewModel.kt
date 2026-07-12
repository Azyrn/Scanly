package com.skeler.scanely.ui

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skeler.scanely.core.ads.RewardedAdManager
import com.skeler.scanely.core.ai.AiProvider
import com.skeler.scanely.core.ai.GenerativeAiService
import com.skeler.scanely.core.network.NetworkObserver
import com.skeler.scanely.history.data.HistoryManager
import com.skeler.scanely.ui.ratelimit.RateLimitManager
import com.skeler.scanely.ui.ratelimit.RateLimitState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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

data class ScanUiState(
    // Restored history text (no re-extraction).
    val historyText: String? = null
)

// Survives activity ViewModel store so history restore isn't lost on recreation.
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

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val stateHolder: ScanStateHolder,
    private val networkObserver: NetworkObserver,
    private val rateLimitManager: RateLimitManager,
    private val rewardedAdManager: RewardedAdManager,
    private val historyManager: HistoryManager,
    private val generativeAiService: GenerativeAiService
) : ViewModel() {

    val uiState: StateFlow<ScanUiState> = stateHolder.uiState

    private var historyItemId: String? = null

    val isOnline: StateFlow<Boolean> = networkObserver.isOnline
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    val rateLimitState: StateFlow<RateLimitState> = rateLimitManager.rateLimitState
    val showRateLimitSheet: StateFlow<Boolean> = rateLimitManager.showRateLimitSheet

    // Eager for synchronous rate-limit checks; seed with bundled set before settings load.
    private val bundledKeyProviders: StateFlow<Set<AiProvider>> =
        generativeAiService.bundledKeyProviders()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = generativeAiService.bundledCapableProviders
            )

    val isRewardedAdAvailable: StateFlow<Boolean> = rewardedAdManager.isAdAvailable

    init {
        rateLimitManager.restoreState(viewModelScope)
        rewardedAdManager.preload()
    }

    fun showRewardedAdForExtraScan(activity: Activity) {
        rewardedAdManager.show(activity) {
            rateLimitManager.grantExtraScan()
        }
    }

    fun dismissRateLimitSheet() {
        rateLimitManager.dismissRateLimitSheet()
    }

    // Bundled keys only; user keys bypass. Returns false if limited.
    fun triggerAiWithRateLimit(provider: AiProvider, onAllowed: () -> Unit): Boolean {
        if (provider !in bundledKeyProviders.value) {
            onAllowed()
            return true
        }
        return rateLimitManager.triggerWithRateLimit(viewModelScope, onAllowed)
    }

    fun onNewScanSelected() {
        historyItemId = null
        stateHolder.reset()
    }

    fun setHistoryText(text: String, id: String? = null) {
        historyItemId = id
        stateHolder.update {
            it.copy(historyText = text)
        }
    }

    fun updateHistoryText(newText: String) {
        stateHolder.update { it.copy(historyText = newText) }
        val id = historyItemId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                historyManager.updateItemText(id, newText)
            } catch (e: Exception) {
                Log.w(TAG, "History edit persist failed", e)
            }
        }
    }

    fun clearState() {
        historyItemId = null
        stateHolder.reset()
    }

    override fun onCleared() {
        super.onCleared()
        rateLimitManager.cancelCooldown()
    }
}
