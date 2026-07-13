package com.skeler.scanely.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skeler.scanely.core.actions.ScanAction
import com.skeler.scanely.core.ocr.OcrResult
import com.skeler.scanely.core.ocr.OcrSource
import com.skeler.scanely.core.ocr.TextBlockData
import com.skeler.scanely.core.scan.UnifiedScanService
import com.skeler.scanely.history.data.HistoryManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UnifiedScanViewModel @Inject constructor(
    private val unifiedScanService: UnifiedScanService,
    private val historyManager: HistoryManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(UnifiedScanUiState())
    val uiState: StateFlow<UnifiedScanUiState> = _uiState.asStateFlow()

    private var currentScanJob: Job? = null

    fun processImage(uri: Uri) {
        currentScanJob?.cancel()
        _uiState.value = UnifiedScanUiState(isLoading = true)

        currentScanJob = viewModelScope.launch {
            val result = unifiedScanService.scanImage(uri)

            val success = result.textResult as? OcrResult.Success
            val extractedText = success?.text

            if (extractedText != null) {
                historyManager.saveItem(extractedText, uri.toString())
            }

            _uiState.value = UnifiedScanUiState(
                isLoading = false,
                extractedText = extractedText,
                textBlocks = success?.blocks.orEmpty(),
                isOfflineOcr = success?.source == OcrSource.PADDLE,
                markdown = success?.markdown,
                barcodeActions = result.barcodeActions,
                textActions = result.textActions,
                hasText = result.hasText,
                hasBarcodes = result.hasBarcodes,
                isEmpty = result.isEmpty
            )
        }
    }

    fun processBarcodeOnly(uri: Uri) {
        currentScanJob?.cancel()
        _uiState.value = UnifiedScanUiState(isLoading = true)

        currentScanJob = viewModelScope.launch {
            val barcodeActions = unifiedScanService.scanBarcodeOnly(uri)

            _uiState.value = UnifiedScanUiState(
                isLoading = false,
                barcodeActions = barcodeActions,
                hasBarcodes = barcodeActions.isNotEmpty(),
                isEmpty = barcodeActions.isEmpty()
            )
        }
    }

    fun updateExtractedText(text: String) {
        // Boxes and structured Markdown describe the scan, not the edit — drop them,
        // and stop offering the Paddle-only export formats they backed.
        _uiState.value = _uiState.value.copy(
            extractedText = text,
            textBlocks = emptyList(),
            markdown = null,
            isOfflineOcr = false
        )
    }

    fun clearResult() {
        currentScanJob?.cancel()
        currentScanJob = null
        _uiState.value = UnifiedScanUiState()
    }
}

data class UnifiedScanUiState(
    val isLoading: Boolean = false,
    val extractedText: String? = null,
    val textBlocks: List<TextBlockData> = emptyList(),
    val isOfflineOcr: Boolean = false,
    val markdown: String? = null,
    val barcodeActions: List<ScanAction> = emptyList(),
    val textActions: List<ScanAction> = emptyList(),
    val hasText: Boolean = false,
    val hasBarcodes: Boolean = false,
    val isEmpty: Boolean = true
)
