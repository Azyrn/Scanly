@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.skeler.scanely.settings.presentation.screen

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.skeler.scanely.R
import com.skeler.scanely.core.common.LocalSettings
import com.skeler.scanely.navigation.LocalNavController
import com.skeler.scanely.ocr.OcrHelper
import com.skeler.scanely.settings.data.SettingsKeys
import com.skeler.scanely.settings.presentation.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val navController = LocalNavController.current
    val topBarScrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()

    val isHighContrastDarkMode = LocalSettings.current.isHighContrastDarkMode
    val ocrLanguages = LocalSettings.current.ocrLanguages

    val languageList by remember {
        derivedStateOf {
            OcrHelper.SUPPORTED_LANGUAGES_MAP.entries
                .sortedBy { it.value }
        }
    }

    val onThemeChange: (mode: Int) -> Unit = { mode ->
        settingsViewModel.setInt(key = SettingsKeys.THEME_MODE, value = mode)
    }

    val toggleHighContrastDarkMode: () -> Unit = {
        settingsViewModel.setBoolean(
            SettingsKeys.HIGH_CONTRAST_DARK_MODE,
            value = !isHighContrastDarkMode
        )
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                scrollBehavior = topBarScrollBehavior,
                title = {
                    val collapsedFraction = topBarScrollBehavior.state.collapsedFraction
                    val expandedFontSize = 33.sp
                    val collapsedFontSize = 20.sp

                    val fontSize = lerp(expandedFontSize, collapsedFontSize, collapsedFraction)
                    Text(
                        modifier = Modifier.basicMarquee(),
                        text = "Settings",
                        maxLines = 1,
                        fontSize = fontSize,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 0.05.em
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(topBarScrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {

            // --- Appearance Section ---
            item {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Appearance",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ThemeSelectionCard(
                            mode = AppCompatDelegate.MODE_NIGHT_NO,
                            onSelect = onThemeChange,
                            modifier = Modifier.weight(1f)
                        )
                        ThemeSelectionCard(
                            mode = AppCompatDelegate.MODE_NIGHT_YES,
                            onSelect = onThemeChange,
                            modifier = Modifier.weight(1f)
                        )
                        ThemeSelectionCard(
                            mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                            onSelect = onThemeChange,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .clickable(
                                enabled = true,
                                onClick = toggleHighContrastDarkMode
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 15.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "High Contrast Dark Theme",
                                style = MaterialTheme.typography.titleMediumEmphasized,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Pure black dark theme for OLED devices",
                                style = MaterialTheme.typography.bodyMediumEmphasized,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                            )
                        }

                        Switch(
                            modifier = Modifier.padding(end = 15.dp),
                            checked = isHighContrastDarkMode,
                            onCheckedChange = { toggleHighContrastDarkMode() },
                            thumbContent = {
                                if (isHighContrastDarkMode) Icon(
                                    painter = painterResource(R.drawable.ic_check),
                                    contentDescription = "switch thumb icon",
                                    modifier = Modifier.size(20.dp)
                                )
                            })
                    }
                }
            }

            // --- Languages Section ---
            item {
                Text(
                    text = "Recognition Languages",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(24.dp)
                )
            }

            items(languageList, key = { it.key }) { (code, name) ->
                LanguageCheckboxRow(
                    label = name,
                    checked = ocrLanguages.contains(code),
                    onCheckedChange = { checked ->
                        settingsViewModel.toggleOcrLanguage(code, ocrLanguages)
                    }
                )
            }

            if (languageList.isEmpty()) {
                item {
                    Text(
                        text = "No languages found.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }

            // --- About Section ---
            item {
                AboutSection()
            }
        }
    }
}

@Composable
private fun ThemeSelectionCard(
    mode: Int,
    onSelect: (mode: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTheme = LocalSettings.current.themeMode
    val isSelected = mode == currentTheme

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (mode) {
                AppCompatDelegate.MODE_NIGHT_NO -> LightModeMockup(
                    onSelect = onSelect,
                    isSelected = isSelected
                )

                AppCompatDelegate.MODE_NIGHT_YES -> DarkModeMockup(
                    onSelect = onSelect,
                    isSelected = isSelected
                )

                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> SystemMockup(
                    onSelect = onSelect,
                    isSelected = isSelected
                )
            }

            CheckIcon(
                isSelected = isSelected,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = when (mode) {
                AppCompatDelegate.MODE_NIGHT_NO -> "Light"
                AppCompatDelegate.MODE_NIGHT_YES -> "Dark"
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> "System"
                else -> ""
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
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
            .padding(horizontal = 24.dp, vertical = 6.dp), // Reduced vertical padding
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 16.dp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AboutSection() {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Icon & Version
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_document_scanner),
                contentDescription = "Scanly",
                modifier = Modifier.size(28.dp),
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
                    text = "v1.3.0",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Developer Info
        Text(
            text = "Developed by Azyrn Skeler",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // GitHub Link Row
        Row(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable {
                    uriHandler.openUri("https://github.com/Azyrn/Scanly")
                }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_github),
                contentDescription = "GitHub",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "View on GitHub",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun LightModeMockup(
    modifier: Modifier = Modifier,
    isSelected: Boolean,
    onSelect: (Int) -> Unit
) {
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                onSelect(AppCompatDelegate.MODE_NIGHT_NO)
            },
        shape = RoundedCornerShape(16.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 8.dp else 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            TextMockup(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 10.dp)
            )

            TextMockup(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 35.dp)
            )
            TextMockup(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 20.dp)
            )
        }
    }
}

@Composable
private fun DarkModeMockup(
    modifier: Modifier = Modifier,
    isSelected: Boolean,
    onSelect: (Int) -> Unit
) {
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(
                enabled = true,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { onSelect(AppCompatDelegate.MODE_NIGHT_YES) }),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.95f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 8.dp else 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            TextMockup(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 10.dp),
                color = Color.White
            )

            TextMockup(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 35.dp),
                color = Color.White
            )
            TextMockup(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 20.dp),
                color = Color.White
            )
        }
    }
}

@Composable
private fun SystemMockup(
    modifier: Modifier = Modifier,
    isSelected: Boolean,
    onSelect: (Int) -> Unit
) {
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(
                enabled = true,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { onSelect(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) }),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 8.dp else 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(Color.White)
                    .padding(start = 10.dp, top = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                TextMockup(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Black,
                    cornerShape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)
                )

                TextMockup(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Black,
                    cornerShape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)

                )

                TextMockup(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Black,
                    cornerShape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(end = 10.dp, top = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                TextMockup(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    cornerShape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
                )

                TextMockup(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    cornerShape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
                )

                TextMockup(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    cornerShape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun TextMockup(
    modifier: Modifier = Modifier,
    color: Color = Color.Black,
    cornerShape: RoundedCornerShape = RoundedCornerShape(4.dp)
) {
    Box(
        modifier = modifier
            .height(8.dp)
            .clip(cornerShape)
            .background(color)
    )
}

@Composable
private fun CheckIcon(
    modifier: Modifier = Modifier,
    isSelected: Boolean
) {
    AnimatedVisibility(
        visible = isSelected,
        modifier = modifier,
        enter = scaleIn(
            initialScale = 0.6f, animationSpec = tween<Float>(
                durationMillis = 180,
                easing = FastOutSlowInEasing
            )
        ) + fadeIn(
            animationSpec = tween(120)
        ),
        exit = scaleOut(
            targetScale = 0.6f,
            animationSpec = tween(
                durationMillis = 150,
                easing = FastOutSlowInEasing
            )
        ) + fadeOut(
            animationSpec = tween(100)
        )
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Selected",
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .size(24.dp)
                .background(
                    MaterialTheme.colorScheme.primaryContainer,
                    CircleShape
                )
                .padding(4.dp)
        )
    }
}