@file:OptIn(ExperimentalMaterial3Api::class)

package com.skeler.scanely.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skeler.scanely.navigation.LocalNavController
import com.skeler.scanely.ui.components.ExportContent
import com.skeler.scanely.ui.components.ExportMenuItem
import com.skeler.scanely.ui.components.TEXT_EXPORT_FORMATS
import com.skeler.scanely.ui.components.rememberTextExporter
import com.skeler.scanely.ui.components.unavailableFormats

@Composable
fun TextComposeScreen() {
    val navController = LocalNavController.current
    val exportText = rememberTextExporter()

    var text by remember { mutableStateOf("") }
    var exportMenuOpen by remember { mutableStateOf(false) }

    // What you typed is what you get: this screen has no Markdown view.
    val preview = remember(text) { ExportContent(text) }
    val disabledFormats = remember(preview) { unavailableFormats(preview) }

    val counts = remember(text) {
        val words = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        words to text.length
    }

    val readingStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = 17.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.015.sp,
        textDirection = TextDirection.Content,
        color = MaterialTheme.colorScheme.onSurface
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Text to PDF", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        FilledTonalIconButton(
                            onClick = { exportMenuOpen = true },
                            shape = RoundedCornerShape(14.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SaveAlt,
                                contentDescription = "Export",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = exportMenuOpen,
                            onDismissRequest = { exportMenuOpen = false }
                        ) {
                            TEXT_EXPORT_FORMATS.forEach { format ->
                                ExportMenuItem(
                                    format = format,
                                    enabled = format !in disabledFormats,
                                    onClick = {
                                        exportMenuOpen = false
                                        exportText(preview, format)
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp)
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = "Start typing…",
                        style = readingStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = readingStyle,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Text(
                text = "${counts.first} words · ${counts.second} characters",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }
    }
}
