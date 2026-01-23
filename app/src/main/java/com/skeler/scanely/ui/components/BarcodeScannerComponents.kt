package com.skeler.scanely.ui.components

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.skeler.scanely.core.actions.ScanAction
import com.skeler.scanely.core.barcode.BarcodeAnalyzer
import java.util.concurrent.Executors

private const val TAG = "BarcodeScannerComponents"

/**
 * Camera preview with barcode analysis.
 */
@Composable
fun BarcodeCameraPreview(
    barcodeAnalyzer: BarcodeAnalyzer,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
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
                .also { it.setAnalyzer(cameraExecutor, barcodeAnalyzer) }

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
        modifier = modifier.fillMaxSize()
    )
}

/**
 * Scanning overlay with cutout and corner accents.
 */
@Composable
fun ScanningOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val scanBoxSize = size.minDimension * 0.7f
        val left = (size.width - scanBoxSize) / 2
        val top = (size.height - scanBoxSize) / 2

        // Semi-transparent overlay
        drawRect(color = Color.Black.copy(alpha = 0.5f), size = size)

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
        val accentColor = Color(0xFF6750A4)

        // Top-left
        drawLine(accentColor, Offset(left, top + 24.dp.toPx()), Offset(left, top + cornerLength), cornerStroke)
        drawLine(accentColor, Offset(left + 24.dp.toPx(), top), Offset(left + cornerLength, top), cornerStroke)
        // Top-right
        drawLine(accentColor, Offset(left + scanBoxSize, top + 24.dp.toPx()), Offset(left + scanBoxSize, top + cornerLength), cornerStroke)
        drawLine(accentColor, Offset(left + scanBoxSize - 24.dp.toPx(), top), Offset(left + scanBoxSize - cornerLength, top), cornerStroke)
        // Bottom-left
        drawLine(accentColor, Offset(left, top + scanBoxSize - 24.dp.toPx()), Offset(left, top + scanBoxSize - cornerLength), cornerStroke)
        drawLine(accentColor, Offset(left + 24.dp.toPx(), top + scanBoxSize), Offset(left + cornerLength, top + scanBoxSize), cornerStroke)
        // Bottom-right
        drawLine(accentColor, Offset(left + scanBoxSize, top + scanBoxSize - 24.dp.toPx()), Offset(left + scanBoxSize, top + scanBoxSize - cornerLength), cornerStroke)
        drawLine(accentColor, Offset(left + scanBoxSize - 24.dp.toPx(), top + scanBoxSize), Offset(left + scanBoxSize - cornerLength, top + scanBoxSize), cornerStroke)
    }
}

/**
 * Bottom sheet content for barcode actions.
 */
@Composable
fun BarcodeActionsSheet(
    actions: List<ScanAction>,
    onActionClick: (ScanAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(bottom = 32.dp)) {
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

/**
 * Text detail sheet for viewing and copying barcode content.
 */
@Composable
fun TextDetailSheet(
    text: String,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = "Barcode Content",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            SelectionContainer {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        FilledTonalButton(
            onClick = {
                onCopy()
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Copy to Clipboard")
        }
    }
}
