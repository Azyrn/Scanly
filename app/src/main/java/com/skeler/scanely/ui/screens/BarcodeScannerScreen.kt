@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.skeler.scanely.ui.screens

import android.Manifest
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.skeler.scanely.core.actions.ActionExecutor
import com.skeler.scanely.core.actions.ScanAction
import com.skeler.scanely.core.barcode.BarcodeAnalyzer
import com.skeler.scanely.navigation.LocalNavController
import java.util.concurrent.Executors

private const val TAG = "BarcodeScannerScreen"

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScannerScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val navController = LocalNavController.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    var detectedActions by remember { mutableStateOf<List<ScanAction>>(emptyList()) }
    var showActionsSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Barcode analyzer
    val barcodeAnalyzer = remember {
        BarcodeAnalyzer { actions ->
            if (actions.isNotEmpty() && !showActionsSheet) {
                detectedActions = actions
                showActionsSheet = true
            }
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            barcodeAnalyzer.close()
        }
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
            // Camera Preview with Barcode Analysis
            BarcodeCameraPreview(
                barcodeAnalyzer = barcodeAnalyzer
            )

            // Scanning Overlay
            ScanningOverlay()

            // Hint text
            AnimatedVisibility(
                visible = !showActionsSheet,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Black.copy(alpha = 0.7f)
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = "Point camera at barcode or QR code",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // Back button
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
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        } else {
            // Permission Denied State
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Camera permission required\nfor barcode scanning.",
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    // Actions Bottom Sheet
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
                    showActionsSheet = false
                    // Execute the action (open URL, copy text, call, email, etc.)
                    ActionExecutor.execute(context, action)
                },
                onDismiss = {
                    showActionsSheet = false
                    detectedActions = emptyList()
                }
            )
        }
    }
}

@Composable
private fun BarcodeCameraPreview(
    barcodeAnalyzer: BarcodeAnalyzer
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, barcodeAnalyzer)
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun ScanningOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val scanBoxSize = size.minDimension * 0.7f
        val left = (size.width - scanBoxSize) / 2
        val top = (size.height - scanBoxSize) / 2

        // Semi-transparent overlay
        drawRect(
            color = Color.Black.copy(alpha = 0.5f),
            size = size
        )

        // Clear center area (punch-out effect)
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(scanBoxSize, scanBoxSize),
            cornerRadius = CornerRadius(24.dp.toPx()),
            blendMode = BlendMode.Clear
        )

        // White border around scan area
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(scanBoxSize, scanBoxSize),
            cornerRadius = CornerRadius(24.dp.toPx()),
            style = Stroke(width = 3.dp.toPx())
        )

        // Corner accents
        val cornerLength = 40.dp.toPx()
        val cornerStroke = 4.dp.toPx()
        val accentColor = Color(0xFF6750A4) // Primary color

        // Top-left corner
        drawLine(accentColor, Offset(left, top + 24.dp.toPx()), Offset(left, top + cornerLength), cornerStroke)
        drawLine(accentColor, Offset(left + 24.dp.toPx(), top), Offset(left + cornerLength, top), cornerStroke)

        // Top-right corner
        drawLine(accentColor, Offset(left + scanBoxSize, top + 24.dp.toPx()), Offset(left + scanBoxSize, top + cornerLength), cornerStroke)
        drawLine(accentColor, Offset(left + scanBoxSize - 24.dp.toPx(), top), Offset(left + scanBoxSize - cornerLength, top), cornerStroke)

        // Bottom-left corner
        drawLine(accentColor, Offset(left, top + scanBoxSize - 24.dp.toPx()), Offset(left, top + scanBoxSize - cornerLength), cornerStroke)
        drawLine(accentColor, Offset(left + 24.dp.toPx(), top + scanBoxSize), Offset(left + cornerLength, top + scanBoxSize), cornerStroke)

        // Bottom-right corner
        drawLine(accentColor, Offset(left + scanBoxSize, top + scanBoxSize - 24.dp.toPx()), Offset(left + scanBoxSize, top + scanBoxSize - cornerLength), cornerStroke)
        drawLine(accentColor, Offset(left + scanBoxSize - 24.dp.toPx(), top + scanBoxSize), Offset(left + scanBoxSize - cornerLength, top + scanBoxSize), cornerStroke)
    }
}

@Composable
private fun BarcodeActionsSheet(
    actions: List<ScanAction>,
    onActionClick: (ScanAction) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.padding(bottom = 32.dp)
    ) {
        Text(
            text = "Barcode Detected",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(actions) { action ->
                Card(
                    onClick = { onActionClick(action) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = action.label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        supportingContent = {
                            val subtitle = when (action) {
                                is ScanAction.OpenUrl -> action.url.take(50)
                                is ScanAction.CopyText -> action.text.take(50)
                                is ScanAction.CallPhone -> action.number
                                is ScanAction.SendEmail -> action.email
                                is ScanAction.ConnectWifi -> action.ssid
                                is ScanAction.SendSms -> action.number
                                is ScanAction.AddContact -> action.name ?: "Contact"
                                is ScanAction.ShowRaw -> action.text.take(50)
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
