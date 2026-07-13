@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.skeler.scanely.ui.screens

import android.Manifest
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
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
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.roundToInt

private const val TAG = "CameraScreen"

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
    var camera by remember { mutableStateOf<Camera?>(null) }
    var focusRing by remember { mutableStateOf<Offset?>(null) }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
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
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            BindCamera(previewView) { boundCapture, boundCamera ->
                imageCapture = boundCapture
                camera = boundCamera
            }

            // Tap-to-focus: sits under the controls, so shutter/close taps still win.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(camera) {
                        detectTapGestures { offset ->
                            val cam = camera ?: return@detectTapGestures
                            val point = previewView.meteringPointFactory
                                .createPoint(offset.x, offset.y)
                            val action = FocusMeteringAction.Builder(point)
                                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                .build()
                            runCatching { cam.cameraControl.startFocusAndMetering(action) }
                            focusRing = offset
                        }
                    }
            )

            focusRing?.let { point ->
                FocusRing(point) { focusRing = null }
            }

            CaptureTopBar(onClose = { navController.popBackStack() })

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                        )
                    )
                    .navigationBarsPadding()
                    .padding(top = 44.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FocusHint()
                Spacer(Modifier.height(18.dp))
                ShutterButton(
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
private fun CaptureTopBar(onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 32.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(
                onClick = onClose,
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
    }
}

@Composable
private fun FocusHint() {
    Surface(
        color = Color.Black.copy(alpha = 0.4f),
        shape = CircleShape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.TouchApp,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "Tap to focus · line the page up with the edges",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun ShutterButton(
    isCapturing: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(104.dp)
            .background(Color.Black.copy(alpha = 0.32f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .border(4.dp, Color.White.copy(alpha = 0.9f), CircleShape)
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                onClick = onClick,
                shape = CircleShape,
                color = if (isCapturing) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.fillMaxSize()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isCapturing) {
                        CircularWavyProgressIndicator(modifier = Modifier.size(36.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.CameraAlt,
                            contentDescription = "Capture",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusRing(point: Offset, onFinished: () -> Unit) {
    val ringPx = with(LocalDensity.current) { 76.dp.toPx() }
    val scale = remember(point) { Animatable(1.35f) }
    val alpha = remember(point) { Animatable(1f) }

    LaunchedEffect(point) {
        scale.animateTo(1f, tween(220))
        delay(500)
        alpha.animateTo(0f, tween(200))
        onFinished()
    }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (point.x - ringPx / 2f).roundToInt(),
                    (point.y - ringPx / 2f).roundToInt()
                )
            }
            .size(76.dp)
            .graphicsLayer { this.alpha = alpha.value }
            .scale(scale.value)
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .border(3.dp, Color.White, CircleShape)
        )
    }
}

@Composable
private fun BindCamera(
    previewView: PreviewView,
    onCameraReady: (ImageCapture, Camera) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

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
            val camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )

            onCameraReady(imageCapture, camera)
        } catch (e: Exception) {
            Log.e(TAG, "Camera binding failed", e)
        }
    }
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
