package com.skeler.scanely.ui.components

import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.skeler.scanely.core.actions.ScanAction
import com.skeler.scanely.core.barcode.BarcodeAnalyzer
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val TAG = "BarcodeScannerComponents"

@Composable
fun BarcodeCameraPreview(
    barcodeAnalyzer: BarcodeAnalyzer,
    modifier: Modifier = Modifier,
    autoZoomEnabled: () -> Boolean = { true },
    onCameraBound: (Camera, PreviewView) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            barcodeAnalyzer.onZoomSuggested = null
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

            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        android.util.Size(1280, 720),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(resolutionSelector)
                .build()
                .also { it.setAnalyzer(cameraExecutor, barcodeAnalyzer) }

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )

                onCameraBound(camera, previewView)

                // Auto-zoom from ML Kit suggestions (small/distant codes), until the
                // user pinches — after that the zoom is theirs to control.
                barcodeAnalyzer.onZoomSuggested = { suggestedRatio ->
                    if (autoZoomEnabled()) {
                        val maxZoom = camera.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
                        val target = suggestedRatio.coerceIn(1f, maxZoom)
                        camera.cameraControl.setZoomRatio(target)
                        true
                    } else {
                        false
                    }
                }
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

/** [verticalBias] 0f pins the box to the top, 0.5f centres it. */
@Composable
fun ScanningOverlay(
    modifier: Modifier = Modifier,
    verticalBias: Float = 0.5f
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val scanBoxSize = size.minDimension * 0.7f
        val left = (size.width - scanBoxSize) / 2
        val top = (size.height - scanBoxSize) * verticalBias

        drawRect(color = Color.Black.copy(alpha = 0.5f), size = size)

        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(scanBoxSize, scanBoxSize),
            cornerRadius = CornerRadius(24.dp.toPx()),
            blendMode = BlendMode.Clear
        )

        drawRoundRect(
            color = Color.White,
            topLeft = Offset(left, top),
            size = Size(scanBoxSize, scanBoxSize),
            cornerRadius = CornerRadius(24.dp.toPx()),
            style = Stroke(width = 3.dp.toPx())
        )

        val cornerLength = 40.dp.toPx()
        val cornerStroke = 4.dp.toPx()
        val accentColor = Color(0xFF6750A4)

        drawLine(accentColor, Offset(left, top + 24.dp.toPx()), Offset(left, top + cornerLength), cornerStroke)
        drawLine(accentColor, Offset(left + 24.dp.toPx(), top), Offset(left + cornerLength, top), cornerStroke)
        drawLine(
            accentColor,
            Offset(left + scanBoxSize, top + 24.dp.toPx()),
            Offset(left + scanBoxSize, top + cornerLength),
            cornerStroke
        )
        drawLine(
            accentColor,
            Offset(left + scanBoxSize - 24.dp.toPx(), top),
            Offset(left + scanBoxSize - cornerLength, top),
            cornerStroke
        )
        drawLine(
            accentColor,
            Offset(left, top + scanBoxSize - 24.dp.toPx()),
            Offset(left, top + scanBoxSize - cornerLength),
            cornerStroke
        )
        drawLine(
            accentColor,
            Offset(left + 24.dp.toPx(), top + scanBoxSize),
            Offset(left + cornerLength, top + scanBoxSize),
            cornerStroke
        )
        drawLine(
            accentColor,
            Offset(left + scanBoxSize, top + scanBoxSize - 24.dp.toPx()),
            Offset(left + scanBoxSize, top + scanBoxSize - cornerLength),
            cornerStroke
        )
        drawLine(
            accentColor,
            Offset(left + scanBoxSize - 24.dp.toPx(), top + scanBoxSize),
            Offset(left + scanBoxSize - cornerLength, top + scanBoxSize),
            cornerStroke
        )
    }
}

/**
 * Pinch to zoom, tap to focus. Sits above the scan overlay so it sees touches first;
 * the camera is bound elsewhere, so both may still be null on the first frames.
 */
@Composable
fun CameraGestureOverlay(
    camera: Camera?,
    previewView: PreviewView?,
    onUserZoom: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var zoomVisible by remember { mutableStateOf(false) }

    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            delay(FOCUS_RING_MS)
            focusPoint = null
        }
    }

    LaunchedEffect(zoomRatio, zoomVisible) {
        if (zoomVisible) {
            delay(ZOOM_LABEL_MS)
            zoomVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(camera) {
                detectTransformGestures { _, _, gestureZoom, _ ->
                    val control = camera ?: return@detectTransformGestures
                    val state = control.cameraInfo.zoomState.value ?: return@detectTransformGestures
                    // setZoomRatio is async, so zoomState lags a live pinch — accumulate
                    // locally while the gesture is running and re-sync when it starts.
                    val base = if (zoomVisible) zoomRatio else state.zoomRatio
                    val target = (base * gestureZoom)
                        .coerceIn(state.minZoomRatio, state.maxZoomRatio)
                    control.cameraControl.setZoomRatio(target)
                    zoomRatio = target
                    zoomVisible = true
                    onUserZoom()
                }
            }
            .pointerInput(camera, previewView) {
                detectTapGestures { offset ->
                    val control = camera ?: return@detectTapGestures
                    val view = previewView ?: return@detectTapGestures
                    val point = view.meteringPointFactory.createPoint(offset.x, offset.y)
                    val action = FocusMeteringAction
                        .Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                        .setAutoCancelDuration(AUTO_CANCEL_SECONDS, TimeUnit.SECONDS)
                        .build()
                    control.cameraControl.startFocusAndMetering(action)
                    focusPoint = offset
                }
            }
    ) {
        focusPoint?.let { point ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.White,
                    radius = 34.dp.toPx(),
                    center = point,
                    style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(color = Color.White, radius = 4.dp.toPx(), center = point)
            }
        }

        AnimatedVisibility(
            visible = zoomVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 260.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f)),
                shape = CircleShape
            ) {
                Text(
                    text = String.format(Locale.US, "%.1f×", zoomRatio),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

private const val FOCUS_RING_MS = 900L
private const val ZOOM_LABEL_MS = 1200L
private const val AUTO_CANCEL_SECONDS = 4L

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
                                is ScanAction.AddEvent -> action.title ?: "Event"
                                is ScanAction.ShowRaw -> action.text.take(50)
                                is ScanAction.LookupProduct -> "Barcode: ${action.barcode}"
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
            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Copy to Clipboard")
        }
    }
}
