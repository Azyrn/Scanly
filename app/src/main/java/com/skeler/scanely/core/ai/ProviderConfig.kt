package com.skeler.scanely.core.ai

import com.skeler.scanely.BuildConfig
import com.skeler.scanely.core.network.MistralApi
import com.skeler.scanely.core.security.Secrets
import com.skeler.scanely.settings.data.SettingsKeys
import com.skeler.scanely.settings.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

internal data class ProviderConfig(
    val kind: ProviderKind,
    val model: String,
    val apiKey: String,
    val url: String? = null, // required for OPENAI_COMPAT
    val usesBundledKey: Boolean = false // true only when the key came from the bundled default, not the user
) {
    companion object {
        private const val MISTRAL_CHAT_MODEL = "mistral-small-latest"

        /** App default Mistral OCR model — the only model eligible for the older-model retry. */
        const val MISTRAL_OCR_DEFAULT = "mistral-ocr-latest"

        // Mistral's OCR endpoint can't translate or read plain text; use its chat API.
        fun mistralChat(apiKey: String) =
            ProviderConfig(ProviderKind.OPENAI_COMPAT, MISTRAL_CHAT_MODEL, apiKey, MistralApi.CHAT_URL)
    }
}

// Resolves endpoint/model/key per provider (settings, then bundled key) and the fallback order.
@Singleton
internal class ProviderConfigResolver @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    // null when not usable yet: no key, or an unconfigured custom endpoint.
    suspend fun resolve(provider: AiProvider): ProviderConfig? = when (provider) {
        AiProvider.GEMINI -> {
            val userKey = settingValue(SettingsKeys.GEMINI_API_KEY)
            val key = userKey ?: Secrets.gemini.trim().ifBlank { null }
            val model = settingValue(SettingsKeys.GEMINI_MODEL) ?: GEMINI_MODEL
            key?.let { ProviderConfig(ProviderKind.GEMINI, model, it, usesBundledKey = userKey == null) }
        }
        AiProvider.OPENROUTER -> {
            val userKey = settingValue(SettingsKeys.OPENROUTER_API_KEY)
            val key = userKey ?: Secrets.openRouter.trim().ifBlank { null }
            val model = settingValue(SettingsKeys.OPENROUTER_MODEL) ?: OPENROUTER_MODEL
            key?.let { ProviderConfig(ProviderKind.OPENAI_COMPAT, model, it, OPENROUTER_URL, usesBundledKey = userKey == null) }
        }
        AiProvider.HUGGINGFACE -> {
            val userKey = settingValue(SettingsKeys.HUGGINGFACE_API_KEY)
            val key = userKey ?: Secrets.huggingFace.trim().ifBlank { null }
            // A manual model override wins; otherwise the selected OCR engine decides.
            val engineModel = OcrEngine.fromId(settingValue(SettingsKeys.OCR_ENGINE)).model
            val model = settingValue(SettingsKeys.HUGGINGFACE_MODEL) ?: engineModel
            key?.let { ProviderConfig(ProviderKind.OPENAI_COMPAT, model, it, HUGGINGFACE_URL, usesBundledKey = userKey == null) }
        }
        AiProvider.NVIDIA -> {
            val userKey = settingValue(SettingsKeys.NVIDIA_API_KEY)
            val key = userKey ?: Secrets.nvidia.trim().ifBlank { null }
            val model = settingValue(SettingsKeys.NVIDIA_MODEL) ?: NVIDIA_MODEL
            key?.let { ProviderConfig(ProviderKind.OPENAI_COMPAT, model, it, NVIDIA_URL, usesBundledKey = userKey == null) }
        }
        AiProvider.GROQ -> {
            val userKey = settingValue(SettingsKeys.GROQ_API_KEY)
            val key = userKey ?: Secrets.groq.trim().ifBlank { null }
            val model = settingValue(SettingsKeys.GROQ_MODEL) ?: GROQ_MODEL
            key?.let { ProviderConfig(ProviderKind.OPENAI_COMPAT, model, it, GROQ_URL, usesBundledKey = userKey == null) }
        }
        AiProvider.CEREBRAS -> {
            val userKey = settingValue(SettingsKeys.CEREBRAS_API_KEY)
            val key = userKey ?: Secrets.cerebras.trim().ifBlank { null }
            val model = settingValue(SettingsKeys.CEREBRAS_MODEL) ?: CEREBRAS_MODEL
            key?.let { ProviderConfig(ProviderKind.OPENAI_COMPAT, model, it, CEREBRAS_URL, usesBundledKey = userKey == null) }
        }
        AiProvider.CLOUDFLARE -> {
            // A token is bound to its account, so key + account id travel as a pair:
            // use the user's pair when both are set, else fall back to the bundled pair.
            val userKey = settingValue(SettingsKeys.CLOUDFLARE_API_KEY)
            val userAccount = settingValue(SettingsKeys.CLOUDFLARE_ACCOUNT_ID)
            val bundledKey = Secrets.cloudflareToken.trim().ifBlank { null }
            val bundledAccount = Secrets.cloudflareAccountId.trim().ifBlank { null }
            val pair = when {
                userKey != null && userAccount != null -> Triple(userKey, userAccount, false)
                bundledKey != null && bundledAccount != null -> Triple(bundledKey, bundledAccount, true)
                else -> null
            }
            pair?.let { (key, accountId, bundled) ->
                val model = settingValue(SettingsKeys.CLOUDFLARE_MODEL) ?: CLOUDFLARE_MODEL
                ProviderConfig(ProviderKind.OPENAI_COMPAT, model, key, cloudflareUrl(accountId), usesBundledKey = bundled)
            }
        }
        AiProvider.OPENAI -> settingValue(SettingsKeys.OPENAI_API_KEY)?.let {
            val model = settingValue(SettingsKeys.OPENAI_MODEL) ?: OPENAI_MODEL
            ProviderConfig(ProviderKind.OPENAI_COMPAT, model, it, OPENAI_URL)
        }
        AiProvider.CLAUDE -> settingValue(SettingsKeys.CLAUDE_API_KEY)?.let {
            val model = settingValue(SettingsKeys.CLAUDE_MODEL) ?: CLAUDE_MODEL
            ProviderConfig(ProviderKind.ANTHROPIC, model, it)
        }
        AiProvider.MISTRAL -> {
            val userKey = settingValue(SettingsKeys.MISTRAL_API_KEY)
            val key = userKey ?: Secrets.mistral.trim().ifBlank { null }
            val model = settingValue(SettingsKeys.MISTRAL_MODEL) ?: ProviderConfig.MISTRAL_OCR_DEFAULT
            key?.let { ProviderConfig(ProviderKind.MISTRAL_OCR, model, it, usesBundledKey = userKey == null) }
        }
        AiProvider.CUSTOM -> {
            val key = settingValue(SettingsKeys.CUSTOM_API_KEY)
            val url = settingValue(SettingsKeys.CUSTOM_BASE_URL)
            val model = settingValue(SettingsKeys.CUSTOM_MODEL)
            if (key != null && url != null && model != null) {
                ProviderConfig(ProviderKind.OPENAI_COMPAT, model, key, url)
            } else null
        }
    }

    // The selected provider is authoritative. It runs alone unless the user opted
    // into cross-provider fallback, in which case other configured providers follow
    // it. If the selected provider isn't configured (no key), the chain is empty and
    // the caller surfaces a missing-key error — never a silent substitution.
    //
    // When the primary runs on the user's own key, the fallback is limited to
    // providers the user also keyed themselves: it never drops down to a bundled
    // built-in key. A user-key run therefore never leaks onto a developer key.
    suspend fun chain(selected: AiProvider): List<Pair<AiProvider, ProviderConfig>> {
        val primary = resolve(selected)?.let { selected to it } ?: return emptyList()
        if (!fallbackEnabled()) return listOf(primary)
        val userKeyedOnly = !primary.second.usesBundledKey
        val rest = FALLBACK_ORDER
            .filter { it != selected }
            .mapNotNull { provider -> resolve(provider)?.let { provider to it } }
            .filter { (_, config) -> !userKeyedOnly || !config.usesBundledKey }
        return listOf(primary) + rest
    }

    // Cross-provider fallback is opt-in and off by default.
    private suspend fun fallbackEnabled(): Boolean =
        settingsRepository.getBoolean(SettingsKeys.AI_PROVIDER_FALLBACK).first()

    // Providers on the bundled key right now (drops one when its user key is saved).
    // The shared free-tier rate limit applies to exactly this set.
    fun bundledKeyProviders(): Flow<Set<AiProvider>> = combine(
        settingsRepository.getString(SettingsKeys.GEMINI_API_KEY),
        settingsRepository.getString(SettingsKeys.MISTRAL_API_KEY),
        settingsRepository.getString(SettingsKeys.OPENROUTER_API_KEY),
        settingsRepository.getString(SettingsKeys.HUGGINGFACE_API_KEY),
        settingsRepository.getString(SettingsKeys.NVIDIA_API_KEY),
        settingsRepository.getString(SettingsKeys.CLOUDFLARE_API_KEY),
        settingsRepository.getString(SettingsKeys.CLOUDFLARE_ACCOUNT_ID),
        settingsRepository.getString(SettingsKeys.GROQ_API_KEY),
        settingsRepository.getString(SettingsKeys.CEREBRAS_API_KEY)
    ) { keys ->
        buildSet {
            if (keys[0].isBlank() && BUNDLED_GEMINI) add(AiProvider.GEMINI)
            if (keys[1].isBlank() && BUNDLED_MISTRAL) add(AiProvider.MISTRAL)
            if (keys[2].isBlank() && BUNDLED_OPENROUTER) add(AiProvider.OPENROUTER)
            if (keys[3].isBlank() && BUNDLED_HUGGINGFACE) add(AiProvider.HUGGINGFACE)
            if (keys[4].isBlank() && BUNDLED_NVIDIA) add(AiProvider.NVIDIA)
            // Cloudflare falls back to the bundled pair unless BOTH user fields are set
            // (resolve() uses key + account id as an inseparable pair).
            if ((keys[5].isBlank() || keys[6].isBlank()) && BUNDLED_CLOUDFLARE) add(AiProvider.CLOUDFLARE)
            if (keys[7].isBlank() && BUNDLED_GROQ) add(AiProvider.GROQ)
            if (keys[8].isBlank() && BUNDLED_CEREBRAS) add(AiProvider.CEREBRAS)
        }
    }

    // Conservative default before settings load: assume each is on the shared quota.
    val bundledCapableProviders: Set<AiProvider> = buildSet {
        if (BUNDLED_GEMINI) add(AiProvider.GEMINI)
        if (BUNDLED_MISTRAL) add(AiProvider.MISTRAL)
        if (BUNDLED_OPENROUTER) add(AiProvider.OPENROUTER)
        if (BUNDLED_HUGGINGFACE) add(AiProvider.HUGGINGFACE)
        if (BUNDLED_NVIDIA) add(AiProvider.NVIDIA)
        if (BUNDLED_CLOUDFLARE) add(AiProvider.CLOUDFLARE)
        if (BUNDLED_GROQ) add(AiProvider.GROQ)
        if (BUNDLED_CEREBRAS) add(AiProvider.CEREBRAS)
    }

    // Trimmed, or null if unset/blank.
    private suspend fun settingValue(key: SettingsKeys): String? =
        settingsRepository.getString(key).first().trim().ifBlank { null }

    companion object {
        // All models below are vision-capable.
        private const val OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"
        private const val OPENROUTER_MODEL = "google/gemma-4-26b-a4b-it:free"
        private const val HUGGINGFACE_URL = "https://router.huggingface.co/v1/chat/completions"
        private const val NVIDIA_URL = "https://integrate.api.nvidia.com/v1/chat/completions"
        private const val NVIDIA_MODEL = "google/gemma-4-31b-it"
        private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
        private const val GROQ_MODEL = "qwen/qwen3.6-27b"
        private const val CEREBRAS_URL = "https://api.cerebras.ai/v1/chat/completions"
        private const val CEREBRAS_MODEL = "gemma-4-31b"
        private const val CLOUDFLARE_MODEL = "@cf/mistralai/mistral-small-3.1-24b-instruct"
        // Workers AI OpenAI-compatible endpoint; the account id is part of the path.
        fun cloudflareUrl(accountId: String) =
            "https://api.cloudflare.com/client/v4/accounts/$accountId/ai/v1/chat/completions"
        private const val OPENAI_URL = "https://api.openai.com/v1/chat/completions"
        private const val OPENAI_MODEL = "gpt-4o-mini"
        private const val CLAUDE_MODEL = "claude-haiku-4-5-20251001"
        private const val GEMINI_MODEL = "gemma-4-31b-it"

        private val BUNDLED_GEMINI = BuildConfig.GEMINI_API_KEY.isNotBlank()
        private val BUNDLED_MISTRAL = BuildConfig.MISTRAL_API_KEY.isNotBlank()
        private val BUNDLED_OPENROUTER = BuildConfig.OPENROUTER_API_KEY.isNotBlank()
        private val BUNDLED_HUGGINGFACE = BuildConfig.HUGGINGFACE_API_KEY.isNotBlank()
        private val BUNDLED_NVIDIA = BuildConfig.NVIDIA_API_KEY.isNotBlank()
        private val BUNDLED_GROQ = BuildConfig.GROQ_API_KEY.isNotBlank()
        private val BUNDLED_CEREBRAS = BuildConfig.CEREBRAS_API_KEY.isNotBlank()
        private val BUNDLED_CLOUDFLARE =
            BuildConfig.CLOUDFLARE_API_KEY.isNotBlank() && BuildConfig.CLOUDFLARE_ACCOUNT_ID.isNotBlank()

        private val FALLBACK_ORDER = listOf(
            AiProvider.GEMINI,
            AiProvider.MISTRAL,
            AiProvider.OPENROUTER,
            AiProvider.HUGGINGFACE,
            AiProvider.NVIDIA,
            AiProvider.GROQ,
            AiProvider.CEREBRAS,
            AiProvider.CLOUDFLARE,
            AiProvider.OPENAI,
            AiProvider.CLAUDE,
            AiProvider.CUSTOM
        )
    }
}
