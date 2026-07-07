package com.skeler.scanely.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skeler.scanely.R
import com.skeler.scanely.core.ai.AiMode
import com.skeler.scanely.core.ai.AiProvider

/**
 * AI mode selection bottom sheet.
 *
 * Lets the user pick the AI provider (per-scan) and the extraction mode.
 *
 * @param sheetState Modal sheet state
 * @param initialProvider Provider to preselect (the user's last choice)
 * @param onDismiss Called when sheet is dismissed
 * @param onProviderSelected Called when the user picks a provider (to persist it)
 * @param onModeSelected Called with the chosen mode and provider
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiModeBottomSheet(
    sheetState: SheetState,
    initialProvider: AiProvider,
    onDismiss: () -> Unit,
    onProviderSelected: (AiProvider) -> Unit,
    onModeSelected: (AiMode, AiProvider) -> Unit
) {
    var provider by remember(initialProvider) { mutableStateOf(initialProvider) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
            Text(
                text = "AI Scan",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            Text(
                text = "PROVIDER",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(AiProvider.entries) { option ->
                    val selected = provider == option
                    FilterChip(
                        selected = selected,
                        onClick = {
                            provider = option
                            onProviderSelected(option)
                        },
                        label = { Text(option.displayName) },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        } else null
                    )
                }
            }

            AiModeItem(
                iconRes = R.drawable.ic_action_extract_text,
                title = "Extract Text",
                subtitle = "Extract visible text from image",
                onClick = { onModeSelected(AiMode.EXTRACT_TEXT, provider) }
            )

            AiModeItem(
                iconRes = R.drawable.ic_action_extract_pdf,
                title = "Extract PDF",
                subtitle = "AI-powered PDF and text file extraction",
                onClick = { onModeSelected(AiMode.EXTRACT_PDF_TEXT, provider) }
            )
        }
    }
}

/**
 * Single AI mode item card.
 */
@Composable
private fun AiModeItem(
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            },
            supportingContent = {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingContent = {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        )
    }
}
