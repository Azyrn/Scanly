package com.skeler.scanely.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Main action button card for HomeScreen.
 * 
 * Used for: Camera, Gallery, PDF, QR scan actions.
 * 
 * @param icon Leading icon (ImageVector)
 * @param title Primary text
 * @param subtitle Secondary description
 * @param onClick Action callback
 * @param enabled Whether button is interactive
 * @param countdownSeconds Shows countdown in subtitle when > 0
 */
@Composable
fun MainActionButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    countdownSeconds: Int = 0
) {
    MainActionButtonContent(
        icon = { tint ->
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = tint
            )
        },
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        countdownSeconds = countdownSeconds
    )
}

/**
 * Main action button card with drawable resource icon.
 * 
 * @param iconRes Leading icon drawable resource
 * @param title Primary text
 * @param subtitle Secondary description
 * @param onClick Action callback
 * @param enabled Whether button is interactive
 * @param countdownSeconds Shows countdown in subtitle when > 0
 */
@Composable
fun MainActionButton(
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    countdownSeconds: Int = 0
) {
    MainActionButtonContent(
        icon = { tint ->
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = tint
            )
        },
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        countdownSeconds = countdownSeconds
    )
}

@Composable
private fun MainActionButtonContent(
    icon: @Composable (androidx.compose.ui.graphics.Color) -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    countdownSeconds: Int = 0
) {
    val actualSubtitle = if (countdownSeconds > 0) {
        "Wait ${countdownSeconds}s..."
    } else {
        subtitle
    }
    
    val iconTint = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon(iconTint)
            Spacer(modifier = Modifier.size(20.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = actualSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (countdownSeconds > 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
