package com.skeler.scanely.ui.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skeler.scanely.core.ocr.OcrResult
import com.skeler.scanely.core.ocr.TextOcrService
import com.skeler.scanely.history.data.HistoryManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "OcrViewModel"

data class OcrUiState(
    val isProcessing: Boolean = false,
    val result: OcrResult? = null
)

@HiltViewModel
class OcrViewModel @Inject constructor(
    private val ocrService: TextOcrService,
    private val historyManager: HistoryManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(OcrUiState())
    val uiState: StateFlow<OcrUiState> = _uiState.asStateFlow()

    // Deferred so an edit before save completes can await the row id.
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "History save failed", e)
                null
            }
        }
    }

    fun updateText(newText: String) {
        val current = _uiState.value.result
        if (current !is OcrResult.Success) return
        // Boxes and structured Markdown describe the scan, not the edit — drop them.
        _uiState.value = _uiState.value.copy(
            result = current.copy(text = newText, blocks = emptyList(), markdown = null)
        )
        val pendingId = savedHistoryId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val id = pendingId.await() ?: return@launch
                historyManager.updateItemText(id, newText)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Keep in-memory correction if persist fails.
                Log.w(TAG, "History text update failed", e)
            }
        }
    }

    fun clearResult() {
        savedHistoryId = null
        _uiState.value = OcrUiState()
    }
}
