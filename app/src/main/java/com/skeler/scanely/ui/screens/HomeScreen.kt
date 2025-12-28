package com.skeler.scanely.ui.screens

/**
 * Clean & Simple HomeScreen
 * - Center: 3 Main Actions (Camera, Gallery, PDF)
 * - Top: App Bar with Settings
 * - Bottom: History Link
 * - Settings: Theme Selection + Developer Info
 */
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.skeler.scanely.R
import com.skeler.scanely.navigation.LocalNavController
import com.skeler.scanely.navigation.Routes
import com.skeler.scanely.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    currentTheme: ThemeMode,
    onThemeChanged: (ThemeMode) -> Unit,
    ocrLanguages: Set<String>,
    onOcrLanguagesChanged: (Set<String>) -> Unit,
    onGalleryClick: () -> Unit = {},
    onPdfClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    val navController = LocalNavController.current

    var showSettingsDialog by remember { mutableStateOf(false) }

    if (showSettingsDialog) {
        SettingsDialog(
            currentTheme = currentTheme,
            onThemeChange = onThemeChanged,
            ocrLanguages = ocrLanguages,
            onOcrLanguagesChanged = onOcrLanguagesChanged,
            onDismiss = { showSettingsDialog = false }
        )
    }

    Scaffold(
        topBar = {
// ... (rest of TopBar same as before) ...
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Scanly",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // ... (rest of Home content same as before) ...
            Text(
                text = "What would you like to scan?",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(48.dp))

            MainActionButton(
                icon = Icons.Outlined.CameraAlt,
                title = "Capture Photo",
                subtitle = "Scan text using camera",
                onClick = { navController.navigate(Routes.CAMERA) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            MainActionButton(
                icon = Icons.Outlined.PhotoLibrary,
                title = "From Gallery",
                subtitle = "Import image file",
                onClick = onGalleryClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            MainActionButton(
                icon = Icons.Outlined.PictureAsPdf,
                title = "Extract PDF",
                subtitle = "Import PDF document",
                onClick = onPdfClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            MainActionButton(
                icon = Icons.Default.QrCodeScanner,
                title = "Scan Barcode/QR",
                subtitle = "Scan QR, Barcodes & More",
                onClick = { navController.navigate(Routes.BARCODE_SCANNER) }
            )

            Spacer(modifier = Modifier.height(48.dp))

            // History Link
            FilledTonalButton(
                onClick = { navController.navigate(Routes.HISTORY) },
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "History",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("View Previous Extracts")
            }
        }
    }
}

private val SUPPORTED_LANGUAGES_MAP = mapOf(
    "eng" to "English",
    "ara" to "Arabic",
    "fra" to "French",
    "spa" to "Spanish",
    "deu" to "German",
    "ita" to "Italian",
    "por" to "Portuguese",
    "rus" to "Russian",
    "jpn" to "Japanese",
    "chi_sim" to "Chinese (Simplified)"
)

@Composable
private fun SettingsDialog(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    ocrLanguages: Set<String>,
    onOcrLanguagesChanged: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    // Use derivedStateOf for expensive computed values
    val allKeys = remember { SUPPORTED_LANGUAGES_MAP.keys }
    val allSelected by remember {
        derivedStateOf {
            ocrLanguages.containsAll(allKeys)
        }
    }

    // Optimized Dialog with high-quality spring animation
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // Scrim with blur effect (mocked by color alpha)

        AnimatedVisibility(
            visible = true,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight / 2 }, // Slide up from bottom
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) + fadeIn(
                animationSpec = tween(300)
            ) + scaleIn(
                initialScale = 0.9f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
            exit = slideOutVertically(
                targetOffsetY = { it / 2 },
                animationSpec = tween(200)
            ) + fadeOut(
                animationSpec = tween(200)
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(vertical = 24.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Scrollable Content
                    Column(
                        modifier = Modifier
                            .weight(weight = 1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "Appearance",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        ThemeOptionRow(
                            "System Default",
                            ThemeMode.System,
                            currentTheme,
                            onThemeChange
                        )
                        ThemeOptionRow("Light Mode", ThemeMode.Light, currentTheme, onThemeChange)
                        ThemeOptionRow("Dark Mode", ThemeMode.Dark, currentTheme, onThemeChange)
                        ThemeOptionRow("OLED Black", ThemeMode.Oled, currentTheme, onThemeChange)

                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recognition Languages",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )

                            TextButton(onClick = {
                                if (allSelected) {
                                    onOcrLanguagesChanged(setOf("eng"))
                                } else {
                                    onOcrLanguagesChanged(allKeys)
                                }
                            }) {
                                Text(if (allSelected) "None" else "All")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Use key() for stable recomposition
                        SUPPORTED_LANGUAGES_MAP.forEach { (code, name) ->
                            key(code) {
                                LanguageCheckboxRow(
                                    label = name,
                                    checked = ocrLanguages.contains(code),
                                    onCheckedChange = { checked ->
                                        val newSet = ocrLanguages.toMutableSet()
                                        if (checked) {
                                            newSet.add(code)
                                        } else {
                                            if (newSet.size > 1) {
                                                newSet.remove(code)
                                            }
                                        }
                                        onOcrLanguagesChanged(newSet)
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        // About Section
                        val uriHandler = LocalUriHandler.current

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // App Icon & Version
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_scanly),
                                    contentDescription = "Scanly",
                                    modifier = Modifier.size(32.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.size(12.dp))
                                Column {
                                    Text(
                                        text = "Scanly",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "v1.1.0",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Developer Info
                            Text(
                                text = "Developed by Azyrn Skeler",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // GitHub Link Row
                            Row(
                                modifier = Modifier
                                    .clickable {
                                        uriHandler.openUri("https://github.com/Azyrn/Scanly")
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_github),
                                    contentDescription = "GitHub",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = "View on GitHub",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageCheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null // Handled by parent Row
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 12.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ThemeOptionRow(
    label: String,
    mode: ThemeMode,
    currentTheme: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(mode) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = (mode == currentTheme),
            onClick = { onSelect(mode) }
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// OcrModeOptionRow removed

@Composable
private fun MainActionButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.size(20.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
