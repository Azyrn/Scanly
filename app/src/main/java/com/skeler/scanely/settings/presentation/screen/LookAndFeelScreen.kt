@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.skeler.scanely.settings.presentation.screen

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.skeler.scanely.core.common.LocalDarkMode
import com.skeler.scanely.core.common.LocalSettings
import com.skeler.scanely.navigation.LocalNavController
import com.skeler.scanely.settings.data.SettingsKeys
import com.skeler.scanely.settings.presentation.viewmodel.SettingsViewModel
import com.skeler.scanely.ui.components.PaletteChip
import com.skeler.scanely.ui.components.SettingSwitchTile
import com.skeler.scanely.ui.components.SettingsGroup
import com.skeler.scanely.ui.components.SettingsSectionHeader
import com.skeler.scanely.ui.components.SettingsTileDivider
import com.skeler.scanely.ui.components.illustrations.PaletteShowcase
import com.skeler.scanely.ui.theme.SeedPalettes

@Composable
fun LookAndFeelScreen(
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val navController = LocalNavController.current

    val settings = LocalSettings.current
    val useDynamicColors = settings.useDynamicColors
    val seedColorIndex = settings.seedColorIndex
    val isOledMode = settings.isOledModeEnabled
    val isDark = LocalDarkMode.current

    val selectedName = when {
        useDynamicColors -> "Wallpaper colors"
        else -> SeedPalettes.ALL.getOrElse(seedColorIndex) { SeedPalettes.DEFAULT }.name
    }

    // Compact bar: the content fits on one screen, so a large/collapsing bar
    // would only waste vertical space above the palette showcase.
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Look & Feel", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
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
            // Theme-reactive hero + palette name.
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    PaletteShowcase(
                        modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    AnimatedContent(
                        targetState = selectedName,
                        transitionSpec = {
                            (fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 4 }) togetherWith
                                (fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 4 })
                        },
                        label = "PaletteName"
                    ) { name ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Pick a palette to recolor the whole app",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 40.dp)
                    )

                    Spacer(modifier = Modifier.height(28.dp))
                }
            }

            // Palette picker, grouped under its own accent heading.
            item {
                SettingsSectionHeader(
                    text = "Color palette",
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp)
                ) {
                    // Dynamic (wallpaper) colours share the selector: they are
                    // mutually exclusive with picking a fixed palette.
                    item(key = "dynamic") {
                        PaletteChip(
                            label = "Dynamic",
                            isSelected = useDynamicColors,
                            onClick = {
                                settingsViewModel.setBoolean(SettingsKeys.USE_DYNAMIC_COLORS, true)
                            }
                        )
                    }
                    itemsIndexed(
                        items = SeedPalettes.ALL,
                        key = { _, seedColor -> seedColor.name }
                    ) { index, seedColor ->
                        PaletteChip(
                            label = seedColor.name,
                            dotColor = Color(seedColor.primary),
                            isSelected = !useDynamicColors && seedColorIndex == index,
                            onClick = {
                                settingsViewModel.setInt(SettingsKeys.SEED_COLOR_INDEX, index)
                                settingsViewModel.setBoolean(SettingsKeys.USE_DYNAMIC_COLORS, false)
                            }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(36.dp)) }

            // Grouped appearance toggles on a single tonal-elevated surface.
            item {
                SettingsSectionHeader(
                    text = "Personalize",
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingsGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SettingSwitchTile(
                        title = "Dark theme",
                        subtitle = if (isDark) "On" else "Off",
                        icon = Icons.Rounded.DarkMode,
                        checked = isDark,
                        onCheckedChange = { checked ->
                            val newMode = if (checked) {
                                AppCompatDelegate.MODE_NIGHT_YES
                            } else {
                                AppCompatDelegate.MODE_NIGHT_NO
                            }
                            settingsViewModel.setInt(SettingsKeys.THEME_MODE, newMode)
                        }
                    )

                    SettingsTileDivider()

                    SettingSwitchTile(
                        title = "Pure Black M3",
                        subtitle = "Save battery on OLED displays",
                        icon = Icons.Rounded.Contrast,
                        checked = isOledMode,
                        onCheckedChange = { checked ->
                            settingsViewModel.setBoolean(SettingsKeys.IS_OLED_MODE_ENABLED, checked)
                        }
                    )
                }
            }
        }
    }
}
