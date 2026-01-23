package com.skeler.scanely.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Language chip row for instant switching between original and cached translations.
 * 
 * Layout: [Original] [Japanese] [French] ... [Translate ▼]
 * 
 * @param cachedLanguages Languages already translated and cached
 * @param currentLanguage Currently displayed language (null = Original)
 * @param showLanguageMenu Whether the language dropdown is visible
 * @param onShowLanguageMenu Opens the language dropdown
 * @param onDismissLanguageMenu Closes the language dropdown
 * @param onSelectOriginal Switches back to original text
 * @param onSelectCached Switches to a cached translation
 * @param allLanguages All available target languages
 * @param onNewLanguageSelected Called when user selects a new language to translate
 */
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
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Original chip
        FilterChip(
            selected = currentLanguage == null,
            onClick = onSelectOriginal,
            label = { Text("Original") }
        )
        
        // Cached language chips
        cachedLanguages.forEach { language ->
            FilterChip(
                selected = currentLanguage == language,
                onClick = { onSelectCached(language) },
                label = { Text(language) }
            )
        }
        
        // Add new language chip with dropdown
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
                // Filter out already-cached languages
                allLanguages.filterNot { it in cachedLanguages }.forEach { language ->
                    DropdownMenuItem(
                        text = { Text(language) },
                        onClick = { onNewLanguageSelected(language) }
                    )
                }
            }
        }
    }
}
