package com.skeler.scanely.ui.screens

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.skeler.scanely.core.scan.DocumentScannerManager
import com.skeler.scanely.navigation.LocalNavController
import com.skeler.scanely.navigation.Routes
import com.skeler.scanely.ui.viewmodel.DocumentScanViewModel

/** Opens the ML Kit scanner straight from Home; degoogled devices fall back to the in-app camera. */
@Composable
fun rememberDocumentScanStarter(): () -> Unit {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val navController = LocalNavController.current
    val vm: DocumentScanViewModel = hiltViewModel(activity)

    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uris = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                ?.pages?.map { it.imageUri }.orEmpty()
            if (uris.isNotEmpty()) {
                vm.loadPages(uris)
                navController.navigate(Routes.SCAN_REVIEW)
            }
        }
    }

    return {
        if (DocumentScannerManager.isScannerAvailable(context)) {
            DocumentScannerManager.getStartScanIntent(activity)
                .addOnSuccessListener { intentSender ->
                    scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
                .addOnFailureListener {
                    navController.navigate(Routes.DOCUMENT_CAPTURE)
                }
        } else {
            navController.navigate(Routes.DOCUMENT_CAPTURE)
        }
    }
}
