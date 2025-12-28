package com.skeler.scanely.ui.components

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A container that applies a glassmorphism effect:
 * - Blur (Android 12+)
 * - Semi-transparent background
 * - Subtle gradient border
 */
@Composable
fun GlassBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable BoxScope.() -> Unit
) {
    // Glass Effect Parameters
    val blurRadius = 20.dp
    val glassColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f) // Adjust alpha for "glass" level
    val borderColor = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.4f),
            Color.White.copy(alpha = 0.1f)
        )
    )

    Box(
        modifier = modifier
            .then(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Modifier.blur(blurRadius)
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(glassColor)
            .border(BorderStroke(1.dp, borderColor), shape)
            .padding(16.dp), // Default padding
        content = content
    )
}

/**
 * Variation for top/bottom bars that might not need full blur but need the transparency look
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    color: Color = Color.Black.copy(alpha = 0.5f),
    content: @Composable BoxScope.() -> Unit
) {
     Box(
        modifier = modifier
            .background(color)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        content = content
    )
}
