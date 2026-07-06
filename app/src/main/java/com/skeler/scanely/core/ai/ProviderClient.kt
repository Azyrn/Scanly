package com.skeler.scanely.core.ai

import android.os.SystemClock
import com.skeler.scanely.core.network.ChatStreamChunk
import com.skeler.scanely.core.network.ClaudeApi
import com.skeler.scanely.core.network.ClaudeStreamEvent
import com.skeler.scanely.core.network.GeminiApi
import com.skeler.scanely.core.network.GeminiResponse
import com.skeler.scanely.core.network.MistralApi
import com.skeler.scanely.core.network.MistralDocument
import com.skeler.scanely.core.network.MistralOcrRequest
import com.skeler.scanely.core.network.OpenAiCompatApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/** Transient provider-side failure (429 / 5xx / overload) worth retrying. */
internal class TransientAiException(message: String) : Exception(message)

/** Definitive provider error that retrying will not fix. */
internal class FatalAiException(val result: AiResult.Error) : Exception(result.message)

/**
 * Executes a single request against one resolved [ProviderConfig] and returns
 * the extracted text. Parsing errors surface as [TransientAiException] (retry)
 * or [FatalAiException] (give up); transport failures propagate as-is.
 */
@Singleton
internal class ProviderClient @Inject constructor(
    private val openAiApi: OpenAiCompatApi,
    private val claudeApi: ClaudeApi,
    private val geminiApi: GeminiApi,
    private val mistralApi: MistralApi
) {
    /** Lenient decoder for SSE payload lines. */
    private val streamJson = Json { ignoreUnknownKeys = true }

    /** True when this provider can be streamed via SSE. */
    fun supportsStreaming(config: ProviderConfig): Boolean =
        config.kind != ProviderKind.MISTRAL_OCR

    /**
     * Send one streaming request and return the full accumulated text,
     * emitting throttled [AiEvent.Delta]s as tokens arrive. [streamedAnything]
     * is flipped once the first token lands so callers can tell an empty
     * (no-SSE) stream from a slow one.
     */
    suspend fun stream(
        config: ProviderConfig,
        systemInstruction: String?,
        prompt: String,
        images: List<String>,
        streamedAnything: BooleanArray,
        emit: suspend (AiEvent) -> Unit
    ): String {
        val body: ResponseBody = when (config.kind) {
            ProviderKind.OPENAI_COMPAT -> openAiApi.chatCompletionStream(
                url = config.url ?: throw FatalAiException(AiResult.Error("Missing endpoint URL")),
                authorization = "Bearer ${config.apiKey}",
                request = AiRequestFactory.openAi(config, systemInstruction, prompt, images, stream = true)
            )
            ProviderKind.ANTHROPIC -> claudeApi.messagesStream(
                apiKey = config.apiKey,
                request = AiRequestFactory.claude(config, systemInstruction, prompt, images, stream = true)
            )
            ProviderKind.GEMINI -> geminiApi.streamGenerateContent(
                model = config.model,
                apiKey = config.apiKey,
                request = AiRequestFactory.gemini(systemInstruction, prompt, images)
            )
            ProviderKind.MISTRAL_OCR -> error("Mistral OCR does not stream")
        }
        emit(AiEvent.Stage(AiStage.PROCESSING))

        val accumulated = StringBuilder()
        var firstTokenAt = 0L
        var lastEmit = 0L
        val requestStart = SystemClock.elapsedRealtime()

        consumeSse(body) { payload ->
            val piece = parseStreamPiece(config.kind, payload)
            if (!piece.isNullOrEmpty()) {
                if (accumulated.isEmpty()) {
                    firstTokenAt = SystemClock.elapsedRealtime()
                    emit(AiEvent.Stage(AiStage.GENERATING))
                }
                streamedAnything[0] = true
                accumulated.append(piece)
                val now = SystemClock.elapsedRealtime()
                if (now - lastEmit >= DELTA_THROTTLE_MS) {
                    lastEmit = now
                    emit(AiEvent.Delta(accumulated.toString()))
                }
            }
        }

        val result = stripReasoning(accumulated.toString())
        if (result.isNotEmpty()) {
            emit(AiEvent.Delta(result))
            aiDebug {
                "first token ${firstTokenAt - requestStart} ms, " +
                    "full response ${SystemClock.elapsedRealtime() - requestStart} ms"
            }
        }
        return result
    }

    // Some reasoning models emit their chain-of-thought inline as <think>…</think>
    // (Qwen, DeepSeek-R1, …). Disabling it at the API is provider-specific and
    // rejected by non-reasoning models, so strip it from the output for every
    // provider as a universal safety net; Groq additionally turns it off upstream.
    private fun stripReasoning(text: String): String =
        text.replace(THINK_BLOCK, "").trim()

    /** Decode one SSE payload line into a text delta, or null if it carries none. */
    private fun parseStreamPiece(kind: ProviderKind, payload: String): String? = when (kind) {
        ProviderKind.OPENAI_COMPAT -> {
            val chunk = streamJson.decodeFromString<ChatStreamChunk>(payload)
            chunk.error?.let { err ->
                if (err.code == 429) throw TransientAiException(err.message ?: "rate limited")
                throw FatalAiException(AiResult.Error(err.message ?: "Provider error"))
            }
            chunk.choices.firstOrNull()?.delta?.content
        }
        ProviderKind.ANTHROPIC -> {
            val event = streamJson.decodeFromString<ClaudeStreamEvent>(payload)
            event.error?.let { err ->
                if (err.type == "overloaded_error") {
                    throw TransientAiException(err.message ?: "overloaded")
                }
                throw FatalAiException(AiResult.Error(err.message ?: "Claude error"))
            }
            if (event.type == "content_block_delta" && event.delta?.type == "text_delta") {
                event.delta.text
            } else null
        }
        ProviderKind.GEMINI -> {
            val chunk = streamJson.decodeFromString<GeminiResponse>(payload)
            chunk.error?.let { err ->
                if (err.code == 429) throw TransientAiException(err.message ?: "rate limited")
                throw FatalAiException(AiResult.Error(err.message ?: "Gemini error"))
            }
            // Gemma "thinks" by default; keep only non-thought parts.
            chunk.candidates.firstOrNull()?.content?.parts
                ?.filter { it.thought != true }
                ?.mapNotNull { it.text }
                ?.joinToString("")
                ?.ifEmpty { null }
        }
        ProviderKind.MISTRAL_OCR -> null
    }

    /**
     * Read `data:` lines off an SSE body. Checks for cancellation between lines
     * so a user cancel is honored at token granularity; a fully stalled
     * connection is broken by the OkHttp read timeout.
     */
    private suspend fun consumeSse(body: ResponseBody, onData: suspend (String) -> Unit) {
        body.use {
            val source = it.source()
            while (true) {
                currentCoroutineContext().ensureActive()
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.substring(5).trim()
                if (payload == "[DONE]") break
                if (payload.isNotEmpty()) onData(payload)
            }
        }
    }

    /** Send one plain (non-streaming) request and return the response text. */
    suspend fun once(
        config: ProviderConfig,
        systemInstruction: String?,
        prompt: String,
        images: List<String>
    ): String = when (config.kind) {
        ProviderKind.OPENAI_COMPAT -> {
            val url = config.url ?: throw FatalAiException(AiResult.Error("Missing endpoint URL"))
            val response = openAiApi.chatCompletion(
                url, "Bearer ${config.apiKey}",
                AiRequestFactory.openAi(config, systemInstruction, prompt, images, stream = false)
            )
            response.error?.let { err ->
                if (err.code == 429) throw TransientAiException(err.message ?: "rate limited")
                throw FatalAiException(AiResult.Error(err.message ?: "Provider error"))
            }
            stripReasoning(response.choices.firstOrNull()?.message?.content.orEmpty())
        }
        ProviderKind.ANTHROPIC -> {
            val response = claudeApi.messages(
                apiKey = config.apiKey,
                request = AiRequestFactory.claude(config, systemInstruction, prompt, images, stream = false)
            )
            response.error?.let { err ->
                throw FatalAiException(AiResult.Error(err.message ?: "Claude error"))
            }
            response.content
                .filter { it.type == "text" }
                .mapNotNull { it.text }
                .joinToString("")
                .trim()
        }
        ProviderKind.GEMINI -> {
            val response = geminiApi.generateContent(
                config.model, config.apiKey,
                AiRequestFactory.gemini(systemInstruction, prompt, images)
            )
            response.error?.let { err ->
                if (err.code == 429) throw TransientAiException(err.message ?: "rate limited")
                throw FatalAiException(AiResult.Error(err.message ?: "Gemini error"))
            }
            response.candidates.firstOrNull()?.content?.parts
                ?.filter { it.thought != true }
                ?.mapNotNull { it.text }
                ?.joinToString("")
                ?.trim()
                .orEmpty()
        }
        ProviderKind.MISTRAL_OCR -> mistralOcr(config, systemInstruction, prompt, images)
    }

    /**
     * Mistral OCR: one request per image, pages joined as markdown. Text-only
     * prompts (txt files, translation) go to Mistral's chat API instead.
     * If the app's *default* OCR model is rejected, retries once with the older
     * model — a user-selected model is used exactly and never substituted.
     */
    private suspend fun mistralOcr(
        config: ProviderConfig,
        systemInstruction: String?,
        prompt: String,
        images: List<String>
    ): String {
        if (images.isEmpty()) {
            return once(ProviderConfig.mistralChat(config.apiKey), systemInstruction, prompt, images)
        }

        val auth = "Bearer ${config.apiKey}"
        val results = images.map { base64 ->
            val document = MistralDocument(
                type = "image_url",
                image_url = "data:image/jpeg;base64,$base64"
            )
            val response = try {
                mistralApi.ocr(auth, MistralOcrRequest(config.model, document))
            } catch (e: HttpException) {
                // Only the app default is eligible for the older-model retry; a
                // user-chosen model is never silently swapped.
                if (config.model == ProviderConfig.MISTRAL_OCR_DEFAULT &&
                    e.code() in 400..499 && e.code() != 429
                ) {
                    aiDebug { "Mistral ${config.model} rejected (HTTP ${e.code()}), trying $MISTRAL_OCR_FALLBACK_MODEL" }
                    mistralApi.ocr(auth, MistralOcrRequest(MISTRAL_OCR_FALLBACK_MODEL, document))
                } else throw e
            }
            response.pages
                .sortedBy { it.index }
                .joinToString("\n\n") { it.markdown }
                .trim()
        }
        return results.filter { it.isNotEmpty() }.joinToString("\n\n").trim()
    }

    companion object {
        /** Older Mistral OCR model tried when the primary one errors. */
        private const val MISTRAL_OCR_FALLBACK_MODEL = "mistral-ocr-3"

        /** Minimum interval between streamed [AiEvent.Delta] emissions. */
        private const val DELTA_THROTTLE_MS = 100L

        /** Inline reasoning block emitted by some models; stripped from output. */
        private val THINK_BLOCK = Regex("(?s)<think>.*?</think>\\s*")
    }
}
