@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.skeler.scanely.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Full-bleed presentation of a block of extracted text — no surrounding card,
 * so long passages read like an open document that fills the screen.
 *
 * A lightweight header row carries a title and a prominent one-tap copy button;
 * the body uses [ReadableTextContent] for comfortable long-form reading; a subtle
 * footer shows word / character counts. Shared by the AI and unified result
 * screens so both read consistently.
 */
@Composable
fun ExtractedTextSection(
    text: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Extracted Text",
    onExport: ((TextExportFormat) -> Unit)? = null
) {
    val counts = remember(text) {
        val words = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        val chars = text.length
        words to chars
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Notes,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (onExport != null) {
                Box {
                    var exportMenuOpen by remember { mutableStateOf(false) }
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
                        DropdownMenuItem(
                            text = { Text("Save as PDF") },
                            onClick = {
                                exportMenuOpen = false
                                onExport(TextExportFormat.PDF)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Save as CSV") },
                            onClick = {
                                exportMenuOpen = false
                                onExport(TextExportFormat.CSV)
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.size(8.dp))
            }
            FilledTonalIconButton(
                onClick = onCopy,
                shape = RoundedCornerShape(14.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = "Copy text",
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

        ReadableTextContent(text = text)

        Text(
            text = "${counts.first} words · ${counts.second} characters",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
