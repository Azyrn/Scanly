@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.skeler.scanely.settings.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.skeler.scanely.navigation.LocalNavController
import com.skeler.scanely.settings.data.SettingsKeys
import com.skeler.scanely.settings.presentation.viewmodel.SettingsViewModel

/**
 * Lets the user store their own API keys for each AI provider. Keys are kept
 * on-device (DataStore) and never shipped with the app. Choosing which provider
 * to use happens per-scan in the AI mode sheet.
 */
@Composable
fun AiProvidersScreen(
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val navController = LocalNavController.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text("AI Providers", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Text(
                    text = "Bring your own key. Keys are stored only on this device " +
                        "and used for the provider you pick when starting an AI scan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            item {
                ApiKeyField(
                    settingsViewModel = settingsViewModel,
                    settingsKey = SettingsKeys.GEMINI_API_KEY,
                    label = "Gemini (Google AI)",
                    icon = Icons.Rounded.AutoAwesome,
                    hint = "AIza...",
                    getKeyUrl = "https://aistudio.google.com/apikey",
                    getKeyLabel = "Get a Gemini key",
                    helper = "Default provider — works out of the box. Enter a key to use your own."
                )
            }

            item {
                ApiKeyField(
                    settingsViewModel = settingsViewModel,
                    settingsKey = SettingsKeys.MISTRAL_API_KEY,
                    label = "Mistral OCR",
                    icon = Icons.Rounded.DocumentScanner,
                    hint = "key",
                    getKeyUrl = "https://console.mistral.ai/api-keys",
                    getKeyLabel = "Get a Mistral key",
                    helper = "Dedicated OCR model (OCR-4) — fast, accurate document text. " +
                        "Free tier included, enter a key to use your own."
                )
            }

            item {
                ApiKeyField(
                    settingsViewModel = settingsViewModel,
                    settingsKey = SettingsKeys.OPENROUTER_API_KEY,
                    label = "OpenRouter",
                    icon = Icons.Rounded.Bolt,
                    hint = "sk-or-...",
                    getKeyUrl = "https://openrouter.ai/keys",
                    getKeyLabel = "Get an OpenRouter key"
                )
            }

            item {
                ApiKeyField(
                    settingsViewModel = settingsViewModel,
                    settingsKey = SettingsKeys.OPENAI_API_KEY,
                    label = "OpenAI",
                    icon = Icons.Rounded.Bolt,
                    hint = "sk-...",
                    getKeyUrl = "https://platform.openai.com/api-keys",
                    getKeyLabel = "Get an OpenAI key"
                )
            }

            item {
                ApiKeyField(
                    settingsViewModel = settingsViewModel,
                    settingsKey = SettingsKeys.CLAUDE_API_KEY,
                    label = "Claude (Anthropic)",
                    icon = Icons.Rounded.AutoAwesome,
                    hint = "sk-ant-...",
                    getKeyUrl = "https://console.anthropic.com/settings/keys",
                    getKeyLabel = "Get a Claude key"
                )
            }

            item {
                CustomProviderSection(settingsViewModel = settingsViewModel)
            }
        }
    }
}

/**
 * Custom OpenAI-compatible endpoint: base URL + model + key, all user-supplied.
 */
@Composable
private fun CustomProviderSection(settingsViewModel: SettingsViewModel) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = "Custom (OpenAI-compatible)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        PlainSettingField(
            settingsViewModel = settingsViewModel,
            settingsKey = SettingsKeys.CUSTOM_BASE_URL,
            label = "Endpoint URL",
            hint = "https://host/v1/chat/completions"
        )
        Spacer(modifier = Modifier.height(8.dp))
        PlainSettingField(
            settingsViewModel = settingsViewModel,
            settingsKey = SettingsKeys.CUSTOM_MODEL,
            label = "Model",
            hint = "model-id"
        )
        Spacer(modifier = Modifier.height(8.dp))
        ApiKeyField(
            settingsViewModel = settingsViewModel,
            settingsKey = SettingsKeys.CUSTOM_API_KEY,
            label = "API key",
            icon = Icons.Rounded.Key,
            hint = "key",
            getKeyUrl = null,
            getKeyLabel = null
        )
    }
}

/** Plain (non-masked) single-line setting field bound to [settingsKey]. */
@Composable
private fun PlainSettingField(
    settingsViewModel: SettingsViewModel,
    settingsKey: SettingsKeys,
    label: String,
    hint: String
) {
    var value by rememberSaveable(settingsKey) { mutableStateOf("") }
    var seeded by rememberSaveable(settingsKey) { mutableStateOf(false) }
    val stored by settingsViewModel.getString(settingsKey).collectAsState(initial = "")

    LaunchedEffect(stored, seeded) {
        if (!seeded && stored.isNotEmpty()) {
            value = stored
            seeded = true
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = {
            seeded = true
            value = it
            settingsViewModel.setString(settingsKey, it.trim())
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        placeholder = { Text(hint) },
        shape = MaterialTheme.shapes.large
    )
}

@Composable
private fun ApiKeyField(
    settingsViewModel: SettingsViewModel,
    settingsKey: SettingsKeys,
    label: String,
    icon: ImageVector,
    hint: String,
    getKeyUrl: String?,
    getKeyLabel: String?,
    helper: String? = null
) {
    val uriHandler = LocalUriHandler.current

    // Seed the field once from the stored value, then let the user edit freely
    // without the persisted value fighting the cursor.
    var value by rememberSaveable(settingsKey) { mutableStateOf("") }
    var seeded by rememberSaveable(settingsKey) { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }
    val stored by settingsViewModel.getString(settingsKey).collectAsState(initial = "")

    LaunchedEffect(stored, seeded) {
        if (!seeded && stored.isNotEmpty()) {
            value = stored
            seeded = true
        }
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = value,
            onValueChange = {
                seeded = true
                value = it
                settingsViewModel.setString(settingsKey, it.trim())
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(hint) },
            shape = MaterialTheme.shapes.large,
            visualTransformation = if (revealed) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions.Default,
            trailingIcon = {
                IconButton(onClick = { revealed = !revealed }) {
                    Icon(
                        imageVector = if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (revealed) "Hide key" else "Show key"
                    )
                }
            }
        )

        if (helper != null) {
            Text(
                text = helper,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp)
            )
        }

        if (getKeyUrl != null && getKeyLabel != null) {
            TextButton(onClick = { uriHandler.openUri(getKeyUrl) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(getKeyLabel)
            }
        }
    }
}
