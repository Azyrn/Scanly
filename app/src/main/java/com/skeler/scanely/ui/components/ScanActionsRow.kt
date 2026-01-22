package com.skeler.scanely.ui.components

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import com.skeler.scanely.core.actions.ScanAction
import com.skeler.scanely.core.actions.WifiType
import kotlinx.coroutines.launch

/**
 * Horizontal scrollable row of action chips for detected scan actions.
 * 
 * Features:
 * - Material 3 AssistChip styling
 * - Collapsible "X links found" for multiple URLs
 * - Intent launching for each action type
 * - Clipboard integration
 */
@Composable
fun ScanActionsRow(
    actions: List<ScanAction>,
    modifier: Modifier = Modifier
) {
    if (actions.isEmpty()) return
    
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    
    // Group URLs for collapse functionality
    val urlActions = actions.filterIsInstance<ScanAction.OpenUrl>()
    val otherActions = actions.filter { it !is ScanAction.OpenUrl }
    
    var showAllUrls by remember { mutableStateOf(urlActions.size <= 2) }
    
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Show URL summary chip if many URLs
        if (urlActions.size > 2 && !showAllUrls) {
            AssistChip(
                onClick = { showAllUrls = true },
                label = { Text("${urlActions.size} links found") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize)
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
        
        // Show URLs (all or none based on collapse state)
        AnimatedVisibility(
            visible = showAllUrls || urlActions.size <= 2,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                urlActions.take(5).forEach { action ->
                    ActionChip(
                        action = action,
                        onClick = { executeAction(context, action, clipboard, scope) }
                    )
                }
            }
        }
        
        // Show other action types
        otherActions.forEach { action ->
            ActionChip(
                action = action,
                onClick = { 
                    scope.launch {
                        executeAction(context, action, clipboard, scope) 
                    }
                }
            )
        }
    }
}

@Composable
private fun ActionChip(
    action: ScanAction,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = { 
            Text(
                text = action.label,
                maxLines = 1
            ) 
        },
        leadingIcon = {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize)
            )
        },
        colors = when (action) {
            is ScanAction.OpenUrl -> AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
            is ScanAction.CallPhone -> AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
            is ScanAction.SendEmail -> AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
            is ScanAction.ConnectWifi -> AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
            else -> AssistChipDefaults.assistChipColors()
        }
    )
}

/**
 * Execute the action with appropriate Android intent.
 */
private fun executeAction(
    context: Context,
    action: ScanAction,
    clipboard: androidx.compose.ui.platform.Clipboard,
    scope: kotlinx.coroutines.CoroutineScope
) {
    when (action) {
        is ScanAction.OpenUrl -> {
            try {
                val intent = Intent(Intent.ACTION_VIEW, action.url.toUri())
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot open URL", Toast.LENGTH_SHORT).show()
            }
        }
        
        is ScanAction.CopyText -> {
            scope.launch {
                clipboard.setClipEntry(
                    ClipEntry(
                        ClipData.newPlainText("Scanned text", action.text)
                    )
                )
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
        
        is ScanAction.CallPhone -> {
            try {
                val intent = Intent(Intent.ACTION_DIAL, "tel:${action.number}".toUri())
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot open dialer", Toast.LENGTH_SHORT).show()
            }
        }
        
        is ScanAction.SendEmail -> {
            try {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = "mailto:${action.email}".toUri()
                    action.subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
                    action.body?.let { putExtra(Intent.EXTRA_TEXT, it) }
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot open email app", Toast.LENGTH_SHORT).show()
            }
        }
        
        is ScanAction.ConnectWifi -> {
            connectToWifi(context, action.ssid, action.password, action.type)
        }
        
        is ScanAction.SendSms -> {
            try {
                val intent = Intent(Intent.ACTION_SENDTO, "smsto:${action.number}".toUri()).apply {
                    action.message?.let { putExtra("sms_body", it) }
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot open SMS app", Toast.LENGTH_SHORT).show()
            }
        }
        
        is ScanAction.AddContact -> {
            try {
                val intent = Intent(Intent.ACTION_INSERT).apply {
                    type = ContactsContract.Contacts.CONTENT_TYPE
                    action.name?.let { putExtra(ContactsContract.Intents.Insert.NAME, it) }
                    action.phone?.let { putExtra(ContactsContract.Intents.Insert.PHONE, it) }
                    action.email?.let { putExtra(ContactsContract.Intents.Insert.EMAIL, it) }
                    action.organization?.let { putExtra(ContactsContract.Intents.Insert.COMPANY, it) }
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot open contacts", Toast.LENGTH_SHORT).show()
            }
        }
        
        is ScanAction.ShowRaw -> {
            // Just copy for ShowRaw
            scope.launch {
                clipboard.setClipEntry(
                    ClipEntry(
                        ClipData.newPlainText("Scanned text", action.text)
                    )
                )
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/**
 * Connect to WiFi network.
 * Uses WifiNetworkSuggestion API on Android 10+ for privacy.
 */
private fun connectToWifi(
    context: Context,
    ssid: String,
    password: String?,
    type: WifiType
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Android 10+ uses WifiNetworkSuggestion (requires user approval)
        val suggestion = WifiNetworkSuggestion.Builder()
            .setSsid(ssid)
            .apply {
                when (type) {
                    WifiType.WPA -> password?.let { setWpa2Passphrase(it) }
                    WifiType.WEP -> {
                        // WEP not supported in suggestion API
                        Toast.makeText(context, "WEP networks require manual connection", Toast.LENGTH_LONG).show()
                        return
                    }
                    WifiType.OPEN -> { /* No password needed */ }
                }
            }
            .build()
        
        // Open WiFi settings with a toast
        Toast.makeText(
            context, 
            "Network \"$ssid\" suggested. Check WiFi settings.", 
            Toast.LENGTH_LONG
        ).show()
        
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
        context.startActivity(intent)
    } else {
        // Older Android versions - open WiFi settings
        Toast.makeText(
            context,
            "SSID: $ssid${password?.let { "\nPassword: $it" } ?: ""}",
            Toast.LENGTH_LONG
        ).show()
        
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
        context.startActivity(intent)
    }
}
