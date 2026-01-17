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
    /** Cached translations: language name -> translated text */
    val translationCache: Map<String, String> = emptyMap(),
    /** Currently displayed language (null = original text) */
    val currentLanguage: String? = null,
    val isTranslating: Boolean = false,
    /** URI of the last processed image (for rescan) */
    val lastImageUri: Uri? = null,
    /** Total number of files being processed (for multi-file) */
    val totalFiles: Int = 0,
    /** Current file index being processed (1-indexed for display) */
    val currentFileIndex: Int = 0,
    /** All URIs being processed (for multi-file) */
    val allUris: List<Uri> = emptyList()
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
     * Process multiple files with the specified AI mode.
     * Results are combined with file separators.
     */
    fun processMultipleFiles(uris: List<Uri>, mode: AiMode) {
        if (uris.isEmpty()) return

        viewModelScope.launch {
            _aiState.value = AiScanState(
                isProcessing = true,
                mode = mode,
                totalFiles = uris.size,
                currentFileIndex = 1,
                allUris = uris,
                lastImageUri = uris.first()
            )

            val allResults = StringBuilder()

            uris.forEachIndexed { index, uri ->
                _aiState.value = _aiState.value.copy(
                    currentFileIndex = index + 1,
                    lastImageUri = uri
                )

                val result = aiService.processImage(uri, mode)

                when (result) {
                    is AiResult.Success -> {
                        if (allResults.isNotEmpty()) {
                            allResults.append("\n\n--- File ${index + 1} ---\n\n")
                        }
                        allResults.append(result.text)
                    }
                    is AiResult.Error -> {
                        if (allResults.isNotEmpty()) {
                            allResults.append("\n\n--- File ${index + 1} (Error) ---\n\n")
                        }
                        allResults.append("Error: ${result.message}")
                    }
                    is AiResult.RateLimited -> {
                        allResults.append("\n\n--- Rate Limited ---\n")
                    }
                }
            }

            val combinedResult = if (allResults.isNotEmpty()) {
                AiResult.Success(allResults.toString())
            } else {
                AiResult.Error("No text extracted from any file")
            }

            _aiState.value = _aiState.value.copy(
                isProcessing = false,
                result = combinedResult,
                originalText = if (combinedResult is AiResult.Success) combinedResult.text else null
            )

            // Save combined result to history (using first file as reference)
            if (combinedResult is AiResult.Success && combinedResult.text.isNotBlank()) {
                saveToHistory(combinedResult.text, uris.first())
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
     * Returns the URI and mode for the caller to trigger with rate limit check.
     */
    fun getRescanParams(): Pair<Uri, AiMode>? {
        val currentState = _aiState.value
        val imageUri = currentState.lastImageUri ?: return null
        val mode = currentState.mode ?: return null
        return Pair(imageUri, mode)
    }

    /**
     * Translate the current result text to a target language.
     * Stores result in cache for instant switching later.
     */
    fun translateResult(targetLanguage: String) {
        val currentText = _aiState.value.originalText ?: return

        viewModelScope.launch {
            _aiState.value = _aiState.value.copy(isTranslating = true)

            val translationResult = aiService.translateText(currentText, targetLanguage)

            when (translationResult) {
                is AiResult.Success -> {
                    // Cache the successful translation
                    val updatedCache = _aiState.value.translationCache + (targetLanguage to translationResult.text)
                    _aiState.value = _aiState.value.copy(
                        isTranslating = false,
                        translationCache = updatedCache,
                        currentLanguage = targetLanguage
                    )
                }
                is AiResult.RateLimited -> {
                    _aiState.value = _aiState.value.copy(isTranslating = false)
                    // Rate limit handled by ScanViewModel - don't update state further
                }
                is AiResult.Error -> {
                    _aiState.value = _aiState.value.copy(isTranslating = false)
                    // Could show error toast via state if needed
                }
            }
        }
    }

    /**
     * Switch to a previously cached translation instantly (no API call).
     */
    fun selectCachedLanguage(language: String) {
        if (_aiState.value.translationCache.containsKey(language)) {
            _aiState.value = _aiState.value.copy(currentLanguage = language)
        }
    }

    /**
     * Show original text (keeps cache intact for quick switching back).
     */
    fun showOriginal() {
        _aiState.value = _aiState.value.copy(currentLanguage = null)
    }

    /**
     * Clear the current AI result.
     */
    fun clearResult() {
        _aiState.value = AiScanState()
    }
}

