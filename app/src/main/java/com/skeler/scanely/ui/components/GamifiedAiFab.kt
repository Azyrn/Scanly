@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.skeler.scanely.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skeler.scanely.R

data class RateLimitDisplayState(
    val remainingSeconds: Int = 0,
    val progress: Float = 0f,
    val justBecameReady: Boolean = false
)

@Composable
fun GamifiedAiFab(
    rateLimitState: RateLimitDisplayState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current

    val isRecharging = rateLimitState.remainingSeconds > 0
    val progress = rateLimitState.progress

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && !isRecharging) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "Fab Press Scale"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isRecharging) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(220),
        label = "Fab Container"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isRecharging) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(220),
        label = "Fab Content"
    )

    LaunchedEffect(rateLimitState.justBecameReady) {
        if (rateLimitState.justBecameReady) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
    }

    Surface(
        onClick = { if (!isRecharging) onClick() },
        modifier = modifier.scale(pressScale),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = contentColor,
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isRecharging) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.5.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "${rateLimitState.remainingSeconds}s",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_action_spark),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "AI Scan",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
