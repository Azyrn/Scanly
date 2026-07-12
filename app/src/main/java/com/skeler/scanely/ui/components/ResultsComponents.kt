@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.skeler.scanely.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Stable
class ExtractedTextState internal constructor(initial: String) {
    var editing by mutableStateOf(false)
        internal set
    var draft by mutableStateOf(initial)
}

@Composable
fun rememberExtractedTextState(text: String): ExtractedTextState =
    remember(text) { ExtractedTextState(text) }

@Composable
fun RowScope.ExtractedTextActions(
    state: ExtractedTextState,
    text: String,
    onCopy: () -> Unit,
    onExport: ((TextExportFormat) -> Unit)? = null,
    exportFormats: List<TextExportFormat> = TEXT_EXPORT_FORMATS,
    onSaveEdit: ((String) -> Unit)? = null,
    onPrint: (() -> Unit)? = null,
    compact: Boolean = false,
    leading: (@Composable () -> Unit)? = null
) {
    val btnModifier = if (compact) Modifier.size(34.dp) else Modifier
    val iconSize = if (compact) 18.dp else 20.dp
    val gap = if (compact) 6.dp else 8.dp

    if (state.editing) {
        FilledTonalIconButton(
            onClick = {
                state.draft = text
                state.editing = false
            },
            modifier = btnModifier,
            shape = RoundedCornerShape(14.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Cancel", modifier = Modifier.size(iconSize))
        }
        Spacer(modifier = Modifier.size(gap))
        FilledTonalIconButton(
            onClick = {
                state.editing = false
                if (state.draft != text) onSaveEdit?.invoke(state.draft)
            },
            modifier = btnModifier,
            shape = RoundedCornerShape(14.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(Icons.Rounded.Check, contentDescription = "Save", modifier = Modifier.size(iconSize))
        }
    } else {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.size(gap))
        }
        if (onSaveEdit != null) {
            FilledTonalIconButton(
                onClick = {
                    state.draft = text
                    state.editing = true
                },
                modifier = btnModifier,
                shape = RoundedCornerShape(14.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(Icons.Rounded.Edit, contentDescription = "Edit text", modifier = Modifier.size(iconSize))
            }
            Spacer(modifier = Modifier.size(gap))
        }
        if (onPrint != null) {
            FilledTonalIconButton(
                onClick = onPrint,
                modifier = btnModifier,
                shape = RoundedCornerShape(14.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(Icons.Rounded.Print, contentDescription = "Print", modifier = Modifier.size(iconSize))
            }
            Spacer(modifier = Modifier.size(gap))
        }
        if (onExport != null) {
            Box {
                var exportMenuOpen by remember { mutableStateOf(false) }
                FilledTonalIconButton(
                    onClick = { exportMenuOpen = true },
                    modifier = btnModifier,
                    shape = RoundedCornerShape(14.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(Icons.Rounded.SaveAlt, contentDescription = "Export", modifier = Modifier.size(iconSize))
                }
                DropdownMenu(
                    expanded = exportMenuOpen,
                    onDismissRequest = { exportMenuOpen = false }
                ) {
                    exportFormats.forEach { format ->
                        DropdownMenuItem(
                            text = { Text(format.label) },
                            onClick = {
                                exportMenuOpen = false
                                onExport(format)
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.size(gap))
        }
        FilledTonalIconButton(
            onClick = onCopy,
            modifier = btnModifier,
            shape = RoundedCornerShape(14.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy text", modifier = Modifier.size(iconSize))
        }
    }
}

@Composable
fun ExtractedTextSection(
    state: ExtractedTextState,
    text: String,
    modifier: Modifier = Modifier,
    credential: (@Composable () -> Unit)? = null,
    markdown: Boolean = false
) {
    val shown = if (state.editing) state.draft else text
    val counts = remember(shown) {
        val words = shown.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        words to shown.length
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (credential != null && !state.editing) {
            credential()
            Spacer(modifier = Modifier.height(8.dp))
        }

        when {
            state.editing ->
                EditableReadableText(value = state.draft, onValueChange = { state.draft = it })
            markdown -> MarkdownContent(text = text)
            else -> ReadableTextContent(text = text)
        }

        Text(
            text = "${counts.first} words · ${counts.second} characters",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun CredentialBadge(
    providerName: String,
    model: String,
    usesBundledKey: Boolean,
    modifier: Modifier = Modifier
) {
    val content = if (usesBundledKey) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    val label = if (usesBundledKey) "Built-in" else "Your key"
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (usesBundledKey) Icons.Rounded.Cloud else Icons.Rounded.Key,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = "$label · $providerName $model",
            style = MaterialTheme.typography.labelSmall,
            color = content.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
