package com.skeler.scanely.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Photo Picker wrapper using Material 3 Photo Picker (Android 13+)
 * with fallback to legacy picker for older versions.
 * 
 * @param onImageSelected Callback with selected image Uri, null if cancelled
 * @return Lambda to launch the picker
 */
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

/**
 * Multiple image picker for batch processing.
 * 
 * @param maxItems Maximum number of images to select
 * @param onImagesSelected Callback with list of selected image Uris
 * @return Lambda to launch the picker
 */
@Suppress("ComposableNaming") // Returns a lambda, not a Unit-returning composable
@Composable
fun rememberMultiGalleryPicker(
    maxItems: Int = 10,
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

/**
 * Multiple document picker for PDFs and text files.
 * 
 * @param mimeTypes MIME types to filter (default: PDF and plain text)
 * @param onDocumentsSelected Callback with list of selected document Uris
 * @return Lambda to launch the picker
 */
@Suppress("ComposableNaming") // Returns a lambda, not a Unit-returning composable
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

