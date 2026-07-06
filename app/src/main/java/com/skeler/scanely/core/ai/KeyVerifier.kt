package com.skeler.scanely.core.ai

import com.skeler.scanely.core.network.ClaudeApi
import com.skeler.scanely.core.network.KeyValidationApi
import com.skeler.scanely.core.network.NetworkObserver
import kotlinx.coroutines.CancellationException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of a lightweight, read-only key-validation request. */
sealed interface VerificationResult {
    data object Valid : VerificationResult

    /** The provider rejected the key; retrying the same key won't help. */
    data class Invalid(val message: String) : VerificationResult

    /** Validity couldn't be determined (offline, timeout, provider outage). Retryable. */
    data class Failed(val message: String) : VerificationResult
}

/**
 * Verifies an API key by calling the provider's cheapest authenticated endpoint
 * (typically `GET /models`) and inspecting the HTTP status. No tokens are spent
 * and no user content is sent. Runs entirely off the main thread; cancellation
 * propagates so an in-flight check can be abandoned when the key changes.
 */
@Singleton
class KeyVerifier @Inject constructor(
    private val api: KeyValidationApi,
    private val networkObserver: NetworkObserver
) {
    suspend fun verify(
        provider: AiProvider,
        key: String,
        customUrl: String? = null
    ): VerificationResult {
        val trimmed = key.trim()
        if (trimmed.isBlank()) return VerificationResult.Invalid("Enter a key first")

        return try {
            val code = when (provider) {
                AiProvider.GEMINI -> api.get(
                    "https://generativelanguage.googleapis.com/v1beta/models",
                    mapOf("x-goog-api-key" to trimmed)
                ).code()

                AiProvider.MISTRAL -> api.get(
                    "https://api.mistral.ai/v1/models",
                    bearer(trimmed)
                ).code()

                AiProvider.OPENROUTER -> api.get(
                    "https://openrouter.ai/api/v1/key",
                    bearer(trimmed)
                ).code()

                AiProvider.HUGGINGFACE -> api.get(
                    "https://huggingface.co/api/whoami-v2",
                    bearer(trimmed)
                ).code()

                AiProvider.NVIDIA -> api.get(
                    "https://integrate.api.nvidia.com/v1/models",
                    bearer(trimmed)
                ).code()

                AiProvider.GROQ -> api.get(
                    "https://api.groq.com/openai/v1/models",
                    bearer(trimmed)
                ).code()

                AiProvider.OPENAI -> api.get(
                    "https://api.openai.com/v1/models",
                    bearer(trimmed)
                ).code()

                AiProvider.CLAUDE -> api.get(
                    "https://api.anthropic.com/v1/models",
                    mapOf(
                        "x-api-key" to trimmed,
                        "anthropic-version" to ClaudeApi.ANTHROPIC_VERSION
                    )
                ).code()

                AiProvider.CUSTOM -> {
                    val url = customUrl?.trim()?.ifBlank { null }
                        ?: return VerificationResult.Invalid("Set the endpoint URL first")
                    val modelsUrl = deriveModelsUrl(url)
                        ?: return VerificationResult.Failed("Can't auto-check this endpoint")
                    api.get(modelsUrl, bearer(trimmed)).code()
                }
            }
            classify(provider, code)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            if (!networkObserver.isCurrentlyOnline()) {
                VerificationResult.Failed("You're offline")
            } else {
                VerificationResult.Failed("Network error — try again")
            }
        } catch (e: Exception) {
            aiDebug { "key verify failed: ${e.message}" }
            VerificationResult.Failed("Couldn't verify — try again")
        }
    }

    private fun bearer(key: String) = mapOf("Authorization" to "Bearer $key")

    private fun classify(provider: AiProvider, code: Int): VerificationResult = when {
        code in 200..299 -> VerificationResult.Valid
        // Authenticated but throttled: the key itself is accepted.
        code == 429 -> VerificationResult.Valid
        code == 401 || code == 403 -> VerificationResult.Invalid("Key was rejected")
        // Google returns 400 with API_KEY_INVALID rather than 401 for a bad key.
        code == 400 && provider == AiProvider.GEMINI -> VerificationResult.Invalid("Key was rejected")
        code in 500..599 -> VerificationResult.Failed("Provider error — try again")
        else -> VerificationResult.Failed("Unexpected response (HTTP $code)")
    }

    /** Turn an OpenAI-style `…/chat/completions` URL into its sibling `…/models`. */
    private fun deriveModelsUrl(chatUrl: String): String? {
        val idx = chatUrl.indexOf("/chat/completions")
        return if (idx >= 0) chatUrl.substring(0, idx) + "/models" else null
    }
}
