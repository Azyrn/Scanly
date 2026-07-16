@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.skeler.scanely.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import com.skeler.scanely.core.ai.AiResult
import com.skeler.scanely.core.ocr.OcrResult
import com.skeler.scanely.core.ocr.OcrSource
import com.skeler.scanely.core.text.MarkdownParser
import com.skeler.scanely.navigation.LocalNavController
import com.skeler.scanely.navigation.Routes
import com.skeler.scanely.ui.ScanViewModel
import com.skeler.scanely.ui.components.CredentialBadge
import com.skeler.scanely.ui.components.EmptyResultContent
import com.skeler.scanely.ui.components.ExportContent
import com.skeler.scanely.ui.components.ExtractedTextActions
import com.skeler.scanely.ui.components.ExtractedTextSection
import com.skeler.scanely.ui.components.LanguageChipRow
import com.skeler.scanely.ui.components.PADDLE_EXPORT_FORMATS
import com.skeler.scanely.ui.components.ProcessingContent
import com.skeler.scanely.ui.components.RateLimitSheet
import com.skeler.scanely.ui.components.ScanResultSkeleton
import com.skeler.scanely.ui.components.TEXT_EXPORT_FORMATS
import com.skeler.scanely.ui.components.TranslatingContent
import com.skeler.scanely.ui.components.TranslationLanguages
import com.skeler.scanely.ui.components.rememberExtractedTextState
import com.skeler.scanely.ui.components.rememberMarkdownPrinter
import com.skeler.scanely.ui.components.rememberTextExporter
import com.skeler.scanely.ui.components.unavailableFormats
import com.skeler.scanely.ui.viewmodel.AiScanViewModel
import com.skeler.scanely.ui.viewmodel.OcrViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val scanViewModel: ScanViewModel = hiltViewModel(activity)
    val aiViewModel: AiScanViewModel = hiltViewModel(activity)
    val ocrViewModel: OcrViewModel = hiltViewModel(activity)
    val navController = LocalNavController.current

    val scanState by scanViewModel.uiState.collectAsState()
    val aiState by aiViewModel.aiState.collectAsState()
    val ocrState by ocrViewModel.uiState.collectAsState()

    val isProcessing = aiState.isProcessing || ocrState.isProcessing
    val isTranslating = aiState.isTranslating

    val historyText = scanState.historyText
    val aiResultText = when (val result = aiState.result) {
        is AiResult.Success -> result.text
        is AiResult.Error -> "Error: ${result.message}"
        null -> null
    }
    val ocrResultText = when (val result = ocrState.result) {
        is OcrResult.Success -> result.text
        else -> null
    }
    val primaryResultText = historyText ?: aiResultText ?: ocrResultText
    val isAiResult = aiResultText != null

    val cachedLanguages = aiState.translationCache.keys.toList()
    val currentLanguage = aiState.currentLanguage
    val displayText = currentLanguage?.let { aiState.translationCache[it] } ?: primaryResultText

    // The AI answers in Markdown: "Original" reads it as plain text, "Markdown" renders it.
    var markdownView by remember { mutableStateOf(false) }

    // Markdown/JSON exports only describe data the offline engine still has (edits drop it).
    val paddleResult = (ocrState.result as? OcrResult.Success)
        ?.takeIf { it.source == OcrSource.PADDLE }
        ?.takeIf { it.blocks.isNotEmpty() || it.markdown != null }
        ?.takeIf { historyText == null && aiResultText == null && currentLanguage == null }
    val structuredMarkdown = paddleResult?.markdown?.takeIf { it.isNotBlank() }
    // Only provenance-known AI text is treated as Markdown; sniffing plain OCR text corrupts it.
    val textIsMarkdown = remember(displayText, isAiResult) {
        isAiResult && displayText?.let { MarkdownParser.looksLikeMarkdown(it) } == true
    }
    val markdownSource = structuredMarkdown ?: displayText?.takeIf { textIsMarkdown }
    val hasMarkdown = markdownSource != null
    val markdownMode = markdownView && hasMarkdown
    val shownText = remember(displayText, textIsMarkdown) {
        if (textIsMarkdown) displayText?.let(MarkdownParser::toPlainText) else displayText
    }
    val printText = rememberMarkdownPrinter()

    val exportText = rememberTextExporter()
    val exportFormats = if (paddleResult != null) PADDLE_EXPORT_FORMATS else TEXT_EXPORT_FORMATS

    val rateLimitState by scanViewModel.rateLimitState.collectAsState()
    val showRateLimitSheet by scanViewModel.showRateLimitSheet.collectAsState()
    val isRewardedAdAvailable by scanViewModel.isRewardedAdAvailable.collectAsState()
    val isOnline by scanViewModel.isOnline.collectAsState()

    var showLanguageMenu by remember { mutableStateOf(false) }
    val languages = TranslationLanguages.ALL

    var navigatingUp by remember { mutableStateOf(false) }

    val textState = rememberExtractedTextState(displayText.orEmpty())
    val onSaveExtracted: (String) -> Unit = { edited ->
        when {
            currentLanguage != null -> aiViewModel.updateText(edited)
            historyText != null -> scanViewModel.updateHistoryText(edited)
            isAiResult -> aiViewModel.updateText(edited)
            // Editing the offline engine's Markdown view feeds Markdown back, not plain text.
            markdownMode && structuredMarkdown != null -> ocrViewModel.updateMarkdown(edited)
            else -> ocrViewModel.updateText(edited)
        }
    }

    val onBack: () -> Unit = {
        if (!navigatingUp) {
            navigatingUp = true
            navController.popBackStack()
            activity.lifecycleScope.launch {
                delay(600)
                scanViewModel.clearState()
                aiViewModel.clearResult()
                ocrViewModel.clearResult()
            }
        }
    }

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

    val showRescan = displayText != null && !isProcessing && !isTranslating &&
        isAiResult && aiState.lastImageUri != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Results",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
                when {
                    aiState.isProcessing -> {
                        ProcessingContent(
                            totalFiles = aiState.totalFiles,
                            stage = aiState.stage,
                            stageMessage = aiState.stageMessage,
                            streamingText = aiState.streamingText,
                            onCancel = {
                                aiViewModel.cancelProcessing()
                                onBack()
                            }
                        )
                    }
                    isProcessing -> {
                        ScanResultSkeleton(
                            modifier = Modifier.padding(top = 8.dp),
                            showChips = false
                        )
                    }
                    isTranslating -> {
                        TranslatingContent()
                    }
                    displayText != null -> {
                        val runInfo = aiState.runInfo
                        val credential: (@Composable () -> Unit)? =
                            if (isAiResult && historyText == null && runInfo != null &&
                                aiState.result is AiResult.Success
                            ) {
                                {
                                    CredentialBadge(
                                        providerName = runInfo.provider.displayName,
                                        model = runInfo.model,
                                        usesBundledKey = runInfo.usesBundledKey
                                    )
                                }
                            } else {
                                null
                            }

                        if (isAiResult && isOnline) {
                            LanguageChipRow(
                                cachedLanguages = cachedLanguages,
                                currentLanguage = currentLanguage,
                                showLanguageMenu = showLanguageMenu,
                                onShowLanguageMenu = { showLanguageMenu = true },
                                onDismissLanguageMenu = { showLanguageMenu = false },
                                onSelectOriginal = {
                                    markdownView = false
                                    aiViewModel.showOriginal()
                                },
                                onSelectCached = {
                                    markdownView = false
                                    aiViewModel.selectCachedLanguage(it)
                                },
                                allLanguages = languages,
                                onNewLanguageSelected = { language ->
                                    showLanguageMenu = false
                                    scanViewModel.triggerAiWithRateLimit(aiState.provider) {
                                        markdownView = false
                                        aiViewModel.translateResult(language)
                                    }
                                },
                                onComposeText = { navController.navigate(Routes.TEXT_COMPOSE) },
                                showMarkdown = hasMarkdown,
                                markdownSelected = markdownMode,
                                onSelectMarkdown = { markdownView = true }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        } else if (hasMarkdown) {
                            // Offline scans have no language row; still offer the structured view.
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = !markdownMode,
                                    onClick = { markdownView = false },
                                    label = { Text("Original") }
                                )
                                FilterChip(
                                    selected = markdownMode,
                                    onClick = { markdownView = true },
                                    label = { Text("Markdown") }
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        // Copy, export, print, edit and the body all follow the visible view.
                        val visibleText = (if (markdownMode) markdownSource else shownText)
                            ?: displayText
                        val preview = remember(visibleText, markdownMode, paddleResult) {
                            ExportContent(
                                text = visibleText,
                                isMarkdown = markdownMode,
                                blocks = paddleResult?.blocks.orEmpty()
                            )
                        }
                        val disabledFormats = remember(preview) { unavailableFormats(preview) }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Notes,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.size(10.dp))
                                Text(
                                    text = "Extracted Text",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ExtractedTextActions(
                                    state = textState,
                                    text = visibleText,
                                    onCopy = { copyToClipboard(context, visibleText) },
                                    onExport = { format -> exportText(preview, format) },
                                    exportFormats = exportFormats,
                                    disabledFormats = disabledFormats,
                                    onSaveEdit = onSaveExtracted,
                                    onPrint = { printText(visibleText, markdownMode) },
                                    compact = true
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(16.dp))

                        ExtractedTextSection(
                            state = textState,
                            text = visibleText,
                            credential = credential,
                            markdown = markdownMode
                        )
                    }
                    else -> {
                        EmptyResultContent()
                    }
                }

                Spacer(modifier = Modifier.height(100.dp))
            }
        }

        if (showRescan) {
            FilledTonalIconButton(
                onClick = {
                    aiViewModel.getRescanParams()?.let { (uri, mode, provider) ->
                        scanViewModel.triggerAiWithRateLimit(provider) {
                            aiViewModel.processImage(uri, mode, provider)
                        }
                    }
                },
                shape = RoundedCornerShape(18.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 20.dp, bottom = 24.dp)
                    .size(56.dp)
            ) {
                Icon(
                    Icons.Rounded.Autorenew,
                    contentDescription = "Rescan",
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        FilledTonalIconButton(
            onClick = onBack,
            shape = RoundedCornerShape(16.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 12.dp, top = 8.dp)
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Extracted Text", text))
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}
