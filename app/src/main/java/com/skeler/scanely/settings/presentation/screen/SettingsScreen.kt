@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.skeler.scanely.settings.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.FormatPaint
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.skeler.scanely.BuildConfig
import com.skeler.scanely.R
import com.skeler.scanely.core.barcode.BarcodeEngine
import com.skeler.scanely.core.ocr.TextOcrEngine
import com.skeler.scanely.navigation.LocalNavController
import com.skeler.scanely.navigation.Routes
import com.skeler.scanely.settings.data.SettingsKeys
import com.skeler.scanely.settings.presentation.viewmodel.SettingsViewModel
import com.skeler.scanely.ui.components.SettingsItem
import com.skeler.scanely.ui.components.SettingsListSection

private const val SOURCE_URL = "https://github.com/Azyrn/Scanly"
private const val TELEGRAM_URL = "https://telegram.me/ScanlyOCR"

@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val navController = LocalNavController.current
    val uriHandler = LocalUriHandler.current

    val engineId by settingsViewModel.getString(SettingsKeys.BARCODE_ENGINE)
        .collectAsState(initial = BarcodeEngine.DEFAULT.id)
    val barcodeEngine = BarcodeEngine.fromId(engineId)
    var showEngineDialog by remember { mutableStateOf(false) }

    val textEngineId by settingsViewModel.getString(SettingsKeys.TEXT_OCR_ENGINE)
        .collectAsState(initial = TextOcrEngine.DEFAULT.id)
    val textEngine = TextOcrEngine.fromId(textEngineId)

    val contributors = remember {
        listOf(
            Contributor("Azyrn", "Azyrn", "Developer"),
            Contributor("DP-Hridayan", "DP-Hridayan", "Contributor")
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            item {
                SettingsListSection(
                    title = "Customize",
                    items = listOf(
                        SettingsItem(
                            title = "Look & Feel",
                            subtitle = "Color palette, dark theme, pure black",
                            icon = Icons.Rounded.FormatPaint,
                            onClick = { navController.navigate(Routes.LOOK_AND_FEEL) }
                        ),
                        SettingsItem(
                            title = "AI Providers",
                            subtitle = "API keys for Gemini & OpenRouter",
                            icon = Icons.Rounded.Hub,
                            onClick = { navController.navigate(Routes.AI_PROVIDERS) }
                        ),
                        SettingsItem(
                            title = "Text Recognition",
                            subtitle = "${textEngine.displayName} · scripts & preprocessing",
                            icon = Icons.Rounded.TextFields,
                            onClick = { navController.navigate(Routes.TEXT_RECOGNITION) }
                        ),
                        SettingsItem(
                            title = "Barcode Scanner Engine",
                            subtitle = barcodeEngine.displayName,
                            icon = Icons.Rounded.QrCodeScanner,
                            onClick = { showEngineDialog = true }
                        )
                    )
                )
            }

            item {
                SettingsListSection(
                    title = "About",
                    items = listOf(
                        SettingsItem(
                            title = "Source code",
                            subtitle = "github.com/Azyrn/Scanly",
                            iconRes = R.drawable.ic_github,
                            trailingIcon = Icons.AutoMirrored.Rounded.OpenInNew,
                            onClick = { uriHandler.openUri(SOURCE_URL) }
                        ),
                        SettingsItem(
                            title = "Telegram",
                            subtitle = "Report bugs or request features",
                            iconRes = R.drawable.ic_telegram,
                            trailingIcon = Icons.AutoMirrored.Rounded.OpenInNew,
                            onClick = { uriHandler.openUri(TELEGRAM_URL) }
                        )
                    )
                )
            }

            item {
                SettingsListSection(
                    title = "Contributors",
                    items = contributors.map { contributor ->
                        SettingsItem(
                            title = contributor.displayName,
                            subtitle = "@${contributor.username} · ${contributor.role}",
                            avatarUrl = contributor.avatarUrl,
                            trailingIcon = Icons.AutoMirrored.Rounded.OpenInNew,
                            onClick = { uriHandler.openUri(contributor.githubUrl) }
                        )
                    },
                    trailing = { CountBadge(contributors.size) }
                )
            }

            item {
                Text(
                    text = "Scanly ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
            }
        }
    }

    if (showEngineDialog) {
        BarcodeEngineDialog(
            current = barcodeEngine,
            onSelect = { engine ->
                settingsViewModel.setString(SettingsKeys.BARCODE_ENGINE, engine.id)
                showEngineDialog = false
            },
            onDismiss = { showEngineDialog = false }
        )
    }
}

@Composable
private fun CountBadge(count: Int) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun BarcodeEngineDialog(
    current: BarcodeEngine,
    onSelect: (BarcodeEngine) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.QrCodeScanner, contentDescription = null) },
        title = { Text("Barcode Scanner Engine") },
        text = {
            Column {
                BarcodeEngine.entries.forEach { engine ->
                    val description = when (engine) {
                        BarcodeEngine.ML_KIT -> "Google's on-device scanner with auto-zoom"
                        BarcodeEngine.ZXING_CPP -> "Open-source decoder, works without GMS"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onSelect(engine) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = engine == current,
                            onClick = { onSelect(engine) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = engine.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

data class Contributor(
    val username: String,
    val displayName: String,
    val role: String,
    val githubUrl: String = "https://github.com/$username",
    val avatarUrl: String = "https://github.com/$username.png"
)
