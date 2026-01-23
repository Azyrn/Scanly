package com.skeler.scanely.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Unified error state component for consistent error display across the app.
 */
@Composable
fun ErrorStateContent(
    type: ErrorType,
    message: String? = null,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = type.icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = type.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = message ?: type.defaultMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

/**
 * Error types with predefined icons and messages.
 */
enum class ErrorType(
    val icon: ImageVector,
    val title: String,
    val defaultMessage: String
) {
    NETWORK(
        icon = Icons.Rounded.WifiOff,
        title = "No Internet Connection",
        defaultMessage = "Please check your connection and try again."
    ),
    TIMEOUT(
        icon = Icons.Rounded.CloudOff,
        title = "Request Timed Out",
        defaultMessage = "The server took too long to respond. Please try again."
    ),
    NOT_FOUND(
        icon = Icons.Rounded.SearchOff,
        title = "Product Not Found",
        defaultMessage = "We couldn't find information for this product."
    ),
    GENERIC(
        icon = Icons.Rounded.ErrorOutline,
        title = "Something Went Wrong",
        defaultMessage = "An unexpected error occurred. Please try again."
    )
}

/**
 * Determine error type from exception message.
 */
fun determineErrorType(message: String?): ErrorType {
    val lower = message?.lowercase() ?: ""
    return when {
        lower.contains("timeout") -> ErrorType.TIMEOUT
        lower.contains("no internet") || lower.contains("host") || 
                lower.contains("connect") -> ErrorType.NETWORK
        lower.contains("not found") -> ErrorType.NOT_FOUND
        else -> ErrorType.GENERIC
    }
}
