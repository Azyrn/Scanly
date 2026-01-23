@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.skeler.scanely.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.skeler.scanely.core.actions.ActionExecutor
import com.skeler.scanely.core.actions.ScanAction
import com.skeler.scanely.navigation.LocalNavController
import com.skeler.scanely.ui.ScanViewModel
import com.skeler.scanely.ui.viewmodel.UnifiedScanViewModel

/**
 * Unified Results Screen with separated sections for:
 * - Text OCR results
 * - Barcode/QR detection results
 * - Smart actions from OCR text
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedResultsScreen() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val scanViewModel: ScanViewModel = hiltViewModel(activity)
    val unifiedViewModel: UnifiedScanViewModel = hiltViewModel(activity)
    val navController = LocalNavController.current

    val uiState by unifiedViewModel.uiState.collectAsState()
    val scanState by scanViewModel.uiState.collectAsState()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Scan Results") },
                navigationIcon = {
                    IconButton(onClick = { 
                        unifiedViewModel.clearResult()
                        navController.popBackStack() 
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Scanning...", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            uiState.isEmpty -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
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
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Image preview
                    scanState.selectedImageUri?.let { uri ->
                        item {
                            Card(
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                )
                            ) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Scanned image",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }

                    // Barcode Results Section
                    if (uiState.hasBarcodes) {
                        item {
                            SectionHeader(
                                icon = Icons.Default.QrCode2,
                                title = "Barcodes Detected"
                            )
                        }
                        
                        items(uiState.barcodeActions) { action ->
                            BarcodeActionCard(
                                action = action,
                                onClick = { ActionExecutor.execute(context, action) }
                            )
                        }
                    }

                    // Smart Actions from Text (filtered: no Copy, unique only, max 3)
                    val filteredActions = uiState.textActions
                        .filter { it !is ScanAction.CopyText && it !is ScanAction.ShowRaw }
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
                        .take(3)
                    
                    if (filteredActions.isNotEmpty()) {
                        item {
                            Text(
                                text = "Quick Actions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredActions) { action ->
                                    SmartActionChip(
                                        action = action,
                                        onClick = { ActionExecutor.execute(context, action) }
                                    )
                                }
                            }
                        }
                    }

                    // Text Results Section
                    if (uiState.hasText) {
                        item {
                            SectionHeader(
                                icon = Icons.AutoMirrored.Filled.TextSnippet,
                                title = "Extracted Text"
                            )
                        }
                        
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    SelectionContainer {
                                        Text(
                                            text = uiState.extractedText ?: "",
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    FilledTonalButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                                            val clip = ClipData.newPlainText("Extracted Text", uiState.extractedText)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Copy All Text")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BarcodeActionCard(
    action: ScanAction,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = action.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = getActionSubtitle(action),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SmartActionChip(
    action: ScanAction,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(action.label)
    }
}

private fun getActionSubtitle(action: ScanAction): String {
    return when (action) {
        is ScanAction.OpenUrl -> action.url.take(50)
        is ScanAction.CopyText -> action.text.take(50)
        is ScanAction.CallPhone -> action.number
        is ScanAction.SendEmail -> action.email
        is ScanAction.ConnectWifi -> action.ssid
        is ScanAction.SendSms -> action.number
        is ScanAction.AddContact -> action.name ?: "Contact"
        is ScanAction.ShowRaw -> action.text.take(50)
        is ScanAction.LookupProduct -> "Barcode: ${action.barcode}"
    }
}
