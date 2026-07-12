@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.skeler.scanely.ui.screens

import android.Manifest
import android.content.ClipData
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.skeler.scanely.core.actions.ActionExecutor
import com.skeler.scanely.core.actions.ScanAction
import com.skeler.scanely.core.barcode.BarcodeAnalyzer
import com.skeler.scanely.core.barcode.BarcodeEngine
import com.skeler.scanely.core.lookup.LookupOrchestrator
import com.skeler.scanely.settings.data.SettingsKeys
import com.skeler.scanely.settings.presentation.viewmodel.SettingsViewModel
import com.skeler.scanely.core.lookup.LookupResult
import com.skeler.scanely.navigation.LocalNavController
import com.skeler.scanely.ui.components.BarcodeActionsSheet
import com.skeler.scanely.ui.components.BarcodeCameraPreview
import com.skeler.scanely.ui.components.ProductDetailSheet
import com.skeler.scanely.ui.components.ScanningOverlay
import com.skeler.scanely.ui.components.TextDetailSheet
import com.skeler.scanely.ui.components.rememberGalleryPicker
import com.skeler.scanely.ui.viewmodel.UnifiedScanViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScannerScreen(
    lookupOrchestrator: LookupOrchestrator
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val navController = LocalNavController.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val unifiedViewModel: UnifiedScanViewModel = hiltViewModel(activity)
    val scope = rememberCoroutineScope()

    var detectedActions by remember { mutableStateOf<List<ScanAction>>(emptyList()) }
    var showActionsSheet by remember { mutableStateOf(false) }
    var textToShow by remember { mutableStateOf<String?>(null) }
    var isProcessingGallery by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val textSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showProductSheet by remember { mutableStateOf(false) }
    var lookupResult by remember { mutableStateOf<LookupResult?>(null) }
    var isLookupLoading by remember { mutableStateOf(false) }

    val galleryPicker = rememberGalleryPicker { uri ->
        if (uri != null) {
            isProcessingGallery = true
            scope.launch {
                unifiedViewModel.processBarcodeOnly(uri)
                val result = unifiedViewModel.uiState.first { !it.isLoading }
                isProcessingGallery = false
                if (result.barcodeActions.isNotEmpty()) {
                    detectedActions = result.barcodeActions
                    showActionsSheet = true
                } else {
                    Toast.makeText(context, "No barcode found", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun lookupProduct(barcode: String) {
        showActionsSheet = false
        showProductSheet = true
        isLookupLoading = true
        lookupResult = null
        
        scope.launch {
            lookupResult = lookupOrchestrator.lookup(barcode)
            isLookupLoading = false
        }
    }

    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val engineId by settingsViewModel.getString(SettingsKeys.BARCODE_ENGINE)
        .collectAsState(initial = null)

    val barcodeAnalyzer = remember(engineId) {
        engineId?.let { id ->
            BarcodeAnalyzer(engine = BarcodeEngine.fromId(id)) { actions ->
                if (actions.isNotEmpty() && !showActionsSheet && textToShow == null && !showProductSheet) {
                    detectedActions = actions
                    showActionsSheet = true
                }
            }
        }
    }

    DisposableEffect(barcodeAnalyzer) {
        onDispose { barcodeAnalyzer?.close() }
    }

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (cameraPermissionState.status.isGranted) {
            barcodeAnalyzer?.let { BarcodeCameraPreview(barcodeAnalyzer = it) }
            ScanningOverlay()

            AnimatedVisibility(
                visible = !showActionsSheet && !showProductSheet,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f)),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = "Point camera at barcode or QR code",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    
                    Card(
                        onClick = { galleryPicker() },
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isProcessingGallery) "Scanning..." else "Upload from gallery",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.5f),
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Camera permission required\nfor barcode scanning.",
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showActionsSheet && detectedActions.isNotEmpty()) {
        ModalBottomSheet(
            onDismissRequest = {
                showActionsSheet = false
                detectedActions = emptyList()
            },
            sheetState = sheetState
        ) {
            BarcodeActionsSheet(
                actions = detectedActions,
                onActionClick = { action ->
                    when (action) {
                        is ScanAction.ShowRaw -> {
                            showActionsSheet = false
                            textToShow = action.text
                        }
                        is ScanAction.LookupProduct -> {
                            lookupProduct(action.barcode)
                        }
                        else -> {
                            showActionsSheet = false
                            ActionExecutor.execute(context, action)
                        }
                    }
                }
            )
        }
    }

    textToShow?.let { text ->
        ModalBottomSheet(
            onDismissRequest = { textToShow = null },
            sheetState = textSheetState
        ) {
            TextDetailSheet(
                text = text,
                onCopy = {
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    clipboard.setPrimaryClip(ClipData.newPlainText("Barcode Content", text))
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { textToShow = null }
            )
        }
    }

    if (showProductSheet) {
        ProductDetailSheet(
            result = lookupResult,
            isLoading = isLookupLoading,
            onDismiss = {
                showProductSheet = false
                lookupResult = null
            }
        )
    }
}
