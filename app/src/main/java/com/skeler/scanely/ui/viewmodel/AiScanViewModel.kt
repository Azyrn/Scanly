package com.skeler.scanely.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skeler.scanely.core.ai.AiEvent
import com.skeler.scanely.core.ai.AiMode
import com.skeler.scanely.core.ai.AiProvider
import com.skeler.scanely.core.ai.AiResult
import com.skeler.scanely.core.ai.AiStage
import com.skeler.scanely.core.ai.GenerativeAiService
import com.skeler.scanely.history.data.HistoryManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
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
    /** Current pipeline stage while processing (null when idle). */
    val stage: AiStage? = null,
    /** Human-readable stage note, e.g. a retry/fallback message. */
    val stageMessage: String? = null,
    /** Accumulated streamed text while the model is generating. */
    val streamingText: String? = null,
    val result: AiResult? = null,
    val mode: AiMode? = null,
    /** Provider used for this scan (for rescan + translation) */
    val provider: AiProvider = AiProvider.DEFAULT,
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
    val allUris: List<Uri> = emptyList(),
    /** One-shot user-facing message (e.g. a translation failure). */
    val message: String? = null
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

    /** The in-flight processing job, so Cancel can abort it. */
    private var processingJob: Job? = null

    /** Pending history save for the current result, so edits update it in place.
     * Deferred (not a plain id) so an edit fired before the save completes can
     * await the row id instead of silently missing it. */
    private var savedHistoryId: Deferred<String?>? = null

    /**
     * Process an image with the specified AI mode. Progress (stages and
     * streamed text) is surfaced through [aiState] as it happens.
     */
    fun processImage(imageUri: Uri, mode: AiMode, provider: AiProvider) {
        processingJob?.cancel()
        processingJob = viewModelScope.launch {
            _aiState.value = AiScanState(
                isProcessing = true,
                stage = AiStage.PREPARING,
                mode = mode,
                provider = provider,
                lastImageUri = imageUri
            )

            val result = collectPipeline(imageUri, mode, provider)
            publishFinalResult(result, imageUri)
        }
    }

    /** Runs the AI pipeline for one file, mirroring its events into [aiState]. */
    private suspend fun collectPipeline(uri: Uri, mode: AiMode, provider: AiProvider): AiResult {
        var result: AiResult = AiResult.Error("Cancelled")
        aiService.processImageEvents(uri, mode, provider).collect { event ->
            when (event) {
                is AiEvent.Stage -> _aiState.value = _aiState.value.copy(
                    stage = event.stage,
                    stageMessage = event.message
                )
                is AiEvent.Delta -> _aiState.value = _aiState.value.copy(
                    streamingText = event.textSoFar
                )
                is AiEvent.Finished -> result = event.result
            }
        }
        return result
    }

    private fun publishFinalResult(result: AiResult, historyUri: Uri) {
        _aiState.value = _aiState.value.copy(
            isProcessing = false,
            stage = null,
            stageMessage = null,
            streamingText = null,
            result = result,
            originalText = (result as? AiResult.Success)?.text
        )
        if (result is AiResult.Success && result.text.isNotBlank()) {
            saveToHistory(result.text, historyUri)
        }
    }

    /**
     * Cancel the in-flight extraction and return to the idle state.
     */
    fun cancelProcessing() {
        processingJob?.cancel()
        processingJob = null
        _aiState.value = _aiState.value.copy(
            isProcessing = false,
            stage = null,
            stageMessage = null,
            streamingText = null
        )
    }

    /**
     * Process multiple files with the specified AI mode.
     * Results are combined with file separators.
     */
    fun processMultipleFiles(uris: List<Uri>, mode: AiMode, provider: AiProvider) {
        if (uris.isEmpty()) return

        processingJob?.cancel()
        processingJob = viewModelScope.launch {
            _aiState.value = AiScanState(
                isProcessing = true,
                stage = AiStage.PREPARING,
                mode = mode,
                provider = provider,
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

                val result = collectPipeline(uri, mode, provider)
                _aiState.value = _aiState.value.copy(streamingText = null)

                val (label, body) = when (result) {
                    is AiResult.Success -> "" to result.text
                    is AiResult.Error -> " (Error)" to "Error: ${result.message}"
                    is AiResult.RateLimited -> " (Rate limited)" to "Rate limited — try again later."
                }
                if (allResults.isNotEmpty()) {
                    allResults.append("\n\n--- File ${index + 1}$label ---\n\n")
                }
                allResults.append(body)
            }

            val combinedResult = if (allResults.isNotEmpty()) {
                AiResult.Success(allResults.toString())
            } else {
                AiResult.Error("No text extracted from any file")
            }

            // Combined result saves to history keyed on the first file.
            publishFinalResult(combinedResult, uris.first())
        }
    }

    /**
     * Save extraction result to history.
     */
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
     * Replace the extracted text with a user correction. Updates the in-memory
     * result and the persisted history row so the corrected version is what gets
     * exported now and what re-opens from history later.
     */
    fun updateText(newText: String) {
        val state = _aiState.value
        val language = state.currentLanguage
        if (language != null) {
            // Editing a translation edits that cached translation, not the source.
            _aiState.value = state.copy(
                translationCache = state.translationCache + (language to newText)
            )
            return
        }
        _aiState.value = state.copy(
            result = AiResult.Success(newText),
            originalText = newText
        )
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

    /** Consume the one-shot [AiScanState.message] after it has been shown. */
    fun consumeMessage() {
        _aiState.value = _aiState.value.copy(message = null)
    }

    /**
     * Rescan the last processed image with the same mode.
     * Returns the URI and mode for the caller to trigger with rate limit check.
     */
    fun getRescanParams(): Triple<Uri, AiMode, AiProvider>? {
        val currentState = _aiState.value
        val imageUri = currentState.lastImageUri ?: return null
        val mode = currentState.mode ?: return null
        return Triple(imageUri, mode, currentState.provider)
    }

    /**
     * Translate the current result text to a target language.
     * Stores result in cache for instant switching later.
     */
    fun translateResult(targetLanguage: String) {
        val currentText = _aiState.value.originalText ?: return

        viewModelScope.launch {
            _aiState.value = _aiState.value.copy(isTranslating = true)

            val translationResult = aiService.translateText(
                currentText, targetLanguage, _aiState.value.provider
            )

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
                    // Surface the failure instead of silently snapping back to the
                    // original text, which reads as "nothing happened".
                    _aiState.value = _aiState.value.copy(
                        isTranslating = false,
                        message = "Translation failed. Please try again."
                    )
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
        savedHistoryId = null
        _aiState.value = AiScanState()
    }
}

