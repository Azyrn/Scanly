package com.skeler.scanely.ui.screens

/**
 * Clean & Simple HomeScreen - Orchestration Layer Only
 * 
 * Components extracted to:
 * - MainActionButton.kt
 * - GamifiedAiFab.kt
 * - AiModeBottomSheet.kt
 */
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.skeler.scanely.core.ai.AiMode
import com.skeler.scanely.navigation.LocalNavController
import com.skeler.scanely.navigation.Routes
import com.skeler.scanely.ui.ScanViewModel
import com.skeler.scanely.ui.components.AiModeBottomSheet
import com.skeler.scanely.ui.components.GamifiedAiFab
import com.skeler.scanely.ui.components.MainActionButton
import com.skeler.scanely.ui.components.RateLimitDisplayState
import com.skeler.scanely.ui.components.RateLimitSheet
import com.skeler.scanely.ui.components.rememberGalleryPicker
import com.skeler.scanely.ui.components.rememberMultiDocumentPicker
import com.skeler.scanely.ui.components.rememberMultiGalleryPicker
import com.skeler.scanely.ui.viewmodel.AiScanViewModel
import com.skeler.scanely.ui.viewmodel.OcrViewModel
import com.skeler.scanely.ui.viewmodel.UnifiedScanViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val scanViewModel: ScanViewModel = hiltViewModel(activity)
    val aiViewModel: AiScanViewModel = hiltViewModel(activity)
    val ocrViewModel: OcrViewModel = hiltViewModel(activity)
    val unifiedViewModel: UnifiedScanViewModel = hiltViewModel(activity)
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    // UI State
    val snackbarHostState = remember { SnackbarHostState() }
    var showAiBottomSheet by remember { mutableStateOf(false) }
    val aiSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pendingAiMode by remember { mutableStateOf<AiMode?>(null) }

    // Rate Limit state
    val showRateLimitSheet by scanViewModel.showRateLimitSheet.collectAsState()
    val rateLimitState by scanViewModel.rateLimitState.collectAsState()

    // Auto-dismiss rate limit sheet when cooldown completes
    LaunchedEffect(rateLimitState.remainingSeconds, rateLimitState.justBecameReady) {
        if (rateLimitState.remainingSeconds == 0 && rateLimitState.justBecameReady) {
            scanViewModel.dismissRateLimitSheet()
        }
    }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                showAiBottomSheet = true
            } else {
                scope.launch { snackbarHostState.showSnackbar("Camera permission denied") }
            }
        }
    )

    // Pickers
    val aiMultiGalleryPicker = rememberMultiGalleryPicker(maxItems = 10) { uris ->
        if (uris.isNotEmpty() && pendingAiMode != null) {
            val mode = pendingAiMode!!
            pendingAiMode = null
            if (scanViewModel.triggerAiWithRateLimit {
                scanViewModel.onImageSelected(uris.first())
                aiViewModel.processMultipleFiles(uris, mode)
            }) {
                navController.navigate(Routes.RESULTS)
            }
        }
    }

    val aiMultiDocumentPicker = rememberMultiDocumentPicker(
        mimeTypes = arrayOf("application/pdf", "text/plain")
    ) { uris ->
        if (uris.isNotEmpty() && pendingAiMode != null) {
            val mode = pendingAiMode!!
            pendingAiMode = null
            uris.forEach { uri ->
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) { }
            }
            if (scanViewModel.triggerAiWithRateLimit {
                scanViewModel.onPdfSelected(uris.first())
                aiViewModel.processMultipleFiles(uris, mode)
            }) {
                navController.navigate(Routes.RESULTS)
            }
        }
    }

    val launchGalleryPicker = rememberGalleryPicker { uri ->
        if (uri != null) {
            aiViewModel.clearResult()
            ocrViewModel.clearResult()
            scanViewModel.onImageSelected(uri)
            unifiedViewModel.processImage(uri)
            navController.navigate(Routes.UNIFIED_RESULTS)
        }
    }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { }
            aiViewModel.clearResult()
            ocrViewModel.clearResult()
            scanViewModel.onPdfSelected(uri)
            ocrViewModel.processPdf(uri)
            navController.navigate(Routes.RESULTS)
        }
    }

    // Actions
    fun onAiFabClick() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) 
            == PackageManager.PERMISSION_GRANTED) {
            showAiBottomSheet = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun onAiModeSelected(mode: AiMode) {
        pendingAiMode = mode
        showAiBottomSheet = false
        when (mode) {
            AiMode.EXTRACT_TEXT -> aiMultiGalleryPicker()
            AiMode.EXTRACT_PDF_TEXT -> aiMultiDocumentPicker()
            else -> aiMultiGalleryPicker()
        }
    }

    // UI
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Scanly",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            GamifiedAiFab(
                rateLimitState = RateLimitDisplayState(
                    remainingSeconds = rateLimitState.remainingSeconds,
                    progress = rateLimitState.progress,
                    justBecameReady = rateLimitState.justBecameReady
                ),
                onClick = { onAiFabClick() }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "What would you like to scan?",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(48.dp))

            MainActionButton(
                icon = Icons.Outlined.CameraAlt,
                title = "Capture Photo",
                subtitle = "Scan text using camera",
                onClick = { navController.navigate(Routes.CAMERA) }
            )
            Spacer(modifier = Modifier.height(16.dp))

            MainActionButton(
                icon = Icons.Outlined.PhotoLibrary,
                title = "From Gallery",
                subtitle = "Import image file",
                onClick = { launchGalleryPicker() }
            )
            Spacer(modifier = Modifier.height(16.dp))

            MainActionButton(
                icon = Icons.Outlined.PictureAsPdf,
                title = "Extract PDF",
                subtitle = "Import PDF document",
                onClick = { pdfLauncher.launch(arrayOf("application/pdf")) }
            )
            Spacer(modifier = Modifier.height(16.dp))

            MainActionButton(
                icon = Icons.Outlined.QrCodeScanner,
                title = "Scan Barcode/QR",
                subtitle = "Scan QR, Barcodes & More",
                onClick = { navController.navigate(Routes.BARCODE_SCANNER) }
            )

            Spacer(modifier = Modifier.height(48.dp))

            FilledTonalButton(
                onClick = { navController.navigate(Routes.HISTORY) },
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text("View Previous Extracts")
            }
        }
    }

    // Bottom Sheets
    if (showAiBottomSheet) {
        AiModeBottomSheet(
            sheetState = aiSheetState,
            onDismiss = { showAiBottomSheet = false },
            onModeSelected = { onAiModeSelected(it) }
        )
    }

    if (showRateLimitSheet) {
        RateLimitSheet(
            remainingSeconds = rateLimitState.remainingSeconds,
            onDismiss = { scanViewModel.dismissRateLimitSheet() }
        )
    }
}
