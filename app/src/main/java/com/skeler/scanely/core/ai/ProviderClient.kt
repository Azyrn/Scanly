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
import com.skeler.scanely.core.network.codeInt
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

internal class TransientAiException(message: String, cause: Throwable? = null) : Exception(message, cause)

internal class FatalAiException(val result: AiResult.Error) : Exception(result.message)

@Singleton
internal class ProviderClient @Inject constructor(
    private val openAiApi: OpenAiCompatApi,
    private val claudeApi: ClaudeApi,
    private val geminiApi: GeminiApi,
    private val mistralApi: MistralApi
) {
    private val streamJson = Json { ignoreUnknownKeys = true }

    fun supportsStreaming(config: ProviderConfig): Boolean =
        config.kind != ProviderKind.MISTRAL_OCR

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

        try {
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
        } catch (e: FatalAiException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keep partial text after break (e.g. Cerebras closes post-token); don't re-bill.
            if (accumulated.isEmpty()) throw e
            aiDebug { "stream broke after ${accumulated.length} chars, keeping partial: ${e.message}" }
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

    private fun stripReasoning(text: String): String =
        text.replace(THINK_BLOCK, "").trim()

    private inline fun <reified T> decodeChunk(payload: String): T = try {
        streamJson.decodeFromString<T>(payload)
    } catch (e: SerializationException) {
        throw TransientAiException("Malformed response chunk", e)
    }

    private fun parseStreamPiece(kind: ProviderKind, payload: String): String? = when (kind) {
        ProviderKind.OPENAI_COMPAT -> {
            val chunk = decodeChunk<ChatStreamChunk>(payload)
            chunk.error?.let { err ->
                if (err.codeInt == 429) throw TransientAiException(err.message ?: "rate limited")
                throw FatalAiException(AiResult.Error(err.message ?: "Provider error"))
            }
            chunk.choices.firstOrNull()?.delta?.content
        }
        ProviderKind.ANTHROPIC -> {
            val event = decodeChunk<ClaudeStreamEvent>(payload)
            event.error?.let { err ->
                if (err.type == "overloaded_error") {
                    throw TransientAiException(err.message ?: "overloaded")
                }
                throw FatalAiException(AiResult.Error(err.message ?: "Claude error"))
            }
            if (event.type == "content_block_delta" && event.delta?.type == "text_delta") {
                event.delta.text
            } else {
                null
            }
        }
        ProviderKind.GEMINI -> {
            val chunk = decodeChunk<GeminiResponse>(payload)
            chunk.error?.let { err ->
                if (err.code == 429) throw TransientAiException(err.message ?: "rate limited")
                throw FatalAiException(AiResult.Error(err.message ?: "Gemini error"))
            }
            chunk.candidates.firstOrNull()?.content?.parts
                ?.filter { it.thought != true }
                ?.mapNotNull { it.text }
                ?.joinToString("")
                ?.ifEmpty { null }
        }
        ProviderKind.MISTRAL_OCR -> null
    }

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

    suspend fun once(
        config: ProviderConfig,
        systemInstruction: String?,
        prompt: String,
        images: List<String>,
        pdfBase64: String? = null
    ): String = when (config.kind) {
        ProviderKind.OPENAI_COMPAT -> {
            val url = config.url ?: throw FatalAiException(AiResult.Error("Missing endpoint URL"))
            val response = openAiApi.chatCompletion(
                url,
                "Bearer ${config.apiKey}",
                AiRequestFactory.openAi(config, systemInstruction, prompt, images, stream = false)
            )
            response.error?.let { err ->
                if (err.codeInt == 429) throw TransientAiException(err.message ?: "rate limited")
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
                config.model,
                config.apiKey,
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
        ProviderKind.MISTRAL_OCR -> mistralOcr(config, systemInstruction, prompt, images, pdfBase64)
    }

    private suspend fun mistralOcr(
        config: ProviderConfig,
        systemInstruction: String?,
        prompt: String,
        images: List<String>,
        pdfBase64: String?
    ): String {
        // The OCR endpoint takes one document per request, but reads every page of a PDF from it —
        // so send the source PDF whole rather than one request per rendered page.
        val document = when {
            pdfBase64 != null -> MistralDocument(
                type = "document_url",
                document_url = "data:application/pdf;base64,$pdfBase64"
            )
            images.isNotEmpty() -> MistralDocument(
                type = "image_url",
                image_url = "data:image/jpeg;base64,${images.first()}"
            )
            else -> return once(
                ProviderConfig.mistralChat(config.apiKey),
                systemInstruction,
                prompt,
                images
            )
        }

        val auth = "Bearer ${config.apiKey}"
        var model = config.model
        val response = try {
            mistralApi.ocr(auth, MistralOcrRequest(model, document))
        } catch (e: HttpException) {
            if (model == ProviderConfig.MISTRAL_OCR_DEFAULT &&
                e.code() in 400..499 && e.code() != 429
            ) {
                aiDebug { "Mistral $model rejected (HTTP ${e.code()}), trying $MISTRAL_OCR_FALLBACK_MODEL" }
                model = MISTRAL_OCR_FALLBACK_MODEL
                mistralApi.ocr(auth, MistralOcrRequest(model, document))
            } else {
                throw e
            }
        }
        return response.pages
            .sortedBy { it.index }
            .joinToString("\n\n") { it.markdown.trim() }
            .trim()
    }

    companion object {
        private const val MISTRAL_OCR_FALLBACK_MODEL = "mistral-ocr-3"

        private const val DELTA_THROTTLE_MS = 100L

        private val THINK_BLOCK = Regex("(?s)<think>.*?(?:</think>\\s*|$)")
    }
}
