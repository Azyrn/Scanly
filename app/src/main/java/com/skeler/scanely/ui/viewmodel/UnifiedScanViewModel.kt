package com.skeler.scanely.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skeler.scanely.core.actions.ScanAction
import com.skeler.scanely.core.ocr.OcrResult
import com.skeler.scanely.core.scan.UnifiedScanResult
import com.skeler.scanely.core.scan.UnifiedScanService
import com.skeler.scanely.history.data.HistoryManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for unified gallery scanning.
 * Manages state for combined OCR + barcode detection results.
 * 
 * Handles job cancellation to prevent concurrent scans.
 */
@HiltViewModel
class UnifiedScanViewModel @Inject constructor(
    private val unifiedScanService: UnifiedScanService,
    private val historyManager: HistoryManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(UnifiedScanUiState())
    val uiState: StateFlow<UnifiedScanUiState> = _uiState.asStateFlow()

    private var currentImageUri: Uri? = null
    
    /** Current scan job - cancelled before starting new scan */
    private var currentScanJob: Job? = null

    /**
     * Process an image with unified scanning (OCR + barcode).
     * Cancels any previous ongoing scan before starting.
     */
    fun processImage(uri: Uri) {
        // Cancel previous scan to prevent race conditions
        currentScanJob?.cancel()
        
        currentImageUri = uri
        _uiState.value = UnifiedScanUiState(isLoading = true)

        currentScanJob = viewModelScope.launch {
            val result = unifiedScanService.scanImage(uri)
            
            val extractedText = when (val textResult = result.textResult) {
                is OcrResult.Success -> textResult.text
                else -> null
            }

            // Save to history if text was extracted
            if (extractedText != null) {
                historyManager.saveItem(extractedText, uri.toString())
            }

            _uiState.value = UnifiedScanUiState(
                isLoading = false,
                extractedText = extractedText,
                barcodeActions = result.barcodeActions,
                textActions = result.textActions,
                hasText = result.hasText,
                hasBarcodes = result.hasBarcodes,
                isEmpty = result.isEmpty
            )
        }
    }

    /**
     * Process image for barcode-only detection.
     * Cancels any previous ongoing scan before starting.
     */
    fun processBarcodeOnly(uri: Uri) {
        // Cancel previous scan to prevent race conditions
        currentScanJob?.cancel()
        
        currentImageUri = uri
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

    fun clearResult() {
        currentScanJob?.cancel()
        currentScanJob = null
        _uiState.value = UnifiedScanUiState()
        currentImageUri = null
    }

    fun getImageUri(): Uri? = currentImageUri
}

/**
 * UI state for unified scanning results.
 */
data class UnifiedScanUiState(
    val isLoading: Boolean = false,
    val extractedText: String? = null,
    val barcodeActions: List<ScanAction> = emptyList(),
    val textActions: List<ScanAction> = emptyList(),
    val hasText: Boolean = false,
    val hasBarcodes: Boolean = false,
    val isEmpty: Boolean = true,
    val error: String? = null
)
