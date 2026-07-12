package com.skeler.scanely.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
private fun skeletonPulse(): Float {
    val transition = rememberInfiniteTransition(label = "SkeletonPulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SkeletonAlpha"
    )
    return alpha
}

@Composable
private fun SkeletonBlock(
    modifier: Modifier = Modifier,
    height: Dp,
    shape: Shape = MaterialTheme.shapes.small
) {
    Box(
        modifier = modifier
            .height(height)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
    )
}

@Composable
fun ScanResultSkeleton(
    modifier: Modifier = Modifier,
    showChips: Boolean = true
) {
    Column(modifier = modifier.alpha(skeletonPulse())) {
        if (showChips) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBlock(Modifier.width(96.dp), 32.dp, MaterialTheme.shapes.medium)
                SkeletonBlock(Modifier.width(120.dp), 32.dp, MaterialTheme.shapes.medium)
                SkeletonBlock(Modifier.width(88.dp), 32.dp, MaterialTheme.shapes.medium)
            }
            Spacer(Modifier.height(20.dp))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceContainerLow,
                    MaterialTheme.shapes.large
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SkeletonBlock(Modifier.fillMaxWidth(0.9f), 14.dp)
            SkeletonBlock(Modifier.fillMaxWidth(), 14.dp)
            SkeletonBlock(Modifier.fillMaxWidth(0.75f), 14.dp)
            SkeletonBlock(Modifier.fillMaxWidth(0.85f), 14.dp)
            SkeletonBlock(Modifier.fillMaxWidth(0.6f), 14.dp)
        }
    }
}
