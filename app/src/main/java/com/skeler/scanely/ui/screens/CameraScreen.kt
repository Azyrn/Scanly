@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.skeler.scanely.ui.screens

import android.Manifest
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.skeler.scanely.navigation.LocalNavController
import com.skeler.scanely.navigation.Routes
import com.skeler.scanely.ui.viewmodel.DocumentScanViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private const val TAG = "CameraScreen"

/**
 * CameraX capture fallback for the Smart Document Scanner, used when Google Play
 * services (and therefore the ML Kit document scanner) is unavailable — e.g. on
 * degoogled / GMS-less devices. Captures a frame, enhances it via
 * [DocumentScanViewModel.loadCapturedPages], then drops into the same review /
 * filter / export flow the ML Kit path uses.
 *
 * Edge detection, auto-crop and straightening are GMS-only, so a header banner
 * tells the user to frame the page themselves rather than pretending a crop that
 * cannot happen here.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun DocumentCaptureScreen() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val vm: DocumentScanViewModel = hiltViewModel(activity)
    val navController = LocalNavController.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    var isCapturing by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

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
            CameraPreviewContent { capture -> imageCapture = capture }

            FramingOverlay()

            // ---- Top: scrim + close + auto-crop-unavailable notice ----
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 28.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledIconButton(
                        onClick = { navController.popBackStack() },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.15f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = "Capture document",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(16.dp))

                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(
                            text = "Auto-crop and straightening need Google Play services. " +
                                "Line the page up with the frame for the cleanest scan.",
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // ---- Bottom: scrim + shutter ----
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                        )
                    )
                    .navigationBarsPadding()
                    .padding(top = 40.dp, bottom = 36.dp),
                contentAlignment = Alignment.Center
            ) {
                ShutterButton(
                    modifier = Modifier.size(84.dp),
                    isCapturing = isCapturing,
                    onClick = {
                        val capture = imageCapture
                        if (!isCapturing && capture != null) {
                            isCapturing = true
                            captureImage(
                                context,
                                capture,
                                onImageCaptured = { uri ->
                                    isCapturing = false
                                    vm.loadCapturedPages(listOf(uri))
                                    navController.navigate(Routes.SCAN_REVIEW)
                                },
                                onError = { exc ->
                                    isCapturing = false
                                    Log.e(TAG, "Document capture failed", exc)
                                    Toast.makeText(
                                        context,
                                        "Couldn't capture — try again",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    }
                )
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Camera permission required.",
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ShutterButton(
    modifier: Modifier = Modifier,
    isCapturing: Boolean,
    onClick: () -> Unit
) {
    LargeFloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = if (isCapturing) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
        contentColor = if (isCapturing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary,
        modifier = modifier
    ) {
        if (isCapturing) {
            CircularWavyProgressIndicator(modifier = Modifier.size(40.dp))
        } else {
            Icon(
                imageVector = Icons.Rounded.CameraAlt,
                contentDescription = "Capture",
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
private fun FramingOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 2.dp.toPx()
        val color = Color.White.copy(alpha = 0.5f)
        val cornerLength = 40.dp.toPx()
        val margin = 48.dp.toPx()
        val right = size.width - margin
        val bottom = size.height - margin

        // Top Left
        drawLine(color, Offset(margin, margin), Offset(margin + cornerLength, margin), strokeWidth)
        drawLine(color, Offset(margin, margin), Offset(margin, margin + cornerLength), strokeWidth)
        // Top Right
        drawLine(color, Offset(right, margin), Offset(right - cornerLength, margin), strokeWidth)
        drawLine(color, Offset(right, margin), Offset(right, margin + cornerLength), strokeWidth)
        // Bottom Left
        drawLine(color, Offset(margin, bottom), Offset(margin + cornerLength, bottom), strokeWidth)
        drawLine(color, Offset(margin, bottom), Offset(margin, bottom - cornerLength), strokeWidth)
        // Bottom Right
        drawLine(color, Offset(right, bottom), Offset(right - cornerLength, bottom), strokeWidth)
        drawLine(color, Offset(right, bottom), Offset(right, bottom - cornerLength), strokeWidth)
    }
}

@Composable
private fun CameraPreviewContent(
    onCameraReady: (imageCapture: ImageCapture) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    LaunchedEffect(Unit) {
        try {
            val cameraProvider = context.getCameraProvider()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )

            onCameraReady(imageCapture)
        } catch (e: Exception) {
            Log.e(TAG, "Camera binding failed", e)
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

private fun captureImage(
    context: Context,
    imageCapture: ImageCapture,
    onImageCaptured: (Uri) -> Unit,
    onError: (ImageCaptureException) -> Unit
) {
    val photoFile = File(
        context.cacheDir,
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            .format(System.currentTimeMillis()) + ".jpg"
    )

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                onImageCaptured(Uri.fromFile(photoFile))
            }

            override fun onError(exception: ImageCaptureException) {
                onError(exception)
            }
        }
    )
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider =
    suspendCoroutine { continuation ->
        ProcessCameraProvider.getInstance(this).also { future ->
            future.addListener(
                {
                    try {
                        continuation.resume(future.get())
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                },
                ContextCompat.getMainExecutor(this)
            )
        }
    }
