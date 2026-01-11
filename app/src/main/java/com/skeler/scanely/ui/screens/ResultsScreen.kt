@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.skeler.scanely.ui.screens

import android.content.ClipData
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.skeler.scanely.core.ai.AiResult
import com.skeler.scanely.core.ocr.OcrResult
import com.skeler.scanely.navigation.LocalNavController
import com.skeler.scanely.navigation.Routes
import com.skeler.scanely.ui.ScanViewModel
import com.skeler.scanely.ui.components.RateLimitSheet
import com.skeler.scanely.ui.viewmodel.AiScanViewModel
import com.skeler.scanely.ui.viewmodel.OcrViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Wikipedia-style Results Screen
 * 
 * Design: Clean, distraction-free reading experience
 * - Hero image at top with caption
 * - Flowing text below (no cards, no borders)
 * - Full text selectable
 * - Translate button for AI results
 * - FAB for copy all
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val scanViewModel: ScanViewModel = hiltViewModel(activity)
    val aiViewModel: AiScanViewModel = hiltViewModel(activity)
    val ocrViewModel: OcrViewModel = hiltViewModel(activity)
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    val scanState by scanViewModel.uiState.collectAsState()
    val aiState by aiViewModel.aiState.collectAsState()
    val ocrState by ocrViewModel.uiState.collectAsState()
    
    val imageUri = scanState.selectedImageUri
    val pdfThumbnail = scanState.pdfThumbnail
    
    // Processing state
    val isProcessing = scanState.isProcessing || aiState.isProcessing || ocrState.isProcessing
    val isTranslating = aiState.isTranslating
    
    // History text (restored from history - highest priority)
    val historyText = scanState.historyText

    // AI result text
    val aiResultText = when (val result = aiState.result) {
        is AiResult.Success -> result.text
        is AiResult.Error -> "Error: ${result.message}"
        is AiResult.RateLimited -> "Rate limited. Wait ${result.remainingMs / 1000}s"
        null -> null
    }
    
    // OCR result text (on-device ML Kit) - FREE, UNLIMITED
    val ocrResultText = when (val result = ocrState.result) {
        is OcrResult.Success -> result.text
        is OcrResult.Error -> null
        is OcrResult.Empty -> null
        null -> null
    }
    
    // Priority: History > AI > OCR
    val primaryResultText = historyText ?: aiResultText ?: ocrResultText
    
    // Track if result is from AI (only AI results can be translated)
    val isAiResult = aiResultText != null
    
    // Display translated text if available, otherwise original
    val displayText = aiState.translatedText ?: primaryResultText
    val hasTranslation = aiState.translatedText != null

    // Rate Limit & Network State
    val rateLimitState by scanViewModel.rateLimitState.collectAsState()
    val showRateLimitSheet by scanViewModel.showRateLimitSheet.collectAsState()
    val isOnline by scanViewModel.isOnline.collectAsState()
    
    // Language menu state
    var showLanguageMenu by remember { mutableStateOf(false) }
    val languages = listOf(
        "English", "Spanish", "French", "German", "Italian", 
        "Portuguese", "Russian", "Japanese", "Korean", "Chinese",
        "Arabic", "Hindi", "Turkish", "Dutch", "Polish"
    )

    // Auto-dismiss rate limit sheet when cooldown completes
    LaunchedEffect(rateLimitState.remainingSeconds, rateLimitState.justBecameReady) {
        if (rateLimitState.remainingSeconds == 0 && rateLimitState.justBecameReady) {
            scanViewModel.dismissRateLimitSheet()
        }
    }

    var showContent by remember { mutableStateOf(false) }
    var navigatingUp by remember { mutableStateOf(false) }

    val onBack: () -> Unit = {
        if (!navigatingUp) {
            navigatingUp = true
            // Go back to previous screen (History or Home)
            navController.popBackStack()
            scope.launch(Dispatchers.Default) {
                delay(600)
                scanViewModel.clearState()
                aiViewModel.clearResult()
                ocrViewModel.clearResult()
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        showContent = true
    }

    BackHandler { onBack() }

    // Rate Limit Sheet Modal
    if (showRateLimitSheet) {
        RateLimitSheet(
            remainingSeconds = rateLimitState.remainingSeconds,
            onDismiss = { scanViewModel.dismissRateLimitSheet() }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Results",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (displayText != null && !isProcessing && !isTranslating) {
                FloatingActionButton(
                    onClick = {
                        val clipboardManager = context.getSystemService(android.content.ClipboardManager::class.java)
                        val clipData = ClipData.newPlainText("Extracted Text", displayText)
                        clipboardManager.setPrimaryClip(clipData)
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy All"
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn(tween(300))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Hero Image (Wikipedia style - rounded, with caption)
                if (imageUri != null || pdfThumbnail != null) {
                    HeroImage(
                        imageUri = imageUri,
                        pdfThumbnail = pdfThumbnail
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Content Area
                when {
                    isProcessing -> {
                        ProcessingContent()
                    }
                    isTranslating -> {
                        TranslatingContent()
                    }
                    displayText != null -> {
                        // Translate/Revert buttons (for AI results only)
                        if (isAiResult && isOnline) {
                            TranslateActions(
                                hasTranslation = hasTranslation,
                                showLanguageMenu = showLanguageMenu,
                                onShowLanguageMenu = { showLanguageMenu = true },
                                onDismissLanguageMenu = { showLanguageMenu = false },
                                onRevert = { aiViewModel.clearTranslation() },
                                languages = languages,
                                onLanguageSelected = { language ->
                                    showLanguageMenu = false
                                    scanViewModel.triggerAiWithRateLimit {
                                        aiViewModel.translateResult(language)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        
                        ReadableTextContent(text = displayText)
                    }
                    else -> {
                        EmptyContent()
                    }
                }

                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

/**
 * Translate/Revert action buttons
 */
@Composable
private fun TranslateActions(
    hasTranslation: Boolean,
    showLanguageMenu: Boolean,
    onShowLanguageMenu: () -> Unit,
    onDismissLanguageMenu: () -> Unit,
    onRevert: () -> Unit,
    languages: List<String>,
    onLanguageSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (hasTranslation) {
            // Revert button
            OutlinedButton(
                onClick = onRevert,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Show Original")
            }
        } else {
            // Translate button with dropdown
            Box(modifier = Modifier.weight(1f)) {
                FilledTonalButton(
                    onClick = onShowLanguageMenu,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Translate")
                }
                
                DropdownMenu(
                    expanded = showLanguageMenu,
                    onDismissRequest = onDismissLanguageMenu
                ) {
                    languages.forEach { language ->
                        DropdownMenuItem(
                            text = { Text(language) },
                            onClick = { onLanguageSelected(language) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Wikipedia-style hero image with caption
 */
@Composable
private fun HeroImage(
    imageUri: Uri?,
    pdfThumbnail: Bitmap?
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
        ) {
            when {
                pdfThumbnail != null -> {
                    Image(
                        bitmap = pdfThumbnail.asImageBitmap(),
                        contentDescription = "Source image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentScale = ContentScale.Crop
                    )
                }
                imageUri != null -> {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUri)
                            .crossfade(true)
                            .diskCacheKey(imageUri.toString())
                            .memoryCacheKey(imageUri.toString())
                            .build(),
                        contentDescription = "Source image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        // Caption (like Wikipedia image captions)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Source image",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Wikipedia-style readable text - clean, flowing paragraphs
 */
@Composable
private fun ReadableTextContent(text: String) {
    val customSelectionColors = TextSelectionColors(
        handleColor = MaterialTheme.colorScheme.primary,
        backgroundColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    )

    CompositionLocalProvider(LocalTextSelectionColors provides customSelectionColors) {
        SelectionContainer {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    // Wikipedia-style typography
                    lineHeight = 28.sp,
                    letterSpacing = 0.02.sp,
                    textDirection = TextDirection.Content
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Processing indicator
 */
@Composable
private fun ProcessingContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularWavyProgressIndicator(modifier = Modifier.size(56.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Extracting text...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "This may take a few moments",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

/**
 * Translating indicator
 */
@Composable
private fun TranslatingContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Translating...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Empty state
 */
@Composable
private fun EmptyContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No text extracted",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Try selecting a different image",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
    }
}