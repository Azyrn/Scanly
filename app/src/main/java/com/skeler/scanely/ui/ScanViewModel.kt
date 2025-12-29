package com.skeler.scanely.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.skeler.scanely.data.HistoryManager
import com.skeler.scanely.ocr.OcrEngine
import com.skeler.scanely.ocr.OcrResult
import com.skeler.scanely.ocr.PdfProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "ScanViewModel"

/**
 * UI State for scanning operations.
 * Single source of truth for all OCR-related UI state.
 */
data class ScanUiState(
    val isProcessing: Boolean = false,
    val progressMessage: String = "",
    val progressPercent: Float = 0f,
    val selectedImageUri: Uri? = null,
    val pdfThumbnail: Bitmap? = null,
    val ocrResult: OcrResult? = null,
    val error: String? = null
)

/**
 * ViewModel for OCR scanning operations.
 * 
 * Features:
 * - MVVM with StateFlow
 * - Proper resource cleanup in onCleared()
 * - Cancellable processing jobs
 * - Single source of truth for preview state
 */
class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private val ocrEngine = OcrEngine(application.applicationContext)
    private val historyManager = HistoryManager(application.applicationContext)

    // Current settings (can be updated from UI)
    private var currentLanguages: List<String> = listOf("eng")
    
    // Track active processing job for cancellation
    private var currentProcessingJob: Job? = null

    /**
     * Update OCR languages and reinitialize engine.
     */
    fun updateLanguages(languages: Set<String>) {
        if (languages.isEmpty()) return
        
        currentLanguages = languages.toList()
        viewModelScope.launch(Dispatchers.IO) {
            ocrEngine.initialize(currentLanguages)
        }
    }

    /**
     * Called when a generic image (Camera or Gallery) is selected.
     * Clears any previous PDF state to fix the preview bug.
     */
    fun onImageSelected(uri: Uri) {
        // Cancel any ongoing processing
        currentProcessingJob?.cancel()
        
        // Reset state completely for new image
        _uiState.update { 
            ScanUiState(
                selectedImageUri = uri,
                isProcessing = true,
                progressMessage = "Processing image..."
            )
        }
        
        currentProcessingJob = processImage(uri)
    }

    /**
     * Called when a PDF is selected.
     */
    fun onPdfSelected(uri: Uri) {
        // Cancel any ongoing processing
        currentProcessingJob?.cancel()
        
        _uiState.update { 
            ScanUiState(
                selectedImageUri = uri,
                isProcessing = true,
                progressMessage = "Initializing PDF Processor..."
            )
        }
        
        currentProcessingJob = processPdf(uri)
    }

    /**
     * Called when a Barcode is scanned (usually directly from Camera).
     */
    fun onBarcodeScanned(result: OcrResult) {
        _uiState.update { 
            it.copy(
                ocrResult = result,
                isProcessing = false
            )
        }
        
        // Save barcode to history
        if (result.text.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                historyManager.saveItem(result.text, "barcode")
            }
        }
    }
    
    /**
     * Handles processing of a single image for Text OCR.
     */
    private fun processImage(uri: Uri): Job {
        return viewModelScope.launch(Dispatchers.IO) {
            try {
                // Ensure initialized
                if (!ocrEngine.isReady()) {
                    _uiState.update { it.copy(progressMessage = "Initializing OCR...") }
                    ocrEngine.initialize(currentLanguages)
                }

                _uiState.update { it.copy(progressMessage = "Extracting text...") }
                
                val result = ocrEngine.recognizeText(uri)
                
                if (result != null) {
                    _uiState.update { 
                        it.copy(
                            isProcessing = false,
                            ocrResult = result,
                            progressMessage = "",
                            error = null
                        )
                    }
                    
                    // Auto-save to history
                    if (result.text.isNotEmpty()) {
                        historyManager.saveItem(result.text, uri.toString())
                    }
                    
                    Log.d(TAG, "Image OCR completed: ${result.text.length} chars, ${result.confidence}% confidence")
                } else {
                    _uiState.update { 
                        it.copy(
                            isProcessing = false, 
                            error = "Failed to recognize text. Try adjusting the image."
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Image processing error", e)
                _uiState.update { 
                    it.copy(
                        isProcessing = false, 
                        error = e.message ?: "Unknown error"
                    )
                }
            }
        }
    }

    /**
     * Handles processing of PDF documents.
     */
    private fun processPdf(uri: Uri): Job {
        return viewModelScope.launch(Dispatchers.IO) {
            try {
                val tesseractHelper = ocrEngine.getTesseractHelper()
                
                // Ensure initialized
                if (!tesseractHelper.isReady()) {
                    _uiState.update { it.copy(progressMessage = "Initializing OCR...") }
                    tesseractHelper.initialize(currentLanguages)
                }

                val pdfResult = PdfProcessor.extractTextFromPdf(
                    context = getApplication(),
                    pdfUri = uri,
                    ocrHelper = tesseractHelper,
                    enabledLanguages = currentLanguages,
                    onProgress = { update ->
                        val percent = if (update.totalPages > 0) {
                            update.currentPage.toFloat() / update.totalPages
                        } else 0f
                        
                        _uiState.update {
                            it.copy(
                                progressMessage = update.statusMessage,
                                progressPercent = percent
                            )
                        }
                    }
                )
                
                // Create OcrResult wrapper for UI
                val finalResult = OcrResult(
                    text = pdfResult.text,
                    confidence = pdfResult.averageConfidence,
                    languages = listOf(pdfResult.detectedLanguage),
                    processingTimeMs = 0
                )
                
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        pdfThumbnail = pdfResult.thumbnail,
                        ocrResult = finalResult,
                        progressMessage = "",
                        progressPercent = 1f,
                        error = null
                    )
                }
                
                if (pdfResult.text.isNotEmpty()) {
                    historyManager.saveItem(pdfResult.text, uri.toString())
                }
                
                Log.d(TAG, "PDF OCR completed: ${pdfResult.pageCount} pages, ${pdfResult.averageConfidence}% avg confidence")

            } catch (e: Exception) {
                Log.e(TAG, "PDF processing error", e)
                _uiState.update { 
                    it.copy(
                        isProcessing = false, 
                        error = e.message ?: "PDF Error"
                    )
                }
            }
        }
    }
    
    /**
     * Clear state when leaving results screen or starting new scan.
     */
    fun clearState() {
        currentProcessingJob?.cancel()
        _uiState.update { ScanUiState() }
    }
    
    /**
     * Cancel any ongoing processing.
     */
    fun cancelProcessing() {
        currentProcessingJob?.cancel()
        _uiState.update { 
            it.copy(
                isProcessing = false,
                progressMessage = "",
                error = "Processing cancelled"
            )
        }
    }
    
    /**
     * Clean up resources when ViewModel is destroyed.
     */
    override fun onCleared() {
        super.onCleared()
        currentProcessingJob?.cancel()
        ocrEngine.release()
        Log.d(TAG, "ScanViewModel cleared, resources released")
    }
}
