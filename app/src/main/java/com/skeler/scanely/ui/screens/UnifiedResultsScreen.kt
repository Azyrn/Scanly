@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.skeler.scanely.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.skeler.scanely.core.actions.ActionExecutor
import com.skeler.scanely.core.actions.ScanAction
import com.skeler.scanely.navigation.LocalNavController
import com.skeler.scanely.ui.components.ExportContent
import com.skeler.scanely.ui.components.ExtractedTextActions
import com.skeler.scanely.ui.components.ExtractedTextSection
import com.skeler.scanely.ui.components.ScanResultSkeleton
import com.skeler.scanely.ui.components.PADDLE_EXPORT_FORMATS
import com.skeler.scanely.ui.components.TEXT_EXPORT_FORMATS
import com.skeler.scanely.ui.components.rememberExtractedTextState
import com.skeler.scanely.ui.components.rememberTextExporter
import com.skeler.scanely.ui.components.unavailableFormats
import com.skeler.scanely.ui.viewmodel.UnifiedScanViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedResultsScreen() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val unifiedViewModel: UnifiedScanViewModel = hiltViewModel(activity)
    val navController = LocalNavController.current

    val uiState by unifiedViewModel.uiState.collectAsState()
    val exportText = rememberTextExporter()
    val exportFormats = if (uiState.isOfflineOcr) PADDLE_EXPORT_FORMATS else TEXT_EXPORT_FORMATS

    // The offline engine's structured Markdown is a second view of the same scan.
    val markdownSource = uiState.markdown?.takeIf { it.isNotBlank() }
    var markdownView by remember { mutableStateOf(false) }
    val markdownMode = markdownView && markdownSource != null

    val extracted = uiState.extractedText.orEmpty()
    val visibleText = if (markdownMode) markdownSource.orEmpty() else extracted
    val preview = remember(visibleText, markdownMode, uiState.textBlocks) {
        ExportContent(
            text = visibleText,
            isMarkdown = markdownMode,
            blocks = uiState.textBlocks
        )
    }
    val disabledFormats = remember(preview) { unavailableFormats(preview) }

    val textState = rememberExtractedTextState(visibleText)
    val onCopyExtracted = {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Extracted Text", visibleText))
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    val onBack: () -> Unit = {
        unifiedViewModel.clearResult()
        navController.popBackStack()
    }

    // Merge barcode + smart-text actions; drop Copy/ShowRaw; distinct, max 6.
    val quickActions = remember(uiState.barcodeActions, uiState.textActions) {
        val smart = uiState.textActions
            .filter { it !is ScanAction.CopyText && it !is ScanAction.ShowRaw }
        (uiState.barcodeActions + smart)
            .distinctBy { action ->
                when (action) {
                    is ScanAction.OpenUrl -> "url:${action.url}"
                    is ScanAction.CallPhone -> "call:${action.number}"
                    is ScanAction.SendEmail -> "email:${action.email}"
                    is ScanAction.SendSms -> "sms:${action.number}"
                    is ScanAction.ConnectWifi -> "wifi:${action.ssid}"
                    is ScanAction.AddContact -> "contact:${action.name}"
                    else -> action.label
                }
            }
            .take(6)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
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
                uiState.isLoading -> {
                    ScanResultSkeleton(modifier = Modifier.padding(top = 8.dp))
                }
                uiState.isEmpty -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No text or barcodes detected",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    Spacer(modifier = Modifier.height(4.dp))

                    if (markdownSource != null && uiState.hasText) {
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

                    val hasActions = quickActions.isNotEmpty()
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
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Extracted Text",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (uiState.hasText) {
                                ExtractedTextActions(
                                    state = textState,
                                    text = visibleText,
                                    onCopy = onCopyExtracted,
                                    onExport = { exportText(preview, it) },
                                    exportFormats = exportFormats,
                                    disabledFormats = disabledFormats,
                                    onSaveEdit = { unifiedViewModel.updateExtractedText(it) },
                                    compact = hasActions,
                                    leading = if (hasActions) {
                                        {
                                            ActionsButton(
                                                actions = quickActions,
                                                onExecute = { ActionExecutor.execute(context, it) },
                                                compact = true
                                            )
                                        }
                                    } else null
                                )
                            } else if (hasActions) {
                                ActionsButton(
                                    actions = quickActions,
                                    onExecute = { ActionExecutor.execute(context, it) }
                                )
                            }
                        }
                    }

                    if (uiState.hasText) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        ExtractedTextSection(
                            state = textState,
                            text = visibleText,
                            markdown = markdownMode
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
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

@Composable
private fun ActionsButton(
    actions: List<ScanAction>,
    onExecute: (ScanAction) -> Unit,
    compact: Boolean = false
) {
    Box {
        var open by remember { mutableStateOf(false) }
        FilledTonalIconButton(
            onClick = { open = true },
            modifier = if (compact) Modifier.size(34.dp) else Modifier,
            shape = RoundedCornerShape(14.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Icon(
                Icons.Rounded.MoreHoriz,
                contentDescription = "Actions",
                modifier = Modifier.size(if (compact) 18.dp else 20.dp)
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    leadingIcon = {
                        Icon(action.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    onClick = {
                        open = false
                        onExecute(action)
                    }
                )
            }
        }
    }
}
