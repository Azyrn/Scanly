package com.skeler.scanely.ui.screens

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.skeler.scanely.ui.components.GlassPanel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private const val TAG = "CameraScreen"

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    onImageCaptured: (Uri) -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    
    var isCapturing by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var flashEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (cameraPermissionState.status.isGranted) {
            // 1. Camera Preview (Full Screen)
            CameraPreviewContent(
                onImageCaptureReady = { imageCapture = it }
            )
            
            // 2. Framing Guide Overlay
            FramingOverlay()
            
            // 3. Shutter Button (Centered at Bottom)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 64.dp), // Check padding to avoid nav bar overlap
                contentAlignment = Alignment.Center
            ) {
                 ShutterButton(
                     isCapturing = isCapturing,
                     onClick = {
                         if (!isCapturing && imageCapture != null) {
                              isCapturing = true
                              captureImage(
                                  context,
                                  imageCapture!!,
                                  onImageCaptured = { uri ->
                                      isCapturing = false
                                      onImageCaptured(uri)
                                  },
                                  onError = { exc ->
                                      isCapturing = false
                                      Log.e(TAG, "Capture failed", exc)
                                  }
                              )
                         }
                     }
                 )
            }
        } else {
             // Permission Denied State
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
fun ShutterButton(
    isCapturing: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    LargeFloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = if (isCapturing) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
        contentColor = if (isCapturing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary,
        interactionSource = interactionSource,
        modifier = Modifier.size(84.dp)
    ) {
        if (isCapturing) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
                strokeWidth = 3.dp
            )
        } else {
             Icon(
                 imageVector = Icons.Filled.CameraAlt,
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
        val margin = 48.dp.toPx() // Margin from edges
        
        // Top Left
        drawLine(color, Offset(margin, margin), Offset(margin + cornerLength, margin), strokeWidth)
        drawLine(color, Offset(margin, margin), Offset(margin, margin + cornerLength), strokeWidth)
        
        // Top Right
        drawLine(color, Offset(size.width - margin, margin), Offset(size.width - margin - cornerLength, margin), strokeWidth)
        drawLine(color, Offset(size.width - margin, margin), Offset(size.width - margin, margin + cornerLength), strokeWidth)
        
        // Bottom Left
        drawLine(color, Offset(margin, size.height - margin), Offset(margin + cornerLength, size.height - margin), strokeWidth)
        drawLine(color, Offset(margin, size.height - margin), Offset(margin, size.height - margin - cornerLength), strokeWidth)
        
        // Bottom Right
        drawLine(color, Offset(size.width - margin, size.height - margin), Offset(size.width - margin - cornerLength, size.height - margin), strokeWidth)
        drawLine(color, Offset(size.width - margin, size.height - margin), Offset(size.width - margin, size.height - margin - cornerLength), strokeWidth)
    }
}

@Composable
private fun CameraPreviewContent(
    onImageCaptureReady: (ImageCapture) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val previewView = remember { PreviewView(context) }
    // We can use a simpler scale type for immersive feel
    previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
    
    LaunchedEffect(Unit) {
        val cameraProvider = context.getCameraProvider()
        
        val preview = Preview.Builder().build()
        preview.surfaceProvider = previewView.surfaceProvider
        
        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
        
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
            onImageCaptureReady(imageCapture)
        } catch (e: Exception) {
            Log.e(TAG, "Camera binding failed", e)
        }
    }
    
    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )
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
                // Return URI on main thread just in case, though usually callback is on main executor
                Handler(Looper.getMainLooper()).post {
                    onImageCaptured(Uri.fromFile(photoFile))
                }
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
                { continuation.resume(future.get()) },
                ContextCompat.getMainExecutor(this)
            )
        }
    }

