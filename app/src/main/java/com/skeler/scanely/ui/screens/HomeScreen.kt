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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings

import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.skeler.scanely.R
import com.skeler.scanely.core.ai.AiMode
import com.skeler.scanely.core.ai.AiProvider
import com.skeler.scanely.navigation.LocalNavController
import com.skeler.scanely.navigation.Routes
import com.skeler.scanely.ui.ScanViewModel
import com.skeler.scanely.ui.components.AiModeBottomSheet
import com.skeler.scanely.ui.components.GamifiedAiFab
import com.skeler.scanely.ui.components.HistoryPillButton
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
    var pendingAiProvider by remember { mutableStateOf(AiProvider.DEFAULT) }

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
            val provider = pendingAiProvider
            pendingAiMode = null
            if (scanViewModel.triggerAiWithRateLimit {
                scanViewModel.onImageSelected(uris.first())
                aiViewModel.processMultipleFiles(uris, mode, provider)
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
            val provider = pendingAiProvider
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
                aiViewModel.processMultipleFiles(uris, mode, provider)
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

    fun onAiModeSelected(mode: AiMode, provider: AiProvider) {
        pendingAiMode = mode
        pendingAiProvider = provider
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
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
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

            val accents = rememberActionAccents()

            MainActionButton(
                iconRes = R.drawable.ic_action_aperture,
                title = "Scan Document",
                subtitle = "Auto-crop, enhance & export PDF",
                onClick = { navController.navigate(Routes.DOCUMENT_SCANNER) },
                accentTint = accents.document
            )
            Spacer(modifier = Modifier.height(16.dp))

            MainActionButton(
                iconRes = R.drawable.ic_action_gallery_stack,
                title = "From Gallery",
                subtitle = "Import image file",
                onClick = { launchGalleryPicker() },
                accentTint = accents.gallery
            )
            Spacer(modifier = Modifier.height(16.dp))

            MainActionButton(
                iconRes = R.drawable.ic_action_document,
                title = "Extract PDF",
                subtitle = "Import PDF document",
                onClick = { pdfLauncher.launch(arrayOf("application/pdf")) },
                accentTint = accents.pdf
            )
            Spacer(modifier = Modifier.height(16.dp))

            MainActionButton(
                iconRes = R.drawable.ic_action_qr,
                title = "Scan Barcode/QR",
                subtitle = "Scan QR, Barcodes & More",
                onClick = { navController.navigate(Routes.BARCODE_SCANNER) },
                accentTint = accents.qr
            )


            Spacer(modifier = Modifier.height(48.dp))

            HistoryPillButton(
                onClick = { navController.navigate(Routes.HISTORY) }
            )
        }
    }

    // Bottom Sheets
    if (showAiBottomSheet) {
        AiModeBottomSheet(
            sheetState = aiSheetState,
            onDismiss = { showAiBottomSheet = false },
            onModeSelected = { mode, provider -> onAiModeSelected(mode, provider) }
        )
    }

    if (showRateLimitSheet) {
        RateLimitSheet(
            remainingSeconds = rateLimitState.remainingSeconds,
            onDismiss = { scanViewModel.dismissRateLimitSheet() }
        )
    }
}

/**
 * Distinct, muted per-action accent tints so the four scan actions read as
 * instantly distinguishable without turning saturated or gradient-heavy.
 * Tones are pitched slightly deeper in light mode and lighter in dark mode
 * so each glyph stays legible on the surface container card in both themes.
 */
private data class ActionAccents(
    val document: Color,
    val gallery: Color,
    val pdf: Color,
    val qr: Color
)

@Composable
private fun rememberActionAccents(): ActionAccents {
    return if (isSystemInDarkTheme()) {
        ActionAccents(
            document = Color(0xFFB6A9DC), // muted lavender
            gallery = Color(0xFFD8B878),  // muted ochre
            pdf = Color(0xFFE0A79B),      // muted clay
            qr = Color(0xFF9CC7AE)        // muted sage
        )
    } else {
        ActionAccents(
            document = Color(0xFF5E4E80),  // muted lavender
            gallery = Color(0xFF9A7B3F),  // muted ochre
            pdf = Color(0xFFA05A4E),      // muted clay
            qr = Color(0xFF4F7A63)        // muted sage
        )
    }
}
