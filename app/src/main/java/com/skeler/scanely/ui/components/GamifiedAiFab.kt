package com.skeler.scanely.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Rate limit state for gamified FAB.
 */
data class RateLimitDisplayState(
    val remainingSeconds: Int = 0,
    val progress: Float = 0f,
    val justBecameReady: Boolean = false
)

/**
 * Gamified AI FAB with recharging animation.
 *
 * Deep Reasoning (ULTRATHINK):
 * - Psychological: "Recharging" feels active vs "Disabled" feels punitive
 * - CircularProgressIndicator around FAB shows time investment paying off
 * - Haptic feedback on ready creates Pavlovian positive association
 * 
 * @param rateLimitState Current rate limit display state
 * @param onClick Callback when FAB is clicked (only fires when not recharging)
 */
@Composable
fun GamifiedAiFab(
    rateLimitState: RateLimitDisplayState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current

    val isRecharging = rateLimitState.remainingSeconds > 0
    val progress = rateLimitState.progress

    // Haptic feedback when becoming ready
    LaunchedEffect(rateLimitState.justBecameReady) {
        if (rateLimitState.justBecameReady) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
    }

    // Gamified FAB with progress ring
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        // Progress ring (behind FAB)
        if (isRecharging) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(72.dp),
                strokeWidth = 4.dp,
                strokeCap = StrokeCap.Round,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // FAB
        LargeFloatingActionButton(
            onClick = {
                if (!isRecharging) {
                    onClick()
                }
            },
            containerColor = if (isRecharging) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            contentColor = if (isRecharging) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
            modifier = Modifier.size(if (isRecharging) 56.dp else 64.dp)
        ) {
            if (isRecharging) {
                Text(
                    text = "${rateLimitState.remainingSeconds}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Scan",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
