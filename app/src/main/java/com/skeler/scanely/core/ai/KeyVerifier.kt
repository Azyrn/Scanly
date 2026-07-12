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

sealed interface VerificationResult {
    data object Valid : VerificationResult

    data class Invalid(val message: String) : VerificationResult

    data class Failed(val message: String) : VerificationResult
}

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
        code == 429 -> VerificationResult.Valid
        code == 401 || code == 403 -> VerificationResult.Invalid("Key was rejected")
        code == 400 && (provider == AiProvider.GEMINI || provider == AiProvider.CLOUDFLARE) ->
            VerificationResult.Invalid("Key was rejected")
        code in 500..599 -> VerificationResult.Failed("Provider error — try again")
        else -> VerificationResult.Failed("Unexpected response (HTTP $code)")
    }

    private fun deriveModelsUrl(chatUrl: String): String? {
        val idx = chatUrl.indexOf("/chat/completions")
        return if (idx >= 0) chatUrl.substring(0, idx) + "/models" else null
    }

    private companion object {
        const val NVIDIA_PROBE_BODY =
            """{"model":"google/gemma-4-31b-it","messages":[{"role":"user","content":"hi"}],"max_tokens":1}"""
    }
}
