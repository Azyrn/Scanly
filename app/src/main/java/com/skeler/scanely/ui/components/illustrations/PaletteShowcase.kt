@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.skeler.scanely.ui.components.illustrations

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * PaletteShowcase — a bespoke, theme-reactive hero for the Look & Feel screen.
 *
 * Replaces the previous stock "undraw" person illustration. Instead of a flat
 * clip-art scene it renders a fanned stack of squircle swatch cards drawn
 * entirely from the live [MaterialTheme.colorScheme], so the artwork *is* the
 * user's current theme. A gentle breathing rotation keeps it feeling alive in
 * the Material 3 Expressive spirit without leaning on default gradients.
 */
@Composable
fun PaletteShowcase(
    modifier: Modifier = Modifier,
    cardSize: Dp = 132.dp
) {
    val scheme = MaterialTheme.colorScheme

    // Slow, subtle "breathing" so the fan feels alive but never distracting.
    val transition = rememberInfiniteTransition(label = "paletteBreath")
    val sway by transition.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sway"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        // Back card — tertiary, tilted furthest to the left.
        SwatchCard(
            size = cardSize,
            color = scheme.tertiaryContainer,
            rotation = -18f + sway,
            xOffset = (-54).dp
        )
        // Mid card — secondary, fanned to the right.
        SwatchCard(
            size = cardSize,
            color = scheme.secondaryContainer,
            rotation = 15f - sway,
            xOffset = 54.dp
        )
        // Front hero card — primary, holding a palette preview + sparkle accent.
        SwatchCard(
            size = cardSize,
            color = scheme.primaryContainer,
            rotation = -3f + sway * 0.5f,
            xOffset = 0.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = scheme.onPrimaryContainer,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(22.dp)
                )
                Row(
                    modifier = Modifier.align(Alignment.BottomStart),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ColorDot(scheme.primary)
                    ColorDot(scheme.secondary)
                    ColorDot(scheme.tertiary)
                }
            }
        }
    }
}

@Composable
private fun SwatchCard(
    size: Dp,
    color: Color,
    rotation: Float,
    xOffset: Dp,
    content: @Composable () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .offset(x = xOffset)
            .rotate(rotation)
            .size(size)
            .clip(RoundedCornerShape(34.dp))
            .background(color)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                shape = RoundedCornerShape(34.dp)
            ),
        content = { content() }
    )
}

@Composable
private fun ColorDot(color: Color) {
    Spacer(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color)
    )
}
