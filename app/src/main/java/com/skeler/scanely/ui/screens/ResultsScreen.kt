@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.skeler.scanely.ui.screens

import android.content.ClipData
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.skeler.scanely.core.ai.AiResult
import com.skeler.scanely.core.ocr.OcrResult
import com.skeler.scanely.navigation.LocalNavController
import com.skeler.scanely.ui.ScanViewModel
import com.skeler.scanely.ui.components.EmptyResultContent
import com.skeler.scanely.ui.components.LanguageChipRow
import com.skeler.scanely.ui.components.ProcessingContent
import com.skeler.scanely.ui.components.RateLimitSheet
import com.skeler.scanely.ui.components.ReadableTextContent
import com.skeler.scanely.ui.components.TranslatingContent
import com.skeler.scanely.ui.viewmodel.AiScanViewModel
import com.skeler.scanely.ui.viewmodel.OcrViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Wikipedia-style Results Screen - Orchestration Layer Only
 * 
 * Components extracted to:
 * - LanguageChipRow.kt
 * - TextDisplayComponents.kt (ReadableTextContent, ProcessingContent, etc.)
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
    
    // Processing state
    val isProcessing = scanState.isProcessing || aiState.isProcessing || ocrState.isProcessing
    val isTranslating = aiState.isTranslating
    
    // Text priority: History > AI > OCR
    val historyText = scanState.historyText
    val aiResultText = when (val result = aiState.result) {
        is AiResult.Success -> result.text
        is AiResult.Error -> "Error: ${result.message}"
        is AiResult.RateLimited -> "Rate limited. Wait ${result.remainingMs / 1000}s"
        null -> null
    }
    val ocrResultText = when (val result = ocrState.result) {
        is OcrResult.Success -> result.text
        else -> null
    }
    val primaryResultText = historyText ?: aiResultText ?: ocrResultText
    val isAiResult = aiResultText != null
    
    // Translation state
    val cachedLanguages = aiState.translationCache.keys.toList()
    val currentLanguage = aiState.currentLanguage
    val displayText = currentLanguage?.let { aiState.translationCache[it] } ?: primaryResultText

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

    // Auto-dismiss rate limit sheet
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (displayText != null && !isProcessing && !isTranslating) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Rescan FAB (rate-limited)
                    if (isAiResult && aiState.lastImageUri != null) {
                        FloatingActionButton(
                            onClick = {
                                aiViewModel.getRescanParams()?.let { (uri, mode) ->
                                    scanViewModel.triggerAiWithRateLimit {
                                        aiViewModel.processImage(uri, mode)
                                    }
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Rescan")
                        }
                    }
                    
                    // Copy FAB
                    FloatingActionButton(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                            clipboard.setPrimaryClip(ClipData.newPlainText("Extracted Text", displayText))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy All")
                    }
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

                when {
                    isProcessing -> {
                        ProcessingContent(
                            currentFile = aiState.currentFileIndex,
                            totalFiles = aiState.totalFiles
                        )
                    }
                    isTranslating -> {
                        TranslatingContent()
                    }
                    displayText != null -> {
                        if (isAiResult && isOnline) {
                            LanguageChipRow(
                                cachedLanguages = cachedLanguages,
                                currentLanguage = currentLanguage,
                                showLanguageMenu = showLanguageMenu,
                                onShowLanguageMenu = { showLanguageMenu = true },
                                onDismissLanguageMenu = { showLanguageMenu = false },
                                onSelectOriginal = { aiViewModel.showOriginal() },
                                onSelectCached = { aiViewModel.selectCachedLanguage(it) },
                                allLanguages = languages,
                                onNewLanguageSelected = { language ->
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
                        EmptyResultContent()
                    }
                }

                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}