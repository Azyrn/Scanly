package com.skeler.scanely.core.scan

import android.app.Activity
import android.content.IntentSender
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning

/**
 * Scan presets exposed to the user. Each preset tunes the ML Kit Document
 * Scanner for a specific document type while the heavy computer-vision work
 * (edge detection, perspective correction, page straightening, shadow/glare
 * removal, auto-capture and live edge overlay) is handled by the engine.
 */
enum class DocumentScanMode(
    val title: String,
    val subtitle: String,
    /** Maximum number of pages the scanner will let the user capture. */
    val pageLimit: Int
) {
    /** Multi-page documents, receipts, notes, contracts. */
    DOCUMENT(
        title = "Document",
        subtitle = "Multi-page, auto-cropped & straightened",
        pageLimit = 20
    ),

    /** Single-side ID cards, licenses, badges — one crisp page. */
    ID_CARD(
        title = "ID Card",
        subtitle = "Single card, tight crop & glare cleanup",
        pageLimit = 1
    )
}

/**
 * Thin wrapper over Google's ML Kit Document Scanner. It only builds a scan
 * [IntentSender]; the resulting pages are handed off to our own Material 3
 * Expressive review / filter / export flow (see DocumentScanViewModel).
 */
object DocumentScannerManager {

    /**
     * Build a start-scan intent for the given [mode].
     *
     * SCANNER_MODE_FULL enables ML-based image cleaning (shadow removal,
     * enhancement) plus the on-device editing UI with a live edge overlay,
     * auto-capture on stability and manual corner adjustment. Results are
     * requested in JPEG so we can re-process them with our own filters.
     */
    fun getStartScanIntent(activity: Activity, mode: DocumentScanMode): Task<IntentSender> {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(mode.pageLimit)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        return GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
    }
}
