package com.skeler.scanely.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
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
import javax.inject.Inject
import kotlin.math.max

private const val TAG = "DocumentScanViewModel"
private const val MAX_DIMENSION = 2200
private const val PREVIEW_DIMENSION = 220

enum class ScanExportFormat(val label: String, val mimeType: String) {
    PDF("PDF", ScanExporter.PDF_MIME_TYPE),
    WORD("Word", ScanExporter.WORD_MIME_TYPE)
}

data class ExportedScan(
    val uri: Uri,
    val format: ScanExportFormat
)

data class FilterPreview(
    val filter: ScanFilter,
    val bitmap: Bitmap
)

data class DocumentScanUiState(
    val pages: List<Bitmap> = emptyList(),
    val filter: ScanFilter = ScanFilter.AUTO,
    val previews: List<FilterPreview> = emptyList(),
    val isLoading: Boolean = false,
    val isExporting: Boolean = false,
    val message: String? = null,
    val exportedScan: ExportedScan? = null
)

@HiltViewModel
class DocumentScanViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DocumentScanUiState())
    val uiState: StateFlow<DocumentScanUiState> = _uiState.asStateFlow()

    // Unfiltered source so filters are always re-derived, never stacked.
    private var originals: List<Bitmap> = emptyList()

    // Cancel on re-entry so stale load/filter jobs can't overwrite newer work.
    private var workJob: Job? = null
    private var previewJob: Job? = null

    fun loadPages(uris: List<Uri>) = load(uris, preprocess = false)

    // CameraX fallback (no GMS): lift contrast only — auto-crop is ML Kit's job, so the user frames
    // the page themselves and the review-screen filter does the rest.
    fun loadCapturedPages(uris: List<Uri>) = load(uris, preprocess = true)

    private fun load(uris: List<Uri>, preprocess: Boolean) {
        if (uris.isEmpty()) return
        workJob?.cancel()
        _uiState.update { it.copy(isLoading = true, exportedScan = null) }
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
            buildPreviews(0)
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

    /** Thumbnails of every filter applied to the page on screen, so the row shows the result, not a name. */
    fun buildPreviews(pageIndex: Int) {
        val source = originals.getOrNull(pageIndex) ?: return
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val previews = withContext(Dispatchers.Default) {
                val thumb = scaleToThumbnail(source)
                ScanFilter.entries.map { FilterPreview(it, DocumentFilters.apply(thumb, it)) }
            }
            _uiState.update { it.copy(previews = previews) }
        }
    }

    fun rotatePage(index: Int) {
        val current = _uiState.value.pages
        val original = originals.getOrNull(index) ?: return
        if (index !in current.indices) return
        viewModelScope.launch {
            val (rotatedOriginal, rotatedPage) = withContext(Dispatchers.Default) {
                rotate(original, 90f) to rotate(current[index], 90f)
            }
            originals = originals.toMutableList().also { it[index] = rotatedOriginal }
            _uiState.update { state ->
                state.copy(pages = state.pages.toMutableList().also { it[index] = rotatedPage })
            }
            buildPreviews(index)
        }
    }

    fun deletePage(index: Int) {
        if (index !in _uiState.value.pages.indices) return
        originals = originals.toMutableList().also { it.removeAt(index) }
        _uiState.update { state ->
            state.copy(pages = state.pages.toMutableList().also { it.removeAt(index) })
        }
        buildPreviews(index.coerceAtMost(originals.lastIndex))
    }

    fun exportPdf(name: String) = export(ScanExportFormat.PDF, name)

    fun exportWord(name: String) = export(ScanExportFormat.WORD, name)

    private fun export(format: ScanExportFormat, name: String) {
        val pages = _uiState.value.pages
        if (pages.isEmpty() || _uiState.value.isExporting) return
        _uiState.update { it.copy(isExporting = true) }
        viewModelScope.launch {
            val result = runCatching {
                when (format) {
                    ScanExportFormat.PDF -> ScanExporter.exportPdf(appContext, pages, name)
                    ScanExportFormat.WORD -> ScanExporter.exportWord(appContext, pages, name)
                }
            }
            _uiState.update {
                result.fold(
                    onSuccess = { uri ->
                        it.copy(
                            isExporting = false,
                            exportedScan = ExportedScan(uri, format),
                            message = "${format.label} saved to Downloads/Scanly"
                        )
                    },
                    onFailure = { e ->
                        Log.e(TAG, "${format.label} export failed", e)
                        it.copy(isExporting = false, message = "Couldn't create ${format.label}")
                    }
                )
            }
        }
    }

    fun saveImages(name: String) {
        val pages = _uiState.value.pages
        if (pages.isEmpty() || _uiState.value.isExporting) return
        _uiState.update { it.copy(isExporting = true) }
        viewModelScope.launch {
            val count = runCatching {
                ScanExporter.saveImagesToGallery(appContext, pages, name)
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

    fun consumeExport() = _uiState.update { it.copy(exportedScan = null) }

    fun clear() {
        workJob?.cancel()
        previewJob?.cancel()
        originals = emptyList()
        _uiState.value = DocumentScanUiState()
    }

    private fun applyFilter(sources: List<Bitmap>, filter: ScanFilter): List<Bitmap> =
        sources.map { DocumentFilters.apply(it, filter) }

    // Bitmaps replaced by rotate/delete are never recycled: Compose may still be drawing the old one.
    private fun rotate(source: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun scaleToThumbnail(source: Bitmap): Bitmap {
        val longest = max(source.width, source.height).coerceAtLeast(1)
        val scale = PREVIEW_DIMENSION.toFloat() / longest
        if (scale >= 1f) return source
        return Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

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
        val bitmap = appContext.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: error("Unable to decode $uri")
        return capDimension(applyExifRotation(uri, bitmap))
    }

    // BitmapFactory's power-of-two sampling can still decode close to twice MAX_DIMENSION.
    // Enforce the final cap before filtering/exporting to keep memory use predictable.
    private fun capDimension(bitmap: Bitmap): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= MAX_DIMENSION) return bitmap
        val scale = MAX_DIMENSION.toFloat() / longest
        val resized = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
        if (resized != bitmap) bitmap.recycle()
        return resized
    }

    // CameraX stores orientation in EXIF only; ONNX corner detection needs upright input.
    private fun applyExifRotation(uri: Uri, bitmap: Bitmap): Bitmap {
        val degrees = runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { stream ->
                when (
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                    )
                ) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        if (degrees == 0f) return bitmap

        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }
}
