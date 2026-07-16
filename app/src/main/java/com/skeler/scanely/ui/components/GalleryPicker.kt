package com.skeler.scanely.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Max images per AI scan. They travel in one request, so this is the smallest documented
 * per-request image cap across providers (Groq and Cerebras both stop at 5).
 */
const val MAX_AI_FILES = 5

@Suppress("ComposableNaming") // Returns a lambda, not a Unit-returning composable
@Composable
fun rememberGalleryPicker(
    onImageSelected: (Uri?) -> Unit
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        onImageSelected(uri)
    }

    return {
        launcher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }
}

@Suppress("ComposableNaming")
@Composable
fun rememberMultiGalleryPicker(
    maxItems: Int = MAX_AI_FILES,
    onImagesSelected: (List<Uri>) -> Unit
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems)
    ) { uris ->
        onImagesSelected(uris)
    }

    return {
        launcher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }
}

/** One document per scan: a PDF's pages already fill the request's image budget. */
@Suppress("ComposableNaming")
@Composable
fun rememberDocumentPicker(
    mimeTypes: Array<String> = arrayOf("application/pdf", "text/plain"),
    onDocumentSelected: (Uri) -> Unit
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(onDocumentSelected)
    }

    return { launcher.launch(mimeTypes) }
}
