package com.skeler.scanely.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skeler.scanely.core.ai.AiMode
import com.skeler.scanely.core.ai.AiResult
import com.skeler.scanely.core.ai.GenerativeAiService
import com.skeler.scanely.history.data.HistoryManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State for AI scanning operations.
 */
data class AiScanState(
    val isProcessing: Boolean = false,
    val result: AiResult? = null,
    val mode: AiMode? = null,
    val originalText: String? = null,
    val translatedText: String? = null,
    val isTranslating: Boolean = false,
    /** URI of the last processed image (for rescan) */
    val lastImageUri: Uri? = null
)

/**
 * ViewModel for AI-powered image analysis.
 * Saves successful results to history.
 */
@HiltViewModel
class AiScanViewModel @Inject constructor(
    private val aiService: GenerativeAiService,
    private val historyManager: HistoryManager
) : ViewModel() {

    private val _aiState = MutableStateFlow(AiScanState())
    val aiState: StateFlow<AiScanState> = _aiState.asStateFlow()

    /**
     * Process an image with the specified AI mode.
     */
    fun processImage(imageUri: Uri, mode: AiMode) {
        viewModelScope.launch {
            _aiState.value = AiScanState(
                isProcessing = true,
                mode = mode,
                lastImageUri = imageUri
            )

            val result = aiService.processImage(imageUri, mode)
            val originalText = if (result is AiResult.Success) result.text else null

            _aiState.value = _aiState.value.copy(
                isProcessing = false,
                result = result,
                originalText = originalText
            )

            // Save to history on success
            if (result is AiResult.Success && result.text.isNotBlank()) {
                saveToHistory(result.text, imageUri)
            }
        }
    }

    /**
     * Save extraction result to history.
     */
    private fun saveToHistory(text: String, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                historyManager.saveItem(text, uri.toString())
            } catch (e: Exception) {
                // Silent fail - don't interrupt user flow
            }
        }
    }

    /**
     * Rescan the last processed image with the same mode.
     */
    fun rescan() {
        val currentState = _aiState.value
        val imageUri = currentState.lastImageUri ?: return
        val mode = currentState.mode ?: return

        processImage(imageUri, mode)
    }

    /**
     * Translate the current result text to a target language.
     */
    fun translateResult(targetLanguage: String) {
        val currentText = _aiState.value.originalText ?: return

        viewModelScope.launch {
            _aiState.value = _aiState.value.copy(isTranslating = true)

            val translationResult = aiService.translateText(currentText, targetLanguage)

            _aiState.value = _aiState.value.copy(
                isTranslating = false,
                translatedText = when (translationResult) {
                    is AiResult.Success -> translationResult.text
                    is AiResult.RateLimited -> "Rate limited: wait ${translationResult.remainingMs / 1000}s"
                    is AiResult.Error -> "Translation error: ${translationResult.message}"
                }
            )
        }
    }

    /**
     * Clear translation to show original text again.
     */
    fun clearTranslation() {
        _aiState.value = _aiState.value.copy(translatedText = null)
    }

    /**
     * Clear the current AI result.
     */
    fun clearResult() {
        _aiState.value = AiScanState()
    }
}
