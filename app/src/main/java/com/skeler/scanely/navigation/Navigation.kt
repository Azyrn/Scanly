package com.skeler.scanely.navigation

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import com.skeler.scanely.ocr.OcrMode
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.skeler.scanely.ocr.OcrHelper
import com.skeler.scanely.ocr.OcrResult
import com.skeler.scanely.ui.components.GalleryPicker
import com.skeler.scanely.ui.screens.CameraScreen
import com.skeler.scanely.ui.screens.HomeScreen
import com.skeler.scanely.ui.screens.ResultsScreen
import com.skeler.scanely.ui.theme.ThemeMode


object Routes {
    const val HOME = "home"
    const val CAMERA = "camera"
    const val RESULTS = "results"
    const val BARCODE_SCANNER = "barcode_scanner"
    const val SETTINGS = "settings"
}


@Composable
fun ScanelyNavigation(
    navController: NavHostController = rememberNavController(),
    currentTheme: ThemeMode = ThemeMode.System,
    onThemeChanged: (ThemeMode) -> Unit = {},
    ocrLanguages: Set<String> = setOf("eng", "ara"),
    onOcrLanguagesChanged: (Set<String>) -> Unit = {}
) {
    val context = LocalContext.current
    
    // Shared OCR state
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var ocrResult by remember { mutableStateOf<OcrResult?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("") }
    // Flag to control if OCR should run automatically when selectedImageUri changes
    var shouldAutoScan by remember { mutableStateOf(true) }
    
    val ocrHelper = remember { OcrHelper(context) }
    val historyManager = remember { com.skeler.scanely.data.HistoryManager(context) }
    
    // Auto-initialize on Language change
    LaunchedEffect(ocrLanguages) {
        if (ocrLanguages.isNotEmpty()) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                ocrHelper.reinitialize(ocrLanguages.toList())
            }
        }
    }
    
    // PDF Picker
    var isPdfProcessing by remember { mutableStateOf(false) }
    var pdfThumbnail by remember { mutableStateOf<Bitmap?>(null) }
    
    val pdfLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                isPdfProcessing = true
                isProcessing = true
                pdfThumbnail = null
                selectedImageUri = uri
                ocrResult = null
                shouldAutoScan = true // PDF processing is its own flow, but let's be consistent
                
                navController.navigate(Routes.RESULTS)
                
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        // Ensure OCR is initialized
                        if (!ocrHelper.isReady()) {
                            progressMessage = "Initializing OCR..."
                            val initSuccess = ocrHelper.initialize(ocrLanguages.toList())
                            if (!initSuccess) {
                                isProcessing = false
                                isPdfProcessing = false
                                return@launch
                            }
                        }
                        
                        val startTime = System.currentTimeMillis()
                        
                        val pdfResult = com.skeler.scanely.ocr.PdfProcessor.extractTextFromPdf(
                            context = context,
                            pdfUri = uri,
                            ocrHelper = ocrHelper,
                            enabledLanguages = ocrLanguages.toList(),
                            onProgress = { update ->
                                progressMessage = if (update.statusMessage.isNotEmpty()) {
                                    update.statusMessage
                                } else {
                                    "Processing page ${update.currentPage} of ${update.totalPages}..."
                                }
                            }
                        )
                        
                        val totalTime = System.currentTimeMillis() - startTime
                        
                        // Set thumbnail for preview
                        pdfThumbnail = pdfResult.thumbnail
                        
                        val finalResult = OcrResult(
                            text = pdfResult.text,
                            confidence = 100,
                            languages = listOf(pdfResult.detectedLanguage),
                            processingTimeMs = totalTime
                        )
                        
                        ocrResult = finalResult
                        
                        if (pdfResult.text.isNotEmpty() && !pdfResult.text.startsWith("Error")) {
                            historyManager.saveItem(pdfResult.text, uri.toString())
                        }
                        
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        isProcessing = false
                        isPdfProcessing = false
                        progressMessage = ""
                    }
                }
            }
        }
    )

    val launchGalleryPicker = GalleryPicker { uri ->
        if (uri != null) {
            shouldAutoScan = true // Explicitly user picked -> auto scan
            selectedImageUri = uri
            ocrResult = null
            navController.navigate(Routes.RESULTS)
        }
    }
    
    // Process image when selected (skip if PDF is being processed OR if shouldAutoScan is false)
    LaunchedEffect(selectedImageUri, isPdfProcessing, shouldAutoScan) {
        if (isPdfProcessing) return@LaunchedEffect
        
        // Only run if we have a URI and auto-scan is enabled
        if (shouldAutoScan) {
            selectedImageUri?.let { uri ->
                 // Skip PDFs - they're handled by pdfLauncher
                val mimeType = context.contentResolver.getType(uri)
                if (mimeType == "application/pdf") return@let
                
                isProcessing = true
                ocrResult = null
                
                val initialized = ocrHelper.initialize(ocrLanguages.toList())
                if (initialized) {
                    val result = ocrHelper.recognizeText(uri)
                    if (result != null) {
                        ocrResult = result
                        // Save to history automatically if successful
                        if (result.text.isNotEmpty()) {
                            historyManager.saveItem(result.text, uri.toString())
                        }
                    }
                }
                isProcessing = false
            }
        }
    }
    
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300)
            )
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300)
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300)
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300)
            )
        }
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                currentTheme = currentTheme,
                onThemeChanged = onThemeChanged,
                ocrLanguages = ocrLanguages,
                onOcrLanguagesChanged = onOcrLanguagesChanged,
                onCaptureClick = { navController.navigate(Routes.CAMERA) },
                onGalleryClick = { launchGalleryPicker() },
                onPdfClick = { 
                    pdfLauncher.launch(arrayOf("application/pdf"))
                },
                onBarcodeClick = { navController.navigate(Routes.BARCODE_SCANNER) },
                onHistoryClick = { navController.navigate("history") },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) }
            )
        }
        
        composable(Routes.SETTINGS) {
            com.skeler.scanely.ui.screens.SettingsScreen(
                currentTheme = currentTheme,
                onThemeChange = onThemeChanged,
                ocrLanguages = ocrLanguages,
                onOcrLanguagesChanged = onOcrLanguagesChanged,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable("history") {
            com.skeler.scanely.ui.screens.HistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onItemClick = { item ->
                    // Re-open result WITHOUT auto-scanning
                    shouldAutoScan = false // CRITICAL FIX
                    ocrResult = OcrResult(item.text, 0, emptyList(), 0L) 
                    selectedImageUri = Uri.parse(item.imageUri)
                    navController.navigate(Routes.RESULTS)
                }
            )
        }
        
        composable(Routes.CAMERA) {
            CameraScreen(
                onImageCaptured = { uri ->
                    shouldAutoScan = true // Capture -> auto scan
                    selectedImageUri = uri
                    ocrResult = null
                    navController.navigate(Routes.RESULTS) {
                        popUpTo(Routes.HOME)
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Routes.RESULTS) {
            ResultsScreen(
                imageUri = selectedImageUri,
                ocrResult = ocrResult,
                isProcessing = isProcessing,
                progressMessage = progressMessage,
                pdfThumbnail = pdfThumbnail,
                onNavigateBack = {
                    selectedImageUri = null
                    ocrResult = null
                    pdfThumbnail = null
                    shouldAutoScan = true // Reset for next time
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
                onCopyText = { /* Handled */ }
            )
        }
        
        composable(Routes.BARCODE_SCANNER) {
            com.skeler.scanely.ui.screens.BarcodeScannerScreen(
                onNavigateBack = { navController.popBackStack() },
                onBarcodeScanned = { result ->
                    // Save barcode result to history
                    val historyContent = "[${result.formatName}] ${result.displayValue}"
                    historyManager.saveItem(historyContent, "barcode://icon") 
                }
            )
        }
    }
}
