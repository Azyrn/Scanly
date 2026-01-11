package com.skeler.scanely.ui.screens

/**
 * Clean & Simple HomeScreen
 * - Center: 3 Main Actions (Camera, Gallery, PDF)
 * - Top: App Bar with Settings
 * - Bottom: History Link + AI Scan FAB
 * - Settings: Theme Selection + Developer Info
 */
import android.Manifest
import android.content.pm.PackageManager
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.skeler.scanely.core.ai.AiMode
import com.skeler.scanely.core.ai.AiResult
import com.skeler.scanely.navigation.LocalNavController
import com.skeler.scanely.navigation.Routes
import com.skeler.scanely.ui.ScanViewModel
import com.skeler.scanely.ui.components.GalleryPicker
import com.skeler.scanely.ui.components.RateLimitSheet
import com.skeler.scanely.ui.viewmodel.AiScanViewModel
import com.skeler.scanely.ui.viewmodel.OcrViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val scanViewModel: ScanViewModel = hiltViewModel(activity)
    val aiViewModel: AiScanViewModel = hiltViewModel(activity)
    val ocrViewModel: OcrViewModel = hiltViewModel(activity)
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    // Snackbar state
    val snackbarHostState = remember { SnackbarHostState() }

    // AI bottom sheet state
    var showAiBottomSheet by remember { mutableStateOf(false) }
    val aiSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // AI state
    val aiState by aiViewModel.aiState.collectAsState()

    // Rate Limit state for modal sheet
    val showRateLimitSheet by scanViewModel.showRateLimitSheet.collectAsState()
    val rateLimitState by scanViewModel.rateLimitState.collectAsState()

    // Auto-dismiss rate limit sheet when cooldown completes
    LaunchedEffect(rateLimitState.remainingSeconds, rateLimitState.justBecameReady) {
        if (rateLimitState.remainingSeconds == 0 && rateLimitState.justBecameReady) {
            scanViewModel.dismissRateLimitSheet()
        }
    }

    // Selected AI mode for gallery picker
    var pendingAiMode by remember { mutableStateOf<AiMode?>(null) }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                showAiBottomSheet = true
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar("Camera permission denied")
                }
            }
        }
    )

    // Gallery picker for AI scan (images) - triggers immediately with rate limit check
    val aiGalleryPicker = GalleryPicker { uri ->
        if (uri != null && pendingAiMode != null) {
            val mode = pendingAiMode!!
            pendingAiMode = null
            
            // Check rate limit - 2 free requests, then 60s cooldown
            // Only navigate if request is allowed
            val allowed = scanViewModel.triggerAiWithRateLimit {
                // Rate limit allows - trigger API immediately
                scanViewModel.onImageSelected(uri)
                aiViewModel.processImage(uri, mode)
            }
            
            // Only navigate if request was allowed
            if (allowed) {
                navController.navigate(Routes.RESULTS)
            }
        }
    }

    // Document picker for AI scan (PDF, DOCX, PPTX, etc.)
    val aiDocumentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null && pendingAiMode != null) {
                val mode = pendingAiMode!!
                pendingAiMode = null
                
                // Take persistable permission
                try {
                    val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                } catch (e: Exception) {
                    // Ignore if permission taking fails
                }
                
                // Check rate limit
                val allowed = scanViewModel.triggerAiWithRateLimit {
                    scanViewModel.onPdfSelected(uri)
                    aiViewModel.processImage(uri, mode)
                }
                
                if (allowed) {
                    navController.navigate(Routes.RESULTS)
                }
            }
        }
    )

    val launchGalleryPicker = GalleryPicker { uri ->
        if (uri != null) {
            scanViewModel.onImageSelected(uri)
            ocrViewModel.processImage(uri)
            navController.navigate(Routes.RESULTS)
        }
    }

    // PDF Picker
    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                // Take persistable permission to access content later
                try {
                    val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                } catch (e: Exception) {
                    // Ignore if permission taking fails (might be temporary access)
                }
                
                scanViewModel.onPdfSelected(uri)
                ocrViewModel.processPdf(uri)
                navController.navigate(Routes.RESULTS)
            }
        }
    )

    // Handle AI FAB click
    fun onAiFabClick() {
        val cameraPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        )
        if (cameraPermission == PackageManager.PERMISSION_GRANTED) {
            showAiBottomSheet = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Handle AI mode selection - use correct picker based on mode
    fun onAiModeSelected(mode: AiMode) {
        pendingAiMode = mode
        showAiBottomSheet = false
        
        when (mode) {
            AiMode.EXTRACT_TEXT -> {
                // Use photo gallery for images
                aiGalleryPicker()
            }
            AiMode.EXTRACT_PDF_TEXT -> {
                // Use document picker for PDF and text files (Gemini supported)
                aiDocumentPicker.launch(arrayOf(
                    "application/pdf",
                    "text/plain"
                ))
            }
            else -> {
                // Default to image picker
                aiGalleryPicker()
            }
        }
    }

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
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            GamifiedAiFab(
                scanViewModel = scanViewModel,
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

            // History Link
            FilledTonalButton(
                onClick = { navController.navigate(Routes.HISTORY) },
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "History",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("View Previous Extracts")
            }
        }
    }

    // AI Mode Selection Bottom Sheet
    if (showAiBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAiBottomSheet = false },
            sheetState = aiSheetState
        ) {
            Column(
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                Text(
                    text = "AI Scan",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )

                AiModeItem(
                    icon = Icons.AutoMirrored.Filled.TextSnippet,
                    title = "Extract Text",
                    subtitle = "Extract visible text from image",
                    onClick = { onAiModeSelected(AiMode.EXTRACT_TEXT) }
                )

                AiModeItem(
                    icon = Icons.Outlined.PictureAsPdf,
                    title = "Extract PDF",
                    subtitle = "AI-powered PDF and text file extraction",
                    onClick = { onAiModeSelected(AiMode.EXTRACT_PDF_TEXT) }
                )
            }
        }
    }

    // Rate Limit Sheet Modal
    if (showRateLimitSheet) {
        RateLimitSheet(
            remainingSeconds = rateLimitState.remainingSeconds,
            onDismiss = { scanViewModel.dismissRateLimitSheet() }
        )
    }
}

@Composable
private fun AiModeItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            },
            supportingContent = {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingContent = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        )
    }
}

@Composable
private fun MainActionButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    countdownSeconds: Int = 0
) {
    val actualSubtitle = if (countdownSeconds > 0) {
        "Wait ${countdownSeconds}s..."
    } else {
        subtitle
    }
    
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                }
            )
            Spacer(modifier = Modifier.size(20.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = actualSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (countdownSeconds > 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

/**
 * Gamified AI FAB with recharging animation.
 *
 * Deep Reasoning (ULTRATHINK):
 * - Psychological: "Recharging" feels active vs "Disabled" feels punitive
 * - CircularProgressIndicator around FAB shows time investment paying off
 * - Haptic feedback on ready creates Pavlovian positive association
 */
@Composable
private fun GamifiedAiFab(
    scanViewModel: ScanViewModel,
    onClick: () -> Unit
) {
    val view = LocalView.current
    val rateLimitState by scanViewModel.rateLimitState.collectAsState()

    val isRecharging = rateLimitState.remainingSeconds > 0
    val progress = rateLimitState.progress

    // Haptic feedback when becoming ready
    LaunchedEffect(rateLimitState.justBecameReady) {
        if (rateLimitState.justBecameReady) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
    }

    // Gamified FAB with progress ring
    Box(contentAlignment = Alignment.Center) {
        // Progress ring (behind FAB)
        if (isRecharging) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(72.dp),
                strokeWidth = 4.dp,
                strokeCap = StrokeCap.Round,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // FAB
        LargeFloatingActionButton(
            onClick = {
                if (!isRecharging) {
                    onClick()
                }
            },
            containerColor = if (isRecharging) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            contentColor = if (isRecharging) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
            modifier = Modifier.size(if (isRecharging) 56.dp else 64.dp)
        ) {
            if (isRecharging) {
                Text(
                    text = "${rateLimitState.remainingSeconds}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Scan",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
