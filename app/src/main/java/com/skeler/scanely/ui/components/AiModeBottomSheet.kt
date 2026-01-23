package com.skeler.scanely.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skeler.scanely.core.ai.AiMode

/**
 * AI mode selection bottom sheet.
 * 
 * @param sheetState Modal sheet state
 * @param onDismiss Called when sheet is dismissed
 * @param onModeSelected Called when user selects an AI mode
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiModeBottomSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onModeSelected: (AiMode) -> Unit
) {
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

            AiModeItem(
                icon = Icons.AutoMirrored.Filled.TextSnippet,
                title = "Extract Text",
                subtitle = "Extract visible text from image",
                onClick = { onModeSelected(AiMode.EXTRACT_TEXT) }
            )

            AiModeItem(
                icon = Icons.Outlined.PictureAsPdf,
                title = "Extract PDF",
                subtitle = "AI-powered PDF and text file extraction",
                onClick = { onModeSelected(AiMode.EXTRACT_PDF_TEXT) }
            )
        }
    }
}

/**
 * Single AI mode item card.
 */
@Composable
private fun AiModeItem(
    icon: ImageVector,
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
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        )
    }
}
