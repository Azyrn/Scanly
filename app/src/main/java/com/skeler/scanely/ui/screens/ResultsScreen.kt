@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.skeler.scanely.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import com.skeler.scanely.core.ai.AiResult
import com.skeler.scanely.core.ocr.OcrResult
import com.skeler.scanely.navigation.LocalNavController
import com.skeler.scanely.navigation.Routes
import com.skeler.scanely.ui.ScanViewModel
import com.skeler.scanely.ui.components.EmptyResultContent
import com.skeler.scanely.ui.components.ExtractedTextSection
import com.skeler.scanely.ui.components.LanguageChipRow
import com.skeler.scanely.ui.components.ProcessingContent
import com.skeler.scanely.ui.components.RateLimitSheet
import com.skeler.scanely.ui.components.TranslatingContent
import com.skeler.scanely.ui.components.TranslationLanguages
import com.skeler.scanely.ui.components.rememberTextExporter
import com.skeler.scanely.ui.viewmodel.AiScanViewModel
import com.skeler.scanely.ui.viewmodel.OcrViewModel
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
    val exportText = rememberTextExporter()

    val scanState by scanViewModel.uiState.collectAsState()
    val aiState by aiViewModel.aiState.collectAsState()
    val ocrState by ocrViewModel.uiState.collectAsState()
    
    // Processing state
    val isProcessing = aiState.isProcessing || ocrState.isProcessing
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
    val isRewardedAdAvailable by scanViewModel.isRewardedAdAvailable.collectAsState()
    val isOnline by scanViewModel.isOnline.collectAsState()
    
    // Language menu state
    var showLanguageMenu by remember { mutableStateOf(false) }
    val languages = TranslationLanguages.ALL

    var showContent by remember { mutableStateOf(false) }
    var navigatingUp by remember { mutableStateOf(false) }

    val onBack: () -> Unit = {
        if (!navigatingUp) {
            navigatingUp = true
            navController.popBackStack()
            // Activity scope: outlives this composable, so the cleanup still runs
            // after the pop animation instead of dying with the composition.
            activity.lifecycleScope.launch {
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

    // Surface one-shot messages (e.g. a translation failure) as a toast.
    LaunchedEffect(aiState.message) {
        aiState.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            aiViewModel.consumeMessage()
        }
    }

    BackHandler { onBack() }

    if (showRateLimitSheet) {
        RateLimitSheet(
            remainingSeconds = rateLimitState.remainingSeconds,
            onDismiss = { scanViewModel.dismissRateLimitSheet() },
            adAvailable = isRewardedAdAvailable,
            onWatchAd = { scanViewModel.showRewardedAdForExtraScan(activity) }
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
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
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
                                aiViewModel.getRescanParams()?.let { (uri, mode, provider) ->
                                    scanViewModel.triggerAiWithRateLimit(provider) {
                                        aiViewModel.processImage(uri, mode, provider)
                                    }
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Rescan")
                        }
                    }
                    
                    // Prominent, labeled copy action for one-tap copying.
                    ExtendedFloatingActionButton(
                        onClick = { copyToClipboard(context, displayText) },
                        icon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                        text = { Text("Copy") },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
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

                when {
                    isProcessing -> {
                        ProcessingContent(
                            currentFile = aiState.currentFileIndex,
                            totalFiles = aiState.totalFiles,
                            stage = aiState.stage,
                            stageMessage = aiState.stageMessage,
                            streamingText = aiState.streamingText,
                            // Only the AI pipeline is cancellable; plain OCR
                            // finishes in well under a second.
                            onCancel = if (aiState.isProcessing) {
                                {
                                    aiViewModel.cancelProcessing()
                                    onBack()
                                }
                            } else null
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
                                    scanViewModel.triggerAiWithRateLimit(aiState.provider) {
                                        aiViewModel.translateResult(language)
                                    }
                                },
                                onComposeText = { navController.navigate(Routes.TEXT_COMPOSE) }
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        ExtractedTextSection(
                            text = displayText,
                            onCopy = { copyToClipboard(context, displayText) },
                            onExport = { format -> exportText(displayText, format) },
                            onSaveEdit = { edited ->
                                when {
                                    // Editing a shown translation corrects that translation.
                                    currentLanguage != null -> aiViewModel.updateText(edited)
                                    // Reopened-from-history text persists to its row.
                                    historyText != null -> scanViewModel.updateHistoryText(edited)
                                    isAiResult -> aiViewModel.updateText(edited)
                                    else -> ocrViewModel.updateText(edited)
                                }
                            }
                        )
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

/** Copy [text] to the clipboard and show a confirmation toast. */
private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Extracted Text", text))
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}