package com.skeler.scanely.core.ai

import com.skeler.scanely.core.network.ClaudeApi
import com.skeler.scanely.core.network.KeyValidationApi
import com.skeler.scanely.core.network.NetworkObserver
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
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
 * (typically `GET /models`) and inspecting the HTTP status. No user content is
 * sent, and no tokens are spent except NVIDIA's unavoidable 1-token probe.
 * Runs entirely off the main thread; cancellation
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

                // NVIDIA's GET /v1/models is public and returns 200 for any (or no)
                // key, so it can't validate. The only authenticated endpoint is
                // chat/completions itself; probe it with max_tokens=1 (401/403 on a
                // bad key costs nothing; a valid key spends one token).
                AiProvider.NVIDIA -> api.post(
                    "https://integrate.api.nvidia.com/v1/chat/completions",
                    bearer(trimmed),
                    NVIDIA_PROBE_BODY.toRequestBody("application/json".toMediaType())
                ).code()

                AiProvider.GROQ -> api.get(
                    "https://api.groq.com/openai/v1/models",
                    bearer(trimmed)
                ).code()

                AiProvider.CEREBRAS -> api.get(
                    "https://api.cerebras.ai/v1/models",
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

                AiProvider.CLOUDFLARE -> {
                    // customUrl carries the account id here (no full URL to enter).
                    val accountId = customUrl?.trim()?.ifBlank { null }
                        ?: return VerificationResult.Invalid("Enter your Account ID first")
                    api.get(
                        "https://api.cloudflare.com/client/v4/accounts/$accountId/ai/models/search",
                        bearer(trimmed)
                    ).code()
                }

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
        // Google (API_KEY_INVALID) and Cloudflare (auth code 9106) return 400, not 401, for a bad key.
        code == 400 && (provider == AiProvider.GEMINI || provider == AiProvider.CLOUDFLARE) ->
            VerificationResult.Invalid("Key was rejected")
        code in 500..599 -> VerificationResult.Failed("Provider error — try again")
        else -> VerificationResult.Failed("Unexpected response (HTTP $code)")
    }

    /** Turn an OpenAI-style `…/chat/completions` URL into its sibling `…/models`. */
    private fun deriveModelsUrl(chatUrl: String): String? {
        val idx = chatUrl.indexOf("/chat/completions")
        return if (idx >= 0) chatUrl.substring(0, idx) + "/models" else null
    }

    private companion object {
        const val NVIDIA_PROBE_BODY =
            """{"model":"google/gemma-4-31b-it","messages":[{"role":"user","content":"hi"}],"max_tokens":1}"""
    }
}
