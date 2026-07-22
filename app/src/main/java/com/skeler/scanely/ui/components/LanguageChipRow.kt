package com.skeler.scanely.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PostAdd
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skeler.scanely.core.ai.SummaryLength

@Composable
fun LanguageChipRow(
    cachedLanguages: List<String>,
    currentLanguage: String?,
    showLanguageMenu: Boolean,
    onShowLanguageMenu: () -> Unit,
    onDismissLanguageMenu: () -> Unit,
    onSelectOriginal: () -> Unit,
    onSelectCached: (String) -> Unit,
    allLanguages: List<String>,
    onNewLanguageSelected: (String) -> Unit,
    onComposeText: () -> Unit,
    modifier: Modifier = Modifier,
    showMarkdown: Boolean = false,
    markdownSelected: Boolean = false,
    onSelectMarkdown: () -> Unit = {},
    currentSummary: SummaryLength? = null,
    cachedSummaries: List<SummaryLength> = emptyList(),
    showSummaryMenu: Boolean = false,
    onShowSummaryMenu: () -> Unit = {},
    onDismissSummaryMenu: () -> Unit = {},
    onSummaryLengthSelected: (SummaryLength) -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = currentLanguage == null && currentSummary == null && !markdownSelected,
            onClick = onSelectOriginal,
            label = { Text("Original") }
        )

        if (showMarkdown) {
            FilterChip(
                selected = markdownSelected,
                onClick = onSelectMarkdown,
                label = { Text("Markdown") }
            )
        }

        cachedLanguages.forEach { language ->
            FilterChip(
                selected = currentLanguage == language && currentSummary == null && !markdownSelected,
                onClick = { onSelectCached(language) },
                label = { Text(language) }
            )
        }

        Box {
            FilterChip(
                selected = false,
                onClick = onShowLanguageMenu,
                label = { Text("Translate") }
            )

            DropdownMenu(
                expanded = showLanguageMenu,
                onDismissRequest = onDismissLanguageMenu
            ) {
                allLanguages.filterNot { it in cachedLanguages }.forEach { language ->
                    DropdownMenuItem(
                        text = { Text(language) },
                        onClick = { onNewLanguageSelected(language) }
                    )
                }
            }
        }

        Box {
            FilterChip(
                selected = currentSummary != null && !markdownSelected,
                onClick = onShowSummaryMenu,
                label = { Text(currentSummary?.let { "Summary · ${it.label}" } ?: "Summary") }
            )

            // Cached lengths stay listed so a mis-tapped length can be changed; picking one
            // is a free cache switch, hence the check mark rather than hiding it.
            DropdownMenu(
                expanded = showSummaryMenu,
                onDismissRequest = onDismissSummaryMenu
            ) {
                SummaryLength.entries.forEach { length ->
                    DropdownMenuItem(
                        text = { Text(length.label) },
                        trailingIcon = if (length in cachedSummaries) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            null
                        },
                        onClick = { onSummaryLengthSelected(length) }
                    )
                }
            }
        }

        AssistChip(
            onClick = onComposeText,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.PostAdd,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            label = { Text("Text to PDF") }
        )
    }
}
