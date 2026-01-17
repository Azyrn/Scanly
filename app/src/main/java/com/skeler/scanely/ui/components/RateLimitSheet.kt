@file:OptIn(ExperimentalMaterial3Api::class)

package com.skeler.scanely.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private const val RATE_LIMIT_SECONDS = 60

/**
 * Modal bottom sheet explaining rate limit to users.
 *
 * Deep Reasoning (ULTRATHINK):
 * - "Please Wait" is neutral; "Error" or "Rate Limited" feels punitive
 * - Progress bar fills (not depletes) for faster perceived wait time
 * - "Developer is using limited API" humanizes the constraint
 * - Dismiss button respects user agency vs auto-dismiss
 * - Semantics for TalkBack accessibility
 * - Smooth progress animation (1s tween) for fluid bar movement
 */
@Composable
fun RateLimitSheet(
    remainingSeconds: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState()
) {
    val targetProgress = 1f - (remainingSeconds.toFloat() / RATE_LIMIT_SECONDS)
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 1000),
        label = "progress"
    )
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.semantics { 
            contentDescription = "Rate limit explanation. $remainingSeconds seconds remaining."
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = "Please Wait",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Explanation
            Text(
                text = "You are using a free tier. Please wait a full minute before using advanced AI features.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Developer note
            Text(
                text = "Developer is using limited API",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Progress indicator (fills from 0 to 1)
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .semantics { 
                        contentDescription = "$remainingSeconds seconds remaining"
                    },
                strokeCap = StrokeCap.Round,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Countdown text
            Text(
                text = "${remainingSeconds}s remaining",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Dismiss button
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Dismiss")
            }
        }
    }
}
