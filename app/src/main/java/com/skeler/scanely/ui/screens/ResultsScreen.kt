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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import com.skeler.scanely.core.ai.AiResult
import com.skeler.scanely.core.ocr.OcrResult
import com.skeler.scanely.navigation.LocalNavController
import com.skeler.scanely.navigation.Routes
import com.skeler.scanely.ui.ScanViewModel
import com.skeler.scanely.ui.components.CredentialBadge
import com.skeler.scanely.ui.components.ExtractedTextActions
import com.skeler.scanely.ui.components.rememberExtractedTextState
import com.skeler.scanely.ui.components.EmptyResultContent
import com.skeler.scanely.ui.components.ExtractedTextSection
import com.skeler.scanely.ui.components.LanguageChipRow
import com.skeler.scanely.ui.components.ProcessingContent
import com.skeler.scanely.ui.components.RateLimitSheet
import com.skeler.scanely.ui.components.ScanResultSkeleton
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

    var navigatingUp by remember { mutableStateOf(false) }

    // Hoisted so the copy/export/edit actions can live in the header while the
    // body renders below; keyed on the shown text.
    val textState = rememberExtractedTextState(displayText.orEmpty())
    val onSaveExtracted: (String) -> Unit = { edited ->
        when {
            // Editing a shown translation corrects that translation.
            currentLanguage != null -> aiViewModel.updateText(edited)
            // Reopened-from-history text persists to its row.
            historyText != null -> scanViewModel.updateHistoryText(edited)
            isAiResult -> aiViewModel.updateText(edited)
            else -> ocrViewModel.updateText(edited)
        }
    }

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
            // Title scrolls with content; the floating back button overlays it,
            // matching the offline image screen.
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
                // AI pipeline keeps its staged/streaming progress UI; plain
                // OCR/PDF extraction shows a result-shaped skeleton instead
                // of a spinner.
                aiState.isProcessing -> {
                    ProcessingContent(
                        currentFile = aiState.currentFileIndex,
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
                    // Which credentials served this scan — only for a fresh AI
                    // result (history reopens don't know what produced them).
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
                        } else null

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
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // "Extracted Text" pinned left, edit / export / copy opposite.
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
                                text = displayText,
                                onCopy = { copyToClipboard(context, displayText) },
                                onExport = { format -> exportText(displayText, format) },
                                onSaveEdit = onSaveExtracted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(16.dp))

                    ExtractedTextSection(
                        state = textState,
                        text = displayText,
                        credential = credential
                    )
                }
                else -> {
                    EmptyResultContent()
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
          }
        }

        // Rescan (rate-limited) floats bottom-end over the scrolling text.
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

        // Floating back hovers over the scrolling content, matching the offline
        // image results screen.
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

/** Copy [text] to the clipboard and show a confirmation toast. */
private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Extracted Text", text))
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}
