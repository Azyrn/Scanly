@file:OptIn(ExperimentalMaterial3Api::class)

package com.skeler.scanely.settings.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Rotate90DegreesCcw
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.skeler.scanely.core.ocr.TextOcrEngine
import com.skeler.scanely.core.ocr.paddle.PackState
import com.skeler.scanely.core.ocr.paddle.ScriptPack
import com.skeler.scanely.navigation.LocalNavController
import com.skeler.scanely.settings.data.SettingsKeys
import com.skeler.scanely.settings.presentation.viewmodel.TextRecognitionViewModel
import com.skeler.scanely.ui.components.SettingSwitchTile
import com.skeler.scanely.ui.components.SettingsGroup
import com.skeler.scanely.ui.components.SettingsSectionHeader
import com.skeler.scanely.ui.components.SettingsTileDivider

@Composable
fun TextRecognitionScreen(
    viewModel: TextRecognitionViewModel = hiltViewModel()
) {
    val navController = LocalNavController.current
    val engine by viewModel.engine.collectAsState(initial = TextOcrEngine.DEFAULT)
    val script by viewModel.script.collectAsState(initial = ScriptPack.DEFAULT)
    val packStates by viewModel.packStates.collectAsState()

    val docOrientation by viewModel.toggle(SettingsKeys.PADDLE_DOC_ORIENTATION)
        .collectAsState(initial = true)
    val lineOrientation by viewModel.toggle(SettingsKeys.PADDLE_LINE_ORIENTATION)
        .collectAsState(initial = true)
    val dewarp by viewModel.toggle(SettingsKeys.PADDLE_DOC_UNWARP)
        .collectAsState(initial = false)
    val uvdoc by viewModel.uvdocState.collectAsState()
    val structure by viewModel.toggle(SettingsKeys.PADDLE_STRUCTURE)
        .collectAsState(initial = true)
    val tableModel by viewModel.tableState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Text Recognition", fontWeight = FontWeight.Bold) },
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
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 48.dp)
        ) {
            item {
                SettingsSectionHeader(
                    text = "Engine",
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp)
                )
                Spacer(Modifier.height(12.dp))
                SettingsGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                    TextOcrEngine.entries.forEachIndexed { index, option ->
                        if (index > 0) SettingsTileDivider()
                        EngineRow(
                            engine = option,
                            selected = option == engine,
                            onSelect = { viewModel.setEngine(option) }
                        )
                    }
                }
            }

            item {
                AnimatedVisibility(visible = engine == TextOcrEngine.PADDLE) {
                    Column {
                        Spacer(Modifier.height(32.dp))
                        SettingsSectionHeader(
                            text = "Script",
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        SettingsGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                            ScriptPack.entries.forEachIndexed { index, pack ->
                                if (index > 0) SettingsTileDivider()
                                ScriptRow(
                                    pack = pack,
                                    state = packStates[pack] ?: PackState.Missing,
                                    selected = pack == script,
                                    onSelect = { viewModel.setScript(pack) },
                                    onDownload = { viewModel.download(pack) },
                                    onDelete = { viewModel.delete(pack) }
                                )
                            }
                        }

                        Spacer(Modifier.height(32.dp))
                        SettingsSectionHeader(
                            text = "Preprocessing",
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        SettingsGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                            SettingSwitchTile(
                                title = "Auto-rotate page",
                                subtitle = "Detects sideways and upside-down pages",
                                icon = Icons.Rounded.ScreenRotation,
                                checked = docOrientation,
                                onCheckedChange = {
                                    viewModel.setToggle(SettingsKeys.PADDLE_DOC_ORIENTATION, it)
                                }
                            )
                            SettingsTileDivider()
                            SettingSwitchTile(
                                title = "Fix flipped lines",
                                subtitle = "Corrects individual upside-down text lines",
                                icon = Icons.Rounded.Rotate90DegreesCcw,
                                checked = lineOrientation,
                                onCheckedChange = {
                                    viewModel.setToggle(SettingsKeys.PADDLE_LINE_ORIENTATION, it)
                                }
                            )
                            SettingsTileDivider()
                            SettingSwitchTile(
                                title = "Flatten curved pages",
                                subtitle = when (val state = uvdoc) {
                                    is PackState.Downloading ->
                                        "Downloading ${(state.progress * 100).toInt()}%"
                                    is PackState.Failed -> state.message
                                    is PackState.Missing -> "UVDoc dewarping · 30 MB download"
                                    else -> "UVDoc dewarping — slower, for books and folds"
                                },
                                icon = Icons.Rounded.Crop,
                                enabled = uvdoc !is PackState.Downloading,
                                checked = dewarp,
                                onCheckedChange = { viewModel.setDewarp(it) }
                            )
                        }

                        Spacer(Modifier.height(32.dp))
                        SettingsSectionHeader(
                            text = "Document structure",
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        SettingsGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                            SettingSwitchTile(
                                title = "Detect layout",
                                subtitle = "Headings, paragraphs and reading order in the Markdown view",
                                icon = Icons.Rounded.Article,
                                checked = structure,
                                onCheckedChange = {
                                    viewModel.setToggle(SettingsKeys.PADDLE_STRUCTURE, it)
                                }
                            )
                            SettingsTileDivider()
                            SettingSwitchTile(
                                title = "Recognize tables",
                                subtitle = when (val state = tableModel) {
                                    is PackState.Downloading ->
                                        "Downloading ${(state.progress * 100).toInt()}%"
                                    is PackState.Failed -> state.message
                                    is PackState.Missing -> "SLANet tables · 8 MB download"
                                    else -> "Rebuilds tables as grids you can export to CSV"
                                },
                                icon = Icons.Rounded.TableChart,
                                enabled = structure && tableModel !is PackState.Downloading,
                                checked = tableModel is PackState.Installed,
                                onCheckedChange = { viewModel.setTables(it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EngineRow(
    engine: TextOcrEngine,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = engine.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = engine.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ScriptRow(
    pack: ScriptPack,
    state: PackState,
    selected: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val installed = state is PackState.Bundled || state is PackState.Installed

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = installed, onClick = onSelect)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected && installed,
            onClick = onSelect,
            enabled = installed
        )
        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pack.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (installed) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = when (state) {
                    is PackState.Failed -> state.message
                    is PackState.Missing -> "${pack.languages} · ${pack.sizeMb} MB download"
                    else -> pack.languages
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (state is PackState.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        Spacer(Modifier.width(8.dp))

        when (state) {
            is PackState.Downloading -> Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )
            }
            is PackState.Installed -> IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Delete ${pack.displayName}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is PackState.Missing, is PackState.Failed -> IconButton(onClick = onDownload) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = "Download ${pack.displayName}",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            PackState.Bundled -> Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Built in",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
