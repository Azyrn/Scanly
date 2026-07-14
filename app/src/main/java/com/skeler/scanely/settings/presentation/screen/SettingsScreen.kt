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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.skeler.scanely.BuildConfig
import com.skeler.scanely.R
import com.skeler.scanely.core.barcode.BarcodeEngine
import com.skeler.scanely.core.ocr.TextOcrEngine
import com.skeler.scanely.navigation.LocalNavController
import com.skeler.scanely.navigation.Routes
import com.skeler.scanely.settings.data.SettingsKeys
import com.skeler.scanely.settings.presentation.viewmodel.SettingsViewModel
import com.skeler.scanely.ui.components.SettingsGroup
import com.skeler.scanely.ui.components.SettingsNavTile
import com.skeler.scanely.ui.components.SettingsSectionHeader
import com.skeler.scanely.ui.components.SettingsTileDivider

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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
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
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 48.dp)
        ) {
            item {
                SettingsSectionHeader(
                    text = "Customize",
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingsGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SettingsNavTile(
                        title = "Look & Feel",
                        subtitle = "Color palette, dark theme, pure black",
                        icon = Icons.Rounded.FormatPaint,
                        onClick = { navController.navigate(Routes.LOOK_AND_FEEL) }
                    )
                    SettingsTileDivider()
                    SettingsNavTile(
                        title = "AI Providers",
                        subtitle = "API keys for Gemini & OpenRouter",
                        icon = Icons.Rounded.Hub,
                        onClick = { navController.navigate(Routes.AI_PROVIDERS) }
                    )
                    SettingsTileDivider()
                    SettingsNavTile(
                        title = "Text Recognition",
                        subtitle = "${textEngine.displayName} · scripts & preprocessing",
                        icon = Icons.Rounded.TextFields,
                        onClick = { navController.navigate(Routes.TEXT_RECOGNITION) }
                    )
                    SettingsTileDivider()
                    SettingsNavTile(
                        title = "Barcode Scanner Engine",
                        subtitle = barcodeEngine.displayName,
                        icon = Icons.Rounded.QrCodeScanner,
                        onClick = { showEngineDialog = true }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }

            item {
                SettingsSectionHeader(
                    text = "About",
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingsGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SettingsNavTile(
                        title = "Source code",
                        subtitle = "github.com/Azyrn/Scanly",
                        iconPainter = painterResource(id = R.drawable.ic_github),
                        trailingIcon = Icons.AutoMirrored.Rounded.OpenInNew,
                        onClick = { uriHandler.openUri("https://github.com/Azyrn/Scanly") }
                    )
                    SettingsTileDivider()
                    SettingsNavTile(
                        title = "Telegram",
                        subtitle = "Report bugs or request features",
                        iconPainter = painterResource(id = R.drawable.ic_telegram),
                        trailingIcon = Icons.AutoMirrored.Rounded.OpenInNew,
                        onClick = { uriHandler.openUri("https://telegram.me/ScanlyOCR") }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }

            item {
                val contributors = listOf(
                    Contributor("Azyrn", "Azyrn", "Developer"),
                    Contributor("DP-Hridayan", "DP-Hridayan", "Contributor")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingsSectionHeader(text = "Contributors")
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = contributors.size.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                SettingsGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                    contributors.forEachIndexed { index, contributor ->
                        if (index > 0) SettingsTileDivider()
                        ContributorTile(contributor)
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Scanly ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
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

@Composable
private fun ContributorTile(contributor: Contributor) {
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { uriHandler.openUri(contributor.githubUrl) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = contributor.avatarUrl,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contributor.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "@${contributor.username} · ${contributor.role}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.size(18.dp)
        )
    }
}

data class Contributor(
    val username: String,
    val displayName: String,
    val role: String,
    val githubUrl: String = "https://github.com/$username",
    val avatarUrl: String = "https://github.com/$username.png"
)
