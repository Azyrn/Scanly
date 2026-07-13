package com.skeler.scanely.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** Max images/files accepted per AI scan (matches the per-file 20 MB cap in PayloadFactory). */
const val MAX_AI_FILES = 3

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

@Suppress("ComposableNaming")
@Composable
fun rememberMultiDocumentPicker(
    mimeTypes: Array<String> = arrayOf("application/pdf", "text/plain"),
    onDocumentsSelected: (List<Uri>) -> Unit
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        onDocumentsSelected(uris)
    }

    return { launcher.launch(mimeTypes) }
}
