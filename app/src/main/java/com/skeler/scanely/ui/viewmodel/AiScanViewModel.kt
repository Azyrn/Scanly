package com.skeler.scanely.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skeler.scanely.core.ai.AiEvent
import com.skeler.scanely.core.ai.AiMode
import com.skeler.scanely.core.ai.AiProvider
import com.skeler.scanely.core.ai.AiResult
import com.skeler.scanely.core.ai.AiRunInfo
import com.skeler.scanely.core.ai.AiStage
import com.skeler.scanely.core.ai.GenerativeAiService
import com.skeler.scanely.history.data.HistoryManager
import com.skeler.scanely.settings.data.SettingsKeys
import com.skeler.scanely.settings.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiScanState(
    val isProcessing: Boolean = false,
    val stage: AiStage? = null,
    val stageMessage: String? = null,
    val streamingText: String? = null,
    val result: AiResult? = null,
    val mode: AiMode? = null,
    val provider: AiProvider = AiProvider.DEFAULT,
    val runInfo: AiRunInfo? = null,
    val originalText: String? = null,
    val translationCache: Map<String, String> = emptyMap(),
        val currentLanguage: String? = null,
    val isTranslating: Boolean = false,
    val lastImageUri: Uri? = null,
    val totalFiles: Int = 0,
    val currentFileIndex: Int = 0,
    val allUris: List<Uri> = emptyList(),
    val message: String? = null
)

@HiltViewModel
class AiScanViewModel @Inject constructor(
    private val aiService: GenerativeAiService,
    private val historyManager: HistoryManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _aiState = MutableStateFlow(AiScanState())
    val aiState: StateFlow<AiScanState> = _aiState.asStateFlow()

    val selectedProvider: StateFlow<AiProvider> =
        settingsRepository.getString(SettingsKeys.SELECTED_AI_PROVIDER)
            .map { AiProvider.fromName(it.ifBlank { null }) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiProvider.DEFAULT)

    fun setSelectedProvider(provider: AiProvider) {
        viewModelScope.launch {
            settingsRepository.setString(SettingsKeys.SELECTED_AI_PROVIDER, provider.name)
        }
    }

    private var processingJob: Job? = null

    /** Deferred so an edit before save completes can await the row id. */
    private var savedHistoryId: Deferred<String?>? = null

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
                is AiEvent.Finished -> {
                    result = event.result
                    event.runInfo?.let {
                        _aiState.value = _aiState.value.copy(runInfo = it)
                    }
                }
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

            publishFinalResult(combinedResult, uris.first())
        }
    }

    private fun saveToHistory(text: String, uri: Uri) {
        savedHistoryId = viewModelScope.async(Dispatchers.IO) {
            try {
                historyManager.saveItem(text, uri.toString()).id
            } catch (e: Exception) {
                null
            }
        }
    }

    fun updateText(newText: String) {
        val state = _aiState.value
        val language = state.currentLanguage
        if (language != null) {
            // Edit cached translation, not source.
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
                // Keep in-memory correction if persist fails.
            }
        }
    }

    fun consumeMessage() {
        _aiState.value = _aiState.value.copy(message = null)
    }

    fun getRescanParams(): Triple<Uri, AiMode, AiProvider>? {
        val currentState = _aiState.value
        val imageUri = currentState.lastImageUri ?: return null
        val mode = currentState.mode ?: return null
        return Triple(imageUri, mode, currentState.provider)
    }

    fun translateResult(targetLanguage: String) {
        val currentText = _aiState.value.originalText ?: return

        viewModelScope.launch {
            _aiState.value = _aiState.value.copy(isTranslating = true)

            val translationResult = aiService.translateText(
                currentText, targetLanguage, _aiState.value.provider
            )

            when (translationResult) {
                is AiResult.Success -> {
                    val updatedCache = _aiState.value.translationCache + (targetLanguage to translationResult.text)
                    _aiState.value = _aiState.value.copy(
                        isTranslating = false,
                        translationCache = updatedCache,
                        currentLanguage = targetLanguage
                    )
                }
                is AiResult.Error -> {
                    // Surface failure; silent snap-back looks like a no-op.
                    _aiState.value = _aiState.value.copy(
                        isTranslating = false,
                        message = "Translation failed. Please try again."
                    )
                }
            }
        }
    }

    fun selectCachedLanguage(language: String) {
        if (_aiState.value.translationCache.containsKey(language)) {
            _aiState.value = _aiState.value.copy(currentLanguage = language)
        }
    }

    fun showOriginal() {
        _aiState.value = _aiState.value.copy(currentLanguage = null)
    }

    fun clearResult() {
        savedHistoryId = null
        _aiState.value = AiScanState()
    }
}
