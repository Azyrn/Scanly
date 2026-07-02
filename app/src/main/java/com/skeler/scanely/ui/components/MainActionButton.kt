package com.skeler.scanely.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
    countdownSeconds: Int = 0,
    accentTint: Color? = null
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
        countdownSeconds = countdownSeconds,
        accentTint = accentTint
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
 * @param accentTint Optional per-action icon tint; falls back to primary when null
 */
@Composable
fun MainActionButton(
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    countdownSeconds: Int = 0,
    accentTint: Color? = null
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
        countdownSeconds = countdownSeconds,
        accentTint = accentTint
    )
}

/**
 * Secondary "View Previous Extracts" entry point.
 *
 * A stadium pill on the same [MaterialTheme.colorScheme.surfaceContainer] tone
 * as the action cards, so it reads as a quieter member of the same family rather
 * than a stock tinted button. Muted icon + medium label keep it clearly
 * secondary to the four primary scan actions.
 */
@Composable
fun HistoryPillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "History Pill Press"
    )

    Surface(
        onClick = onClick,
        modifier = modifier.scale(pressScale),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "View Previous Extracts",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun MainActionButtonContent(
    icon: @Composable (androidx.compose.ui.graphics.Color) -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    countdownSeconds: Int = 0,
    accentTint: Color? = null
) {
    val actualSubtitle = if (countdownSeconds > 0) {
        "Wait ${countdownSeconds}s..."
    } else {
        subtitle
    }

    val iconTint = when {
        !enabled -> MaterialTheme.colorScheme.outline
        accentTint != null -> accentTint
        else -> MaterialTheme.colorScheme.primary
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
