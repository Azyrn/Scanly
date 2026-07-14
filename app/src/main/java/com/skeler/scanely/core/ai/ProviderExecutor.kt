package com.skeler.scanely.core.ai

import android.os.SystemClock
import com.skeler.scanely.core.network.NetworkObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

internal sealed interface ProviderOutcome {
    data class Success(val text: String) : ProviderOutcome

    data class Exhausted(val networkProblem: Boolean = false) : ProviderOutcome
    data class Fatal(val error: AiResult.Error) : ProviderOutcome
}

@Singleton
internal class ProviderExecutor @Inject constructor(
    private val client: ProviderClient,
    private val networkObserver: NetworkObserver
) {
    suspend fun run(
        name: String,
        config: ProviderConfig,
        systemInstruction: String?,
        prompt: String,
        images: List<String>,
        allowStreaming: Boolean,
        emit: suspend (AiEvent) -> Unit
    ): ProviderOutcome {
        var useStreaming = allowStreaming && client.supportsStreaming(config)
        var sawNetworkIo = false
        var lastFailureWasNetwork = false
        val timeoutMs = attemptTimeout(images.size)

        repeat(MAX_ATTEMPTS) { attempt ->
            if (attempt > 0) {
                val wait = backoffDelay(attempt)
                val seconds = (wait + 500) / 1000
                val suffix = "retrying in $seconds s (attempt ${attempt + 1} of $MAX_ATTEMPTS)"
                emit(
                    AiEvent.Stage(
                        AiStage.PROCESSING,
                        if (lastFailureWasNetwork) {
                            "Connection trouble — $suffix"
                        } else {
                            "$name is busy — $suffix"
                        }
                    )
                )
                delay(wait)
            }

            val streamedAnything = booleanArrayOf(false)
            try {
                emit(AiEvent.Stage(AiStage.UPLOADING))
                val start = SystemClock.elapsedRealtime()
                val text = withTimeout(timeoutMs) {
                    if (useStreaming) {
                        client.stream(config, systemInstruction, prompt, images, streamedAnything, emit)
                    } else {
                        emit(AiEvent.Stage(AiStage.PROCESSING))
                        client.once(config, systemInstruction, prompt, images)
                    }
                }
                aiDebug { "$name attempt ${attempt + 1} ok in ${SystemClock.elapsedRealtime() - start} ms" }
                if (text.isNotBlank()) return ProviderOutcome.Success(text)
                if (useStreaming && !streamedAnything[0]) {
                    aiDebug { "$name returned 200 with no SSE data; degrading to plain requests" }
                    useStreaming = false
                    lastFailureWasNetwork = false
                } else {
                    return ProviderOutcome.Fatal(AiResult.Error("No response generated"))
                }
            } catch (_: TimeoutCancellationException) {
                aiDebug { "$name attempt ${attempt + 1} timed out after $timeoutMs ms" }
                lastFailureWasNetwork = false
                if (streamedAnything[0]) return ProviderOutcome.Exhausted()
            } catch (e: CancellationException) {
                throw e
            } catch (e: FatalAiException) {
                return ProviderOutcome.Fatal(e.result)
            } catch (e: TransientAiException) {
                aiDebug { "$name attempt ${attempt + 1} transient: ${e.message}" }
                lastFailureWasNetwork = false
            } catch (e: HttpException) {
                when {
                    e.code() == 429 || e.code() in 500..599 -> {
                        aiDebug { "$name attempt ${attempt + 1} HTTP ${e.code()}" }
                        lastFailureWasNetwork = false
                    }
                    else -> return ProviderOutcome.Fatal(AiResult.Error(httpErrorMessage(e.code())))
                }
            } catch (e: IOException) {
                aiDebug { "$name attempt ${attempt + 1} IO: ${e.message}" }
                if (e.message?.contains("CLEARTEXT", ignoreCase = true) == true) {
                    return ProviderOutcome.Fatal(AiResult.Error(CLEARTEXT_MESSAGE))
                }
                sawNetworkIo = true
                lastFailureWasNetwork = true
                if (!networkObserver.isCurrentlyOnline()) {
                    return ProviderOutcome.Exhausted(networkProblem = true)
                }
                if (useStreaming && !streamedAnything[0]) useStreaming = false
            } catch (e: SerializationException) {
                aiDebug { "$name attempt ${attempt + 1} unparsable body: ${e.message}" }
                return ProviderOutcome.Fatal(AiResult.Error(BAD_RESPONSE_MESSAGE))
            } catch (e: Exception) {
                aiDebug { "$name attempt ${attempt + 1} ${e::class.simpleName}: ${e.message}" }
                return ProviderOutcome.Fatal(AiResult.Error(UNEXPECTED_MESSAGE))
            }
        }
        return ProviderOutcome.Exhausted(networkProblem = sawNetworkIo)
    }

    private fun backoffDelay(attempt: Int): Long =
        BACKOFF_BASE_MS * (1L shl (attempt - 1)) + Random.nextLong(BACKOFF_JITTER_MS)

    private fun attemptTimeout(imageCount: Int): Long =
        (
            ATTEMPT_TIMEOUT_BASE_MS +
                ATTEMPT_TIMEOUT_PER_EXTRA_IMAGE_MS * (imageCount - 1).coerceAtLeast(0)
            )
            .coerceAtMost(ATTEMPT_TIMEOUT_MAX_MS)

    private fun httpErrorMessage(code: Int): String = when (code) {
        429 ->
            "Provider is rate-limited right now. Try again shortly, or add " +
                "your own API key in Settings → AI Providers."
        402 ->
            "Your API key has hit its usage or billing limit. Check your plan " +
                "with the provider, then try again."
        401, 403 -> "Invalid or unauthorized API key. Check it in Settings → AI Providers."
        HTTP_BAD_REQUEST, HTTP_NOT_FOUND, HTTP_UNPROCESSABLE ->
            "The provider rejected this request (HTTP $code). The model ID is " +
                "usually the cause — check it in Settings → AI Providers, or clear " +
                "the field to use the default model."
        else -> "Request failed (HTTP $code)"
    }

    companion object {
        private const val MAX_ATTEMPTS = 3

        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_UNPROCESSABLE = 422

        private const val BAD_RESPONSE_MESSAGE =
            "The provider sent back something this app can't read. Check the " +
                "endpoint URL and model ID in Settings → AI Providers."
        private const val UNEXPECTED_MESSAGE =
            "Something went wrong talking to the provider. Check your settings " +
                "in Settings → AI Providers and try again."
        private const val CLEARTEXT_MESSAGE =
            "This endpoint uses plain http://, which Android blocks. Use an " +
                "https:// endpoint in Settings → AI Providers."
        private const val BACKOFF_BASE_MS = 1200L
        private const val BACKOFF_JITTER_MS = 600L

        private const val ATTEMPT_TIMEOUT_BASE_MS = 90_000L
        private const val ATTEMPT_TIMEOUT_PER_EXTRA_IMAGE_MS = 20_000L
        private const val ATTEMPT_TIMEOUT_MAX_MS = 240_000L
    }
}
