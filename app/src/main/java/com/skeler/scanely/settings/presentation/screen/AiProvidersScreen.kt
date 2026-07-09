@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.skeler.scanely.settings.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AltRoute
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.skeler.scanely.core.ai.AiProvider
import com.skeler.scanely.navigation.LocalNavController
import com.skeler.scanely.settings.data.SettingsKeys
import com.skeler.scanely.settings.presentation.viewmodel.ProviderVerificationViewModel
import com.skeler.scanely.settings.presentation.viewmodel.SettingsViewModel
import com.skeler.scanely.settings.presentation.viewmodel.VerifyState
import com.skeler.scanely.ui.components.SettingSwitchTile
import com.skeler.scanely.ui.components.SettingsGroup
import com.skeler.scanely.ui.components.SettingsSectionHeader
import com.skeler.scanely.ui.icons.ProviderIcons

/**
 * Lets the user store their own API keys for each AI provider. Keys are kept
 * on-device (DataStore) and never shipped with the app. Choosing which provider
 * to use happens per-scan in the AI mode sheet.
 */
@Composable
fun AiProvidersScreen(
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    verificationViewModel: ProviderVerificationViewModel = hiltViewModel()
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
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Bring your own key. Keys are stored only on this device " +
                        "and used for the provider you pick when starting an AI scan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            item { BundledKeyNote() }

            item {
                SettingsSectionHeader(
                    text = "Providers",
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp)
                )
            }

            items(PROVIDERS, key = { it.settingsKey.name }) { spec ->
                ProviderCard(
                    settingsViewModel = settingsViewModel,
                    verificationViewModel = verificationViewModel,
                    spec = spec
                )
            }

            item {
                SettingsSectionHeader(
                    text = "Advanced",
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp)
                )
            }

            item {
                CloudflareProviderCard(
                    settingsViewModel = settingsViewModel,
                    verificationViewModel = verificationViewModel
                )
            }

            item {
                CustomProviderCard(
                    settingsViewModel = settingsViewModel,
                    verificationViewModel = verificationViewModel
                )
            }

            item {
                SettingsSectionHeader(
                    text = "Fallback",
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp)
                )
            }

            item {
                val fallbackEnabled by settingsViewModel
                    .getBoolean(SettingsKeys.AI_PROVIDER_FALLBACK)
                    .collectAsState(initial = false)
                SettingsGroup {
                    SettingSwitchTile(
                        title = "Fall back to other providers",
                        subtitle = "If your selected provider fails, try other " +
                            "configured providers instead of stopping. Off by default — " +
                            "your chosen provider is always used exactly.",
                        icon = Icons.Rounded.AltRoute,
                        checked = fallbackEnabled,
                        onCheckedChange = {
                            settingsViewModel.setBoolean(SettingsKeys.AI_PROVIDER_FALLBACK, it)
                        }
                    )
                }
            }
        }
    }
}

/** Tonal note explaining that scans run on a shared free-tier key until the user adds their own. */
@Composable
private fun BundledKeyNote() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Using the bundled free-tier API key. Add your own provider API key " +
                    "to use your personal quota and avoid shared limits.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/** Declarative description of a single built-in provider. */
private data class ProviderSpec(
    val provider: AiProvider,
    val settingsKey: SettingsKeys,
    val modelKey: SettingsKeys,
    /** Built-in model used when the model field is left blank; shown as its placeholder. */
    val defaultModel: String,
    val name: String,
    val icon: ImageVector,
    val hint: String,
    val getKeyUrl: String,
    val description: String? = null
)

private val PROVIDERS = listOf(
    ProviderSpec(
        provider = AiProvider.GEMINI,
        settingsKey = SettingsKeys.GEMINI_API_KEY,
        modelKey = SettingsKeys.GEMINI_MODEL,
        defaultModel = "gemma-4-31b-it",
        name = "Gemini",
        icon = ProviderIcons.Gemini,
        hint = "AIza…",
        getKeyUrl = "https://aistudio.google.com/apikey",
        description = "Google AI · works out of the box. Add a key to use your own quota."
    ),
    ProviderSpec(
        provider = AiProvider.MISTRAL,
        settingsKey = SettingsKeys.MISTRAL_API_KEY,
        modelKey = SettingsKeys.MISTRAL_MODEL,
        defaultModel = "mistral-ocr-latest",
        name = "Mistral OCR",
        icon = ProviderIcons.Mistral,
        hint = "key",
        getKeyUrl = "https://console.mistral.ai/api-keys",
        description = "Dedicated OCR-4 model · fast, accurate document text. Free tier included."
    ),
    ProviderSpec(
        provider = AiProvider.OPENROUTER,
        settingsKey = SettingsKeys.OPENROUTER_API_KEY,
        modelKey = SettingsKeys.OPENROUTER_MODEL,
        defaultModel = "google/gemma-4-26b-a4b-it:free",
        name = "OpenRouter",
        icon = ProviderIcons.OpenRouter,
        hint = "sk-or-…",
        getKeyUrl = "https://openrouter.ai/keys",
        description = "One key, many models."
    ),
    ProviderSpec(
        provider = AiProvider.HUGGINGFACE,
        settingsKey = SettingsKeys.HUGGINGFACE_API_KEY,
        modelKey = SettingsKeys.HUGGINGFACE_MODEL,
        defaultModel = "Qwen/Qwen3-VL-30B-A3B-Instruct",
        name = "Hugging Face",
        icon = ProviderIcons.HuggingFace,
        hint = "hf_…",
        getKeyUrl = "https://huggingface.co/settings/tokens",
        description = "Qwen OCR via HF Inference Providers. Free tier included."
    ),
    ProviderSpec(
        provider = AiProvider.NVIDIA,
        settingsKey = SettingsKeys.NVIDIA_API_KEY,
        modelKey = SettingsKeys.NVIDIA_MODEL,
        defaultModel = "google/gemma-4-31b-it",
        name = "NVIDIA",
        icon = ProviderIcons.Nvidia,
        hint = "nvapi-…",
        getKeyUrl = "https://build.nvidia.com/settings/api-keys",
        description = "NVIDIA NIM · vision models on NVIDIA's API. Free tier included."
    ),
    ProviderSpec(
        provider = AiProvider.GROQ,
        settingsKey = SettingsKeys.GROQ_API_KEY,
        modelKey = SettingsKeys.GROQ_MODEL,
        defaultModel = "qwen/qwen3.6-27b",
        name = "Groq",
        icon = ProviderIcons.Groq,
        hint = "gsk_…",
        getKeyUrl = "https://console.groq.com/keys",
        description = "Groq LPU · very fast inference. Free tier included."
    ),
    ProviderSpec(
        provider = AiProvider.CEREBRAS,
        settingsKey = SettingsKeys.CEREBRAS_API_KEY,
        modelKey = SettingsKeys.CEREBRAS_MODEL,
        defaultModel = "gemma-4-31b",
        name = "Cerebras",
        icon = ProviderIcons.Cerebras,
        hint = "csk-…",
        getKeyUrl = "https://cloud.cerebras.ai",
        description = "Cerebras · ultra-fast Gemma-4 vision inference. Free tier included."
    ),
    ProviderSpec(
        provider = AiProvider.OPENAI,
        settingsKey = SettingsKeys.OPENAI_API_KEY,
        modelKey = SettingsKeys.OPENAI_MODEL,
        defaultModel = "gpt-4o-mini",
        name = "OpenAI",
        icon = ProviderIcons.OpenAi,
        hint = "sk-…",
        getKeyUrl = "https://platform.openai.com/api-keys",
        description = "GPT vision models."
    ),
    ProviderSpec(
        provider = AiProvider.CLAUDE,
        settingsKey = SettingsKeys.CLAUDE_API_KEY,
        modelKey = SettingsKeys.CLAUDE_MODEL,
        defaultModel = "claude-haiku-4-5-20251001",
        name = "Claude",
        icon = ProviderIcons.Anthropic,
        hint = "sk-ant-…",
        getKeyUrl = "https://console.anthropic.com/settings/keys",
        description = "Anthropic vision models."
    )
)

/**
 * A single provider on its own tonal card: identity header with a live status,
 * a masked key field, and an elegant secondary "Get key" action. The card border
 * blooms to the accent colour while its field holds focus.
 */
@Composable
private fun ProviderCard(
    settingsViewModel: SettingsViewModel,
    verificationViewModel: ProviderVerificationViewModel,
    spec: ProviderSpec
) {
    val uriHandler = LocalUriHandler.current

    var value by rememberSaveable(spec.settingsKey) { mutableStateOf("") }
    var seeded by rememberSaveable(spec.settingsKey) { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }
    val stored by settingsViewModel.getString(spec.settingsKey).collectAsState(initial = "")

    LaunchedEffect(stored, seeded) {
        if (!seeded && stored.isNotEmpty()) {
            value = stored
            seeded = true
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val entries by verificationViewModel.states.collectAsState()
    val verifyState = verificationViewModel.resolve(entries[spec.provider], value)

    ProviderContainer(focused = focused) {
        ProviderHeader(
            name = spec.name,
            icon = spec.icon,
            description = spec.description,
            verifyState = verifyState
        )

        OutlinedTextField(
            value = value,
            onValueChange = {
                seeded = true
                value = it
                settingsViewModel.setString(spec.settingsKey, it.trim())
                verificationViewModel.onKeyChanged(spec.provider, it)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = verifyState is VerifyState.Invalid,
            label = { Text("API key") },
            placeholder = { Text(spec.hint) },
            leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
            trailingIcon = { RevealToggle(revealed) { revealed = !revealed } },
            supportingText = statusMessage(verifyState)?.let { msg -> { Text(msg) } },
            visualTransformation = keyTransform(revealed),
            keyboardOptions = KEY_KEYBOARD,
            shape = MaterialTheme.shapes.large,
            interactionSource = interactionSource
        )

        PlainField(
            settingsViewModel = settingsViewModel,
            settingsKey = spec.modelKey,
            label = "Model",
            hint = spec.defaultModel,
            leading = Icons.Rounded.Category
        )

        ProviderActions(
            verifyState = verifyState,
            getKeyLabel = "Get ${spec.name} key",
            onVerify = { verificationViewModel.verify(spec.provider, value) },
            onGetKey = { uriHandler.openUri(spec.getKeyUrl) }
        )
    }
}

/**
 * Cloudflare Workers AI: account id + token + model. The account id is folded
 * into the run URL rather than typed as a full endpoint, so it gets its own
 * plain field and is passed to verification as the "custom URL".
 */
@Composable
private fun CloudflareProviderCard(
    settingsViewModel: SettingsViewModel,
    verificationViewModel: ProviderVerificationViewModel
) {
    val uriHandler = LocalUriHandler.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    var accountId by rememberSaveable { mutableStateOf("") }
    var accountSeeded by rememberSaveable { mutableStateOf(false) }
    var key by rememberSaveable { mutableStateOf("") }
    var keySeeded by rememberSaveable { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }
    val storedAccount by settingsViewModel.getString(SettingsKeys.CLOUDFLARE_ACCOUNT_ID).collectAsState(initial = "")
    val storedKey by settingsViewModel.getString(SettingsKeys.CLOUDFLARE_API_KEY).collectAsState(initial = "")

    LaunchedEffect(storedAccount, accountSeeded) {
        if (!accountSeeded && storedAccount.isNotEmpty()) {
            accountId = storedAccount
            accountSeeded = true
        }
    }
    LaunchedEffect(storedKey, keySeeded) {
        if (!keySeeded && storedKey.isNotEmpty()) {
            key = storedKey
            keySeeded = true
        }
    }

    val entries by verificationViewModel.states.collectAsState()
    val verifyState = verificationViewModel.resolve(entries[AiProvider.CLOUDFLARE], key)

    ProviderContainer(focused = focused) {
        ProviderHeader(
            name = "Cloudflare",
            icon = ProviderIcons.Cloudflare,
            description = "Workers AI · vision on Cloudflare's edge. Free tier included; " +
                "add your own Account ID + token to use your quota.",
            verifyState = verifyState
        )

        OutlinedTextField(
            value = accountId,
            onValueChange = {
                accountSeeded = true
                accountId = it
                settingsViewModel.setString(SettingsKeys.CLOUDFLARE_ACCOUNT_ID, it.trim())
                // Account id is part of the run URL — a change invalidates a prior key check.
                verificationViewModel.invalidate(AiProvider.CLOUDFLARE)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Account ID") },
            placeholder = { Text("32-char account id") },
            leadingIcon = { Icon(Icons.Rounded.Tag, contentDescription = null) },
            shape = MaterialTheme.shapes.large
        )

        PlainField(
            settingsViewModel = settingsViewModel,
            settingsKey = SettingsKeys.CLOUDFLARE_MODEL,
            label = "Model",
            hint = "@cf/mistralai/mistral-small-3.1-24b-instruct",
            leading = Icons.Rounded.Category
        )

        OutlinedTextField(
            value = key,
            onValueChange = {
                keySeeded = true
                key = it
                settingsViewModel.setString(SettingsKeys.CLOUDFLARE_API_KEY, it.trim())
                verificationViewModel.onKeyChanged(AiProvider.CLOUDFLARE, it)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = verifyState is VerifyState.Invalid,
            label = { Text("API token") },
            placeholder = { Text("cfut_…") },
            leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
            trailingIcon = { RevealToggle(revealed) { revealed = !revealed } },
            supportingText = statusMessage(verifyState)?.let { msg -> { Text(msg) } },
            visualTransformation = keyTransform(revealed),
            keyboardOptions = KEY_KEYBOARD,
            shape = MaterialTheme.shapes.large,
            interactionSource = interactionSource
        )

        ProviderActions(
            verifyState = verifyState,
            getKeyLabel = "Get Cloudflare token",
            onVerify = { verificationViewModel.verify(AiProvider.CLOUDFLARE, key, customUrl = accountId) },
            onGetKey = { uriHandler.openUri("https://dash.cloudflare.com/profile/api-tokens") }
        )
    }
}

/**
 * Custom OpenAI-compatible endpoint: base URL + model + key, all user-supplied,
 * grouped on one card that matches the built-in providers.
 */
@Composable
private fun CustomProviderCard(
    settingsViewModel: SettingsViewModel,
    verificationViewModel: ProviderVerificationViewModel
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    var url by rememberSaveable { mutableStateOf("") }
    var urlSeeded by rememberSaveable { mutableStateOf(false) }
    var key by rememberSaveable { mutableStateOf("") }
    var keySeeded by rememberSaveable { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(false) }
    val storedUrl by settingsViewModel.getString(SettingsKeys.CUSTOM_BASE_URL).collectAsState(initial = "")
    val storedKey by settingsViewModel.getString(SettingsKeys.CUSTOM_API_KEY).collectAsState(initial = "")

    LaunchedEffect(storedUrl, urlSeeded) {
        if (!urlSeeded && storedUrl.isNotEmpty()) {
            url = storedUrl
            urlSeeded = true
        }
    }
    LaunchedEffect(storedKey, keySeeded) {
        if (!keySeeded && storedKey.isNotEmpty()) {
            key = storedKey
            keySeeded = true
        }
    }

    val entries by verificationViewModel.states.collectAsState()
    val verifyState = verificationViewModel.resolve(entries[AiProvider.CUSTOM], key)

    ProviderContainer(focused = focused) {
        ProviderHeader(
            name = "Custom endpoint",
            icon = Icons.Rounded.Tune,
            description = "Any OpenAI-compatible chat/completions API.",
            verifyState = verifyState
        )

        OutlinedTextField(
            value = url,
            onValueChange = {
                urlSeeded = true
                url = it
                settingsViewModel.setString(SettingsKeys.CUSTOM_BASE_URL, it.trim())
                // Endpoint change invalidates a prior key check.
                verificationViewModel.invalidate(AiProvider.CUSTOM)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Endpoint URL") },
            placeholder = { Text("https://host/v1/chat/completions") },
            leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
            shape = MaterialTheme.shapes.large
        )

        PlainField(
            settingsViewModel = settingsViewModel,
            settingsKey = SettingsKeys.CUSTOM_MODEL,
            label = "Model",
            hint = "model-id",
            leading = Icons.Rounded.Category
        )

        OutlinedTextField(
            value = key,
            onValueChange = {
                keySeeded = true
                key = it
                settingsViewModel.setString(SettingsKeys.CUSTOM_API_KEY, it.trim())
                verificationViewModel.onKeyChanged(AiProvider.CUSTOM, it)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = verifyState is VerifyState.Invalid,
            label = { Text("API key") },
            placeholder = { Text("key") },
            leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
            trailingIcon = { RevealToggle(revealed) { revealed = !revealed } },
            supportingText = statusMessage(verifyState)?.let { msg -> { Text(msg) } },
            visualTransformation = keyTransform(revealed),
            keyboardOptions = KEY_KEYBOARD,
            shape = MaterialTheme.shapes.large,
            interactionSource = interactionSource
        )

        ProviderActions(
            verifyState = verifyState,
            getKeyLabel = null,
            onVerify = { verificationViewModel.verify(AiProvider.CUSTOM, key, customUrl = url) },
            onGetKey = null
        )
    }
}

/** Tonal card shell shared by every provider; its border animates on focus. */
@Composable
private fun ProviderContainer(
    focused: Boolean,
    content: @Composable () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (focused) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
        },
        animationSpec = tween(220),
        label = "cardBorder"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(if (focused) 1.5.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = { content() }
        )
    }
}

/** Identity row: tonal icon squircle, name + description, and a live status chip. */
@Composable
private fun ProviderHeader(
    name: String,
    icon: ImageVector,
    description: String?,
    verifyState: VerifyState
) {
    // Only a confirmed-valid key lights the icon up; a merely-present key does not.
    val verified = verifyState is VerifyState.Verified
    val iconContainer by animateColorAsState(
        targetValue = if (verified) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = tween(220),
        label = "iconContainer"
    )
    val iconTint = if (verified) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(iconContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        StatusChip(verifyState = verifyState)
    }
}

/** Visual style for a [StatusChip], derived from the current [VerifyState]. */
private data class ChipStyle(
    val label: String,
    val icon: ImageVector?,
    val container: Color,
    val content: Color,
    val spinner: Boolean = false
)

@Composable
private fun chipStyleFor(state: VerifyState): ChipStyle? {
    val scheme = MaterialTheme.colorScheme
    return when (state) {
        VerifyState.NotConfigured, VerifyState.Entered -> null
        VerifyState.Verifying -> ChipStyle(
            label = "Verifying",
            icon = null,
            container = scheme.surfaceContainerHighest,
            content = scheme.onSurfaceVariant,
            spinner = true
        )
        VerifyState.Verified -> ChipStyle(
            label = "Verified",
            icon = Icons.Rounded.CheckCircle,
            container = scheme.primaryContainer,
            content = scheme.onPrimaryContainer
        )
        is VerifyState.Invalid -> ChipStyle(
            label = "Invalid",
            icon = Icons.Rounded.ErrorOutline,
            container = scheme.errorContainer,
            content = scheme.onErrorContainer
        )
        is VerifyState.Failed -> ChipStyle(
            label = "Unverified",
            icon = Icons.Rounded.ErrorOutline,
            container = scheme.surfaceContainerHighest,
            content = scheme.onSurfaceVariant
        )
    }
}

/** A status chip that animates in/out as verification progresses. */
@Composable
private fun StatusChip(verifyState: VerifyState) {
    val style = chipStyleFor(verifyState)
    // Keep the last visible style so the exit transition doesn't flash empty.
    var lastStyle by remember { mutableStateOf(style) }
    if (style != null) lastStyle = style

    AnimatedVisibility(
        visible = style != null,
        enter = fadeIn(tween(200)) + expandVertically(tween(200)),
        exit = fadeOut(tween(160)) + shrinkVertically(tween(160))
    ) {
        val shown = lastStyle ?: return@AnimatedVisibility
        Surface(shape = CircleShape, color = shown.container) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (shown.spinner) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = shown.content
                    )
                } else if (shown.icon != null) {
                    Icon(
                        imageVector = shown.icon,
                        contentDescription = null,
                        tint = shown.content,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = shown.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = shown.content
                )
            }
        }
    }
}

/** Action row: a state-aware "Verify" button and an optional "Get key" link. */
@Composable
private fun ProviderActions(
    verifyState: VerifyState,
    getKeyLabel: String?,
    onVerify: () -> Unit,
    onGetKey: (() -> Unit)?
) {
    // Nothing to verify or open when no key is present and there's no key page.
    if (verifyState is VerifyState.NotConfigured && getKeyLabel == null) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (verifyState !is VerifyState.NotConfigured) {
            VerifyButton(verifyState = verifyState, onClick = onVerify)
        }
        if (getKeyLabel != null && onGetKey != null) {
            GetKeyButton(label = getKeyLabel, onClick = onGetKey)
        }
    }
}

/**
 * Primary action for a provider: triggers verification. Disabled (with a spinner)
 * while a check is running and while a key is already verified, so repeat taps
 * can't fire redundant requests.
 */
@Composable
private fun VerifyButton(verifyState: VerifyState, onClick: () -> Unit) {
    val verifying = verifyState is VerifyState.Verifying
    val verified = verifyState is VerifyState.Verified
    val label = when {
        verifying -> "Verifying…"
        verified -> "Verified"
        verifyState is VerifyState.Invalid || verifyState is VerifyState.Failed -> "Retry"
        else -> "Verify key"
    }

    FilledTonalButton(
        onClick = onClick,
        enabled = !verifying && !verified,
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        when {
            verifying -> CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            verified -> Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            else -> Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

/** Supporting message shown under the key field for error states. */
private fun statusMessage(state: VerifyState): String? = when (state) {
    is VerifyState.Invalid -> state.message
    is VerifyState.Failed -> state.message
    else -> null
}

/** Password visibility toggle used by every key field. */
@Composable
private fun RevealToggle(revealed: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            imageVector = if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
            contentDescription = if (revealed) "Hide key" else "Show key",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Elegant secondary action that opens the provider's key page. */
@Composable
private fun GetKeyButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

/** Plain (non-masked) single-line setting field bound to [settingsKey]. */
@Composable
private fun PlainField(
    settingsViewModel: SettingsViewModel,
    settingsKey: SettingsKeys,
    label: String,
    hint: String,
    leading: ImageVector
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
        leadingIcon = { Icon(leading, contentDescription = null) },
        shape = MaterialTheme.shapes.large
    )
}

private fun keyTransform(revealed: Boolean): VisualTransformation =
    if (revealed) VisualTransformation.None else PasswordVisualTransformation()

// Password keyboard so the IME never learns or suggests API keys.
private val KEY_KEYBOARD = KeyboardOptions(keyboardType = KeyboardType.Password)
