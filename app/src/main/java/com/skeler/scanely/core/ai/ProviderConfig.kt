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
    val usesBundledKey: Boolean = false // true only for bundled default, not user key
) {
    companion object {
        private const val MISTRAL_CHAT_MODEL = "mistral-small-latest"

        const val MISTRAL_OCR_DEFAULT = "mistral-ocr-latest"

        fun mistralChat(apiKey: String) =
            ProviderConfig(ProviderKind.OPENAI_COMPAT, MISTRAL_CHAT_MODEL, apiKey, MistralApi.CHAT_URL)
    }
}

@Singleton
internal class ProviderConfigResolver @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
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

    private suspend fun fallbackEnabled(): Boolean =
        settingsRepository.getBoolean(SettingsKeys.AI_PROVIDER_FALLBACK).first()

    // Providers currently on the bundled key (shared free-tier rate limit).
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
            if ((keys[5].isBlank() || keys[6].isBlank()) && BUNDLED_CLOUDFLARE) add(AiProvider.CLOUDFLARE)
            if (keys[7].isBlank() && BUNDLED_GROQ) add(AiProvider.GROQ)
            if (keys[8].isBlank() && BUNDLED_CEREBRAS) add(AiProvider.CEREBRAS)
        }
    }

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

    private suspend fun settingValue(key: SettingsKeys): String? =
        settingsRepository.getString(key).first().trim().ifBlank { null }

    companion object {
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
        // Account id is part of the Workers AI path.
        fun cloudflareUrl(accountId: String) =
            "https://api.cloudflare.com/client/v4/accounts/$accountId/ai/v1/chat/completions"
        private const val OPENAI_URL = "https://api.openai.com/v1/chat/completions"
        private const val OPENAI_MODEL = "gpt-4o-mini"
        private const val CLAUDE_MODEL = "claude-haiku-4-5-20251001"
        private const val GEMINI_MODEL = "gemini-3.1-flash-lite"

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
