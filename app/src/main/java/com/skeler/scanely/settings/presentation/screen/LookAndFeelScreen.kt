@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.skeler.scanely.settings.presentation.screen

import android.graphics.Color as AndroidColor
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.skeler.scanely.core.common.LocalSettings
import com.skeler.scanely.navigation.LocalNavController
import com.skeler.scanely.settings.data.SettingsKeys
import com.skeler.scanely.settings.presentation.viewmodel.SettingsViewModel
import com.skeler.scanely.ui.theme.SeedColor
import com.skeler.scanely.ui.theme.SeedPalettes

import androidx.compose.foundation.Image
import com.skeler.scanely.ui.components.illustrations.DynamicIllustrations
import com.skeler.scanely.ui.components.illustrations.themePicker

@Composable
fun LookAndFeelScreen(
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    
    val settings = LocalSettings.current
    val useDynamicColors = settings.useDynamicColors
    val seedColorIndex = settings.seedColorIndex
    val isDarkTheme = settings.themeMode == AppCompatDelegate.MODE_NIGHT_YES
    val isOledMode = settings.isOledModeEnabled

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .systemBarsPadding(), // EXPLICIT edge-to-edge safety
        topBar = {
            LargeTopAppBar(
                title = { Text("Look & Feel") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)
        ) {
            // Colors Section Header
            item {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Illustration
                    Image(
                        imageVector = DynamicIllustrations.themePicker(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )

                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Color Picker - Split-circle "PaletteWheel" matching aShellYou
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
                    ) {
                        itemsIndexed(SeedPalettes.ALL) { index, seedColor ->
                            val isSelected = !useDynamicColors && seedColorIndex == index
                            PaletteWheel(
                                seedColor = seedColor,
                                isChecked = isSelected,
                                onClick = {
                                    settingsViewModel.setInt(SettingsKeys.SEED_COLOR_INDEX, index)
                                    settingsViewModel.setBoolean(SettingsKeys.USE_DYNAMIC_COLORS, false)
                                }
                            )
                        }
                    }
                }
            }

            // Dynamic Colors Toggle
            item {
                SettingSwitchTile(
                    title = "Dynamic colors",
                    subtitle = "Automatically set the app theme according to the device wallpaper",
                    icon = Icons.Default.ColorLens,
                    checked = useDynamicColors,
                    onCheckedChange = { checked ->
                        settingsViewModel.setBoolean(SettingsKeys.USE_DYNAMIC_COLORS, checked)
                    }
                )
            }
            
            item { Spacer(modifier = Modifier.height(24.dp)) }
            
            item {
                Text(
                     text = "Additional settings",
                     style = MaterialTheme.typography.titleMedium,
                     color = MaterialTheme.colorScheme.primary,
                     fontWeight = FontWeight.Bold,
                     modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Dark Theme Toggle
            item {
                val isDark = settings.themeMode == AppCompatDelegate.MODE_NIGHT_YES
                SettingSwitchTile(
                    title = "Dark theme",
                    subtitle = if (isDark) "On" else "Off",
                    icon = Icons.Default.DarkMode,
                    checked = isDark,
                    onCheckedChange = { checked ->
                        val newMode = if (checked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                        settingsViewModel.setInt(SettingsKeys.THEME_MODE, newMode)
                    }
                )
            }

             // High Contrast / OLED
            item {
                SettingSwitchTile(
                    title = "Pure Black M3",
                    subtitle = "Save battery on OLED displays",
                    icon = Icons.Default.Contrast,
                    checked = isOledMode,
                    onCheckedChange = { checked ->
                         settingsViewModel.setBoolean(SettingsKeys.IS_OLED_MODE_ENABLED, checked)
                    }
                )
            }
        }
    }
}

/**
 * PaletteWheel - Split-circle color picker matching aShellYou pattern.
 * Top half: primary color
 * Bottom-left quarter: secondary color
 * Bottom-right quarter: tertiary color
 */
@Composable
fun PaletteWheel(
    modifier: Modifier = Modifier,
    size: Dp = 50.dp,
    seedColor: SeedColor,
    isChecked: Boolean = false,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isChecked) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "Check Scale Animation"
    )

    val primaryColor = modifyColorForDisplay(Color(seedColor.primary), toneFactor = 1f)
    val secondaryColor = modifyColorForDisplay(Color(seedColor.secondary), toneFactor = 1.4f)
    val tertiaryColor = modifyColorForDisplay(Color(seedColor.tertiary), toneFactor = 0.7f)

    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .border(
                width = 1.dp,
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            .clickable(enabled = true, onClick = onClick)
    ) {
        Box(
            modifier = modifier
                .padding(10.dp)
                .size(size)
                .clip(CircleShape)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                // Top half: primary color
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(color = primaryColor)
                )

                // Bottom half: secondary (left) + tertiary (right)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(color = secondaryColor)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .background(color = tertiaryColor)
                    )
                }
            }

            // Checkmark overlay when selected
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.Center)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(color = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Icon(
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.Center),
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/**
 * Modifies color for display by adjusting saturation and value.
 * Matches aShellYou's modifyColorForDisplay function.
 */
fun modifyColorForDisplay(
    color: Color,
    toneFactor: Float = 1.2f,
    chromaFactor: Float = 1.15f
): Color {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(color.toArgb(), hsv)

    hsv[1] = (hsv[1] * chromaFactor).coerceIn(0f, 1f)
    hsv[2] = (hsv[2] * toneFactor).coerceIn(0f, 1f)

    return Color(AndroidColor.HSVToColor(hsv))
}

@Composable
fun SettingSwitchTile(
    title: String,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.padding(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.padding(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
