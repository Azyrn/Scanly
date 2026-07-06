package com.skeler.scanely.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skeler.scanely.core.ocr.MlKitOcrService
import com.skeler.scanely.core.ocr.OcrResult
import com.skeler.scanely.history.data.HistoryManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OcrUiState(
    val isProcessing: Boolean = false,
    val result: OcrResult? = null
)

/**
 * ViewModel for ML Kit on-device OCR. Saves successful results to history.
 */
@HiltViewModel
class OcrViewModel @Inject constructor(
    private val ocrService: MlKitOcrService,
    private val historyManager: HistoryManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(OcrUiState())
    val uiState: StateFlow<OcrUiState> = _uiState.asStateFlow()

    /** Pending history save for the current result, so edits update it in place.
     * Deferred (not a plain id) so an edit fired before the save completes can
     * await the row id instead of silently missing it. */
    private var savedHistoryId: Deferred<String?>? = null

    fun processPdf(pdfUri: Uri) {
        viewModelScope.launch {
            _uiState.value = OcrUiState(isProcessing = true)

            val result = ocrService.recognizeFromPdf(pdfUri)
            _uiState.value = OcrUiState(isProcessing = false, result = result)

            if (result is OcrResult.Success && result.text.isNotBlank()) {
                saveToHistory(result.text, pdfUri)
            }
        }
    }

    private fun saveToHistory(text: String, uri: Uri) {
        savedHistoryId = viewModelScope.async(Dispatchers.IO) {
            try {
                historyManager.saveItem(text, uri.toString()).id
            } catch (e: Exception) {
                null // Silent fail - don't interrupt user flow
            }
        }
    }

    /**
     * Replace the recognized text with a user correction, updating both the
     * in-memory result and the persisted history row so the corrected version is
     * what gets exported and what re-opens from history later.
     */
    fun updateText(newText: String) {
        val current = _uiState.value.result
        if (current !is OcrResult.Success) return
        _uiState.value = _uiState.value.copy(result = current.copy(text = newText))
        val pendingId = savedHistoryId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val id = pendingId.await() ?: return@launch
                historyManager.updateItemText(id, newText)
            } catch (e: Exception) {
                // Persistence failure shouldn't disrupt the in-memory correction.
            }
        }
    }

    fun clearResult() {
        savedHistoryId = null
        _uiState.value = OcrUiState()
    }
}
