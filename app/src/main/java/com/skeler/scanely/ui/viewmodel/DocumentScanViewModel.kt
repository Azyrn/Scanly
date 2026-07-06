package com.skeler.scanely.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skeler.scanely.core.image.DocumentFilters
import com.skeler.scanely.core.image.ImagePreprocessor
import com.skeler.scanely.core.image.ScanFilter
import com.skeler.scanely.core.pdf.ScanExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import kotlin.math.max

private const val TAG = "DocumentScanViewModel"
private const val MAX_DIMENSION = 2200

data class DocumentScanUiState(
    /** Pages with the active filter applied — what the user sees and exports. */
    val pages: List<Bitmap> = emptyList(),
    val filter: ScanFilter = ScanFilter.AUTO,
    val isLoading: Boolean = false,
    val isExporting: Boolean = false,
    val message: String? = null,
    /** Set after a successful PDF export so the UI can offer share/open. */
    val exportedPdf: Uri? = null
)

/**
 * Holds the scanned pages coming out of ML Kit, re-applies the user's chosen
 * [ScanFilter] on a background dispatcher, and drives PDF / image export.
 */
@HiltViewModel
class DocumentScanViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DocumentScanUiState())
    val uiState: StateFlow<DocumentScanUiState> = _uiState.asStateFlow()

    /** Untouched scanner output kept so filters are always re-derived from source. */
    private var originals: List<Bitmap> = emptyList()

    // Cancelled on re-entry so an older load/filter can't finish after a newer one.
    private var workJob: Job? = null

    /** Load already-straightened pages from the ML Kit document scanner. */
    fun loadPages(uris: List<Uri>) = load(uris, preprocess = false)

    /**
     * Load raw CameraX frames from the fallback capture path. Each frame is
     * auto-cropped and enhanced by [ImagePreprocessor] first so the fallback
     * still lands in review looking like a real scan, not a plain photo.
     */
    fun loadCapturedPages(uris: List<Uri>) = load(uris, preprocess = true)

    private fun load(uris: List<Uri>, preprocess: Boolean) {
        if (uris.isEmpty()) return
        workJob?.cancel()
        _uiState.update { it.copy(isLoading = true, exportedPdf = null) }
        workJob = viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                uris.mapNotNull {
                    runCatching {
                        val bitmap = decodeScaled(it)
                        if (preprocess) ImagePreprocessor.preprocessForDocument(bitmap) else bitmap
                    }.getOrNull()
                }
            }
            originals = loaded
            val filter = _uiState.value.filter
            val filtered = withContext(Dispatchers.Default) { applyFilter(loaded, filter) }
            _uiState.update {
                it.copy(pages = filtered, isLoading = false)
            }
        }
    }

    fun setFilter(filter: ScanFilter) {
        if (filter == _uiState.value.filter && _uiState.value.pages.isNotEmpty()) return
        workJob?.cancel()
        _uiState.update { it.copy(filter = filter, isLoading = true) }
        workJob = viewModelScope.launch {
            val filtered = withContext(Dispatchers.Default) { applyFilter(originals, filter) }
            _uiState.update { it.copy(pages = filtered, isLoading = false) }
        }
    }

    fun exportPdf() {
        val pages = _uiState.value.pages
        if (pages.isEmpty() || _uiState.value.isExporting) return
        _uiState.update { it.copy(isExporting = true) }
        viewModelScope.launch {
            val result = runCatching {
                ScanExporter.exportPdf(appContext, pages, timestampName())
            }
            _uiState.update {
                result.fold(
                    onSuccess = { uri ->
                        it.copy(isExporting = false, exportedPdf = uri, message = "PDF ready")
                    },
                    onFailure = { e ->
                        Log.e(TAG, "PDF export failed", e)
                        it.copy(isExporting = false, message = "Couldn't create PDF")
                    }
                )
            }
        }
    }

    fun saveImages() {
        val pages = _uiState.value.pages
        if (pages.isEmpty() || _uiState.value.isExporting) return
        _uiState.update { it.copy(isExporting = true) }
        viewModelScope.launch {
            val count = runCatching {
                ScanExporter.saveImagesToGallery(appContext, pages, timestampName())
            }.getOrDefault(0)
            _uiState.update {
                it.copy(
                    isExporting = false,
                    message = if (count > 0) {
                        "Saved $count image${if (count > 1) "s" else ""} to gallery"
                    } else {
                        "Couldn't save images"
                    }
                )
            }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    fun clear() {
        workJob?.cancel()
        originals = emptyList()
        _uiState.value = DocumentScanUiState()
    }

    private fun applyFilter(sources: List<Bitmap>, filter: ScanFilter): List<Bitmap> =
        sources.map { DocumentFilters.apply(it, filter) }

    private fun decodeScaled(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val longest = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        var sample = 1
        while (longest / sample > MAX_DIMENSION) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return appContext.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: error("Unable to decode $uri")
    }

    private fun timestampName(): String =
        "Scan_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
}
