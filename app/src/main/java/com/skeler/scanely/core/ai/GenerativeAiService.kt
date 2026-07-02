package com.skeler.scanely.core.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import com.skeler.scanely.BuildConfig
import com.skeler.scanely.core.network.ChatMessage
import com.skeler.scanely.core.network.ChatRequest
import com.skeler.scanely.core.network.ChatStreamChunk
import com.skeler.scanely.core.network.ClaudeApi
import com.skeler.scanely.core.network.ClaudeContent
import com.skeler.scanely.core.network.ClaudeImageSource
import com.skeler.scanely.core.network.ClaudeMessage
import com.skeler.scanely.core.network.ClaudeRequest
import com.skeler.scanely.core.network.ClaudeStreamEvent
import com.skeler.scanely.core.network.ContentPart
import com.skeler.scanely.core.network.GeminiApi
import com.skeler.scanely.core.network.GeminiContent
import com.skeler.scanely.core.network.GeminiInlineData
import com.skeler.scanely.core.network.GeminiPart
import com.skeler.scanely.core.network.GeminiRequest
import com.skeler.scanely.core.network.GeminiResponse
import com.skeler.scanely.core.network.ImageUrl
import com.skeler.scanely.core.network.MistralApi
import com.skeler.scanely.core.network.MistralDocument
import com.skeler.scanely.core.network.MistralOcrRequest
import com.skeler.scanely.core.network.NetworkObserver
import com.skeler.scanely.core.network.OpenAiCompatApi
import com.skeler.scanely.settings.data.SettingsKeys
import com.skeler.scanely.settings.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import retrofit2.HttpException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * AI processing mode for image/document analysis.
 */
enum class AiMode {
    EXTRACT_TEXT,
    EXTRACT_PDF_TEXT,
    ICON_TRANSLATE
}

/**
 * Sealed class representing AI operation results.
 */
sealed class AiResult {
    data class Success(val text: String) : AiResult()
    data class RateLimited(val remainingMs: Long) : AiResult()
    data class Error(val message: String) : AiResult()
}

/** User-visible phases of one extraction, in order. */
enum class AiStage {
    PREPARING,
    UPLOADING,
    PROCESSING,
    GENERATING,
    COMPLETE
}

/**
 * Progress events emitted while an extraction runs.
 *
 * [Stage] marks a phase change (with an optional human-readable note, e.g.
 * "OpenRouter is busy — retrying in 3 s"); [Delta] carries the accumulated
 * streamed text so far; [Finished] is always the terminal event.
 */
sealed class AiEvent {
    data class Stage(val stage: AiStage, val message: String? = null) : AiEvent()
    data class Delta(val textSoFar: String) : AiEvent()
    data class Finished(val result: AiResult) : AiEvent()
}

/**
 * Service handling AI text-extraction / translation across multiple providers
 * (Gemini, OpenRouter, OpenAI, Claude, and custom OpenAI-compatible endpoints).
 *
 * Reliability model:
 * - Responses are streamed (SSE) so text appears as it is generated; if a
 *   provider's stream breaks before producing anything, the next attempt for
 *   that provider degrades to a plain request.
 * - HTTP 429 / 5xx retries use exponential backoff with jitter.
 * - When one provider exhausts its retries, the request falls back to the
 *   next provider that has a usable key, in a fixed preference order.
 * - Every attempt runs under a timeout scaled to the payload size, and all
 *   work runs on [Dispatchers.IO].
 *
 * Supported inputs:
 * - Images: image/png, image/jpeg, image/webp (sent directly)
 * - PDF: application/pdf (pages rendered to images, first [MAX_PDF_PAGES] pages)
 * - Text: text/plain (content inlined into the prompt)
 */
@Singleton
class GenerativeAiService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val openAiApi: OpenAiCompatApi,
    private val claudeApi: ClaudeApi,
    private val geminiApi: GeminiApi,
    private val mistralApi: MistralApi,
    private val settingsRepository: SettingsRepository,
    private val networkObserver: NetworkObserver
) {
    companion object {
        private const val TAG = "GenerativeAi"

        /** OpenRouter endpoint + model (vision-capable, free tier). */
        private const val OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"
        private const val OPENROUTER_MODEL = "google/gemma-4-26b-a4b-it:free"

        /** OpenAI endpoint + model (vision-capable). */
        private const val OPENAI_URL = "https://api.openai.com/v1/chat/completions"
        private const val OPENAI_MODEL = "gpt-4o-mini"

        /** Anthropic (Claude) model id (vision-capable, low cost). */
        private const val CLAUDE_MODEL = "claude-haiku-4-5-20251001"

        /** Google Generative Language model id (vision-capable Gemma 4). */
        private const val GEMINI_MODEL = "gemma-4-31b-it"

        /** Mistral OCR models: OCR-4 first, OCR-3 if it errors. Chat model for translation. */
        private const val MISTRAL_OCR_MODEL = "mistral-ocr-latest"
        private const val MISTRAL_OCR_FALLBACK_MODEL = "mistral-ocr-3"
        private const val MISTRAL_CHAT_MODEL = "mistral-small-latest"

        private const val PROMPT_EXTRACT = "Extract all visible text from this image. Return only the extracted text, nothing else."
        private const val PROMPT_PDF_EXTRACT = """Extract ALL text content from this document.
Rules:
1. Extract every single word, number, and character
2. Preserve the original formatting and structure
3. Include all tables, headers, footers, and captions
4. Do not summarize or skip any content
5. Return ONLY the extracted text, no descriptions or commentary"""
        private const val PROMPT_ICON_TRANSLATE = "Extract all visible text from this image and translate it to English."

        private const val SYSTEM_INSTRUCTION = """You are a precise document text extraction assistant.
Your task is to extract text exactly as it appears in images and documents.

Rules:
1. Extract ALL visible text with 100% accuracy
2. Preserve original formatting, line breaks, and structure
3. Do NOT summarize, interpret, or modify any content
4. Do NOT add any commentary or descriptions
5. For tables, maintain column alignment using spaces
6. For multi-language documents, preserve all languages as-is
7. If text is unclear, mark it with [unclear] but attempt best guess"""

        private const val PDF_MIME_TYPE = "application/pdf"
        private const val TEXT_MIME_TYPE = "text/plain"

        /** Cap pages/dimensions to keep request payloads (and token usage) sane. */
        private const val MAX_PDF_PAGES = 5
        private const val MAX_IMAGE_DIMENSION = 1536
        private const val JPEG_QUALITY = 85

        /** Retry policy: exponential backoff with jitter on 429/5xx. */
        private const val MAX_ATTEMPTS = 3
        private const val BACKOFF_BASE_MS = 1200L
        private const val BACKOFF_JITTER_MS = 600L

        /** Attempt timeout scales with payload; streamed deltas keep it honest. */
        private const val ATTEMPT_TIMEOUT_BASE_MS = 90_000L
        private const val ATTEMPT_TIMEOUT_PER_EXTRA_IMAGE_MS = 20_000L
        private const val ATTEMPT_TIMEOUT_MAX_MS = 240_000L

        /** Minimum interval between streamed [AiEvent.Delta] emissions. */
        private const val DELTA_THROTTLE_MS = 100L

        /** Shown instead of provider errors when the device has no network. */
        private const val OFFLINE_MESSAGE =
            "No internet connection. Check your network and try again."

        /** Fallback order after the user's chosen provider. */
        private val FALLBACK_ORDER = listOf(
            AiProvider.GEMINI,
            AiProvider.MISTRAL,
            AiProvider.OPENROUTER,
            AiProvider.OPENAI,
            AiProvider.CLAUDE,
            AiProvider.CUSTOM
        )
    }

    /** Lenient decoder for SSE payload lines. */
    private val streamJson = Json { ignoreUnknownKeys = true }

    // ------------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------------

    /**
     * Process an image or document, emitting stage changes, streamed text
     * deltas, and a terminal [AiEvent.Finished]. Cancelling the collector
     * cancels the work.
     */
    fun processImageEvents(uri: Uri, mode: AiMode, provider: AiProvider): Flow<AiEvent> =
        channelFlow {
            val totalStart = SystemClock.elapsedRealtime()
            send(AiEvent.Stage(AiStage.PREPARING))

            if (!networkObserver.isCurrentlyOnline()) {
                send(AiEvent.Finished(AiResult.Error(OFFLINE_MESSAGE)))
                return@channelFlow
            }

            val payload = try {
                timed("prepare") { preparePayload(uri, mode) }
            } catch (e: PayloadException) {
                send(AiEvent.Finished(AiResult.Error(e.message ?: "Failed to read file")))
                return@channelFlow
            }

            val chain = providerChain(provider)
            if (chain.isEmpty()) {
                send(AiEvent.Finished(missingConfigError(provider)))
                return@channelFlow
            }

            val exhausted = mutableListOf<Pair<String, Boolean>>()
            chain.forEachIndexed { index, (prov, config) ->
                if (index > 0) {
                    val (prevName, prevNetwork) = exhausted.last()
                    send(
                        AiEvent.Stage(
                            AiStage.UPLOADING,
                            if (prevNetwork) {
                                "Connection trouble with $prevName — trying ${prov.displayName}…"
                            } else {
                                "$prevName is busy — trying ${prov.displayName}…"
                            }
                        )
                    )
                }
                val attemptStart = SystemClock.elapsedRealtime()
                val outcome = runProvider(
                    name = prov.displayName,
                    config = config,
                    systemInstruction = SYSTEM_INSTRUCTION,
                    prompt = payload.prompt,
                    imageJpegs = payload.imageJpegs,
                    allowStreaming = true,
                    emit = { send(it) }
                )
                logDebug {
                    "${prov.displayName} finished in " +
                        "${SystemClock.elapsedRealtime() - attemptStart} ms → " +
                        outcome.javaClass.simpleName
                }
                when (outcome) {
                    is ProviderOutcome.Success -> {
                        send(AiEvent.Stage(AiStage.COMPLETE))
                        logDebug { "total ${SystemClock.elapsedRealtime() - totalStart} ms" }
                        send(AiEvent.Finished(AiResult.Success(outcome.text)))
                        return@channelFlow
                    }
                    is ProviderOutcome.Fatal -> {
                        send(AiEvent.Finished(outcome.error))
                        return@channelFlow
                    }
                    is ProviderOutcome.Exhausted -> {
                        if (outcome.networkProblem && !networkObserver.isCurrentlyOnline()) {
                            send(AiEvent.Finished(AiResult.Error(OFFLINE_MESSAGE)))
                            return@channelFlow
                        }
                        exhausted.add(prov.displayName to outcome.networkProblem)
                    }
                }
            }

            val names = exhausted.joinToString(" and ") { it.first }
            val message = if (exhausted.all { it.second }) {
                "Couldn't reach $names — your connection looks unstable. " +
                    "Check it and try again."
            } else {
                "$names ${if (exhausted.size == 1) "is" else "are"} " +
                    "rate-limited right now. Free tiers get busy at peak " +
                    "times — wait a minute and rescan, or add your own API " +
                    "key in Settings → AI Providers for faster, more " +
                    "reliable scans."
            }
            send(AiEvent.Finished(AiResult.Error(message)))
        }.flowOn(Dispatchers.IO)

    /**
     * One-shot variant of [processImageEvents]: runs the same pipeline and
     * returns only the terminal result.
     */
    suspend fun processImage(uri: Uri, mode: AiMode, provider: AiProvider): AiResult {
        var final: AiResult = AiResult.Error("No response generated")
        processImageEvents(uri, mode, provider).collect { event ->
            if (event is AiEvent.Finished) final = event.result
        }
        return final
    }

    /**
     * Translate text to a target language using [provider] (non-streaming;
     * translations are short and arrive quickly).
     */
    suspend fun translateText(
        text: String,
        targetLanguage: String,
        provider: AiProvider
    ): AiResult = withContext(Dispatchers.IO) {
        if (!networkObserver.isCurrentlyOnline()) {
            return@withContext AiResult.Error(OFFLINE_MESSAGE)
        }
        val resolved = resolveConfig(provider) ?: return@withContext missingConfigError(provider)
        // OCR endpoint can't translate; use Mistral's chat API for that.
        val config = if (resolved.kind == ProviderKind.MISTRAL_OCR) {
            ProviderConfig(ProviderKind.OPENAI_COMPAT, MISTRAL_CHAT_MODEL, resolved.apiKey, MistralApi.CHAT_URL)
        } else resolved
        val prompt = "Translate the following text to $targetLanguage. " +
            "Return only the translated text, nothing else:\n\n$text"
        val outcome = runProvider(
            name = provider.displayName,
            config = config,
            systemInstruction = null,
            prompt = prompt,
            imageJpegs = emptyList(),
            allowStreaming = false,
            emit = {}
        )
        when (outcome) {
            is ProviderOutcome.Success -> AiResult.Success(outcome.text)
            is ProviderOutcome.Fatal -> outcome.error
            is ProviderOutcome.Exhausted -> AiResult.Error(
                if (outcome.networkProblem && !networkObserver.isCurrentlyOnline()) {
                    OFFLINE_MESSAGE
                } else {
                    "${provider.displayName} is rate-limited right now. Try again shortly."
                }
            )
        }
    }

    // ------------------------------------------------------------------------
    // Payload preparation
    // ------------------------------------------------------------------------

    private class PayloadException(message: String) : Exception(message)

    private data class Payload(val prompt: String, val imageJpegs: List<ByteArray>)

    private fun preparePayload(uri: Uri, mode: AiMode): Payload {
        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val prompt = when (mode) {
            AiMode.EXTRACT_TEXT -> PROMPT_EXTRACT
            AiMode.EXTRACT_PDF_TEXT -> PROMPT_PDF_EXTRACT
            AiMode.ICON_TRANSLATE -> PROMPT_ICON_TRANSLATE
        }

        return when {
            mimeType == TEXT_MIME_TYPE -> {
                val bytes = loadFileBytes(uri)
                    ?: throw PayloadException("Failed to read text file")
                Payload("$prompt\n\n${bytes.toString(Charsets.UTF_8)}", emptyList())
            }

            mimeType == PDF_MIME_TYPE -> {
                val bitmaps = renderPdfPages(uri)
                if (bitmaps.isEmpty()) throw PayloadException("Failed to read PDF file")
                try {
                    Payload(prompt, bitmaps.map { encodeJpeg(it) })
                } finally {
                    bitmaps.forEach { it.recycle() }
                }
            }

            mimeType.startsWith("image/") || mode != AiMode.EXTRACT_PDF_TEXT -> {
                val bitmap = loadBitmapFromUri(uri)
                    ?: throw PayloadException("Failed to load image")
                try {
                    Payload(prompt, listOf(encodeJpeg(bitmap)))
                } finally {
                    bitmap.recycle()
                }
            }

            else -> throw PayloadException(
                "Unsupported file type: $mimeType\n\n" +
                    "Supported:\n" +
                    "• PDF files (.pdf)\n" +
                    "• Text files (.txt)\n" +
                    "• Images (.jpg, .png, .webp)\n\n" +
                    "For Word/PowerPoint/Excel files, please convert to PDF first."
            )
        }
    }

    // ------------------------------------------------------------------------
    // Provider configuration
    // ------------------------------------------------------------------------

    /** Fully resolved request target for a provider. */
    private data class ProviderConfig(
        val kind: ProviderKind,
        val model: String,
        val apiKey: String,
        val url: String? = null // required for OPENAI_COMPAT
    )

    /**
     * Resolve endpoint/model/key for [provider] from settings (with the bundled
     * Gemini key as a fallback). Returns null when the provider is not usable
     * yet — e.g. no key, or an unconfigured custom endpoint.
     */
    private suspend fun resolveConfig(provider: AiProvider): ProviderConfig? = when (provider) {
        AiProvider.GEMINI -> {
            val key = settingValue(SettingsKeys.GEMINI_API_KEY)
                ?: BuildConfig.GEMINI_API_KEY.trim().ifBlank { null }
            key?.let { ProviderConfig(ProviderKind.GEMINI, GEMINI_MODEL, it) }
        }
        AiProvider.OPENROUTER -> settingValue(SettingsKeys.OPENROUTER_API_KEY)?.let {
            ProviderConfig(ProviderKind.OPENAI_COMPAT, OPENROUTER_MODEL, it, OPENROUTER_URL)
        }
        AiProvider.OPENAI -> settingValue(SettingsKeys.OPENAI_API_KEY)?.let {
            ProviderConfig(ProviderKind.OPENAI_COMPAT, OPENAI_MODEL, it, OPENAI_URL)
        }
        AiProvider.CLAUDE -> settingValue(SettingsKeys.CLAUDE_API_KEY)?.let {
            ProviderConfig(ProviderKind.ANTHROPIC, CLAUDE_MODEL, it)
        }
        AiProvider.MISTRAL -> {
            val key = settingValue(SettingsKeys.MISTRAL_API_KEY)
                ?: BuildConfig.MISTRAL_API_KEY.trim().ifBlank { null }
            key?.let { ProviderConfig(ProviderKind.MISTRAL_OCR, MISTRAL_OCR_MODEL, it) }
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

    /** The chosen provider first, then every other configured one. */
    private suspend fun providerChain(
        first: AiProvider
    ): List<Pair<AiProvider, ProviderConfig>> {
        val order = listOf(first) + FALLBACK_ORDER.filter { it != first }
        return order.mapNotNull { provider -> resolveConfig(provider)?.let { provider to it } }
    }

    // ------------------------------------------------------------------------
    // Attempt loop: backoff, streaming degrade, timeout
    // ------------------------------------------------------------------------

    private sealed interface ProviderOutcome {
        data class Success(val text: String) : ProviderOutcome

        /** Retries exhausted; [networkProblem] = failures were I/O, not provider responses. */
        data class Exhausted(val networkProblem: Boolean = false) : ProviderOutcome
        data class Fatal(val error: AiResult.Error) : ProviderOutcome
    }

    /** Transient provider-side failure (429 / 5xx / overload) worth retrying. */
    private class TransientAiException(message: String) : Exception(message)

    /** Definitive provider error that retrying will not fix. */
    private class FatalAiException(val result: AiResult.Error) : Exception(result.message)

    /**
     * Run all attempts against one provider. Streaming is used first; if the
     * stream fails before yielding any text (some OpenAI-compatible endpoints
     * don't implement SSE), remaining attempts use plain requests.
     */
    private suspend fun runProvider(
        name: String,
        config: ProviderConfig,
        systemInstruction: String?,
        prompt: String,
        imageJpegs: List<ByteArray>,
        allowStreaming: Boolean,
        emit: suspend (AiEvent) -> Unit
    ): ProviderOutcome {
        var useStreaming = allowStreaming && config.kind != ProviderKind.MISTRAL_OCR
        var sawNetworkIo = false
        var lastFailureWasNetwork = false
        val timeoutMs = attemptTimeout(imageJpegs.size)

        repeat(MAX_ATTEMPTS) { attempt ->
            if (attempt > 0) {
                val wait = backoffDelay(attempt)
                val seconds = (wait + 500) / 1000
                val suffix = "retrying in $seconds s (attempt ${attempt + 1} of $MAX_ATTEMPTS)"
                emit(
                    AiEvent.Stage(
                        AiStage.PROCESSING,
                        if (lastFailureWasNetwork) "Connection trouble — $suffix"
                        else "$name is busy — $suffix"
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
                        requestStreaming(config, systemInstruction, prompt, imageJpegs, streamedAnything, emit)
                    } else {
                        emit(AiEvent.Stage(AiStage.PROCESSING))
                        requestOnce(config, systemInstruction, prompt, imageJpegs)
                    }
                }
                logDebug { "$name attempt ${attempt + 1} ok in ${SystemClock.elapsedRealtime() - start} ms" }
                return if (text.isBlank()) {
                    ProviderOutcome.Fatal(AiResult.Error("No response generated"))
                } else {
                    ProviderOutcome.Success(text)
                }
            } catch (e: TimeoutCancellationException) {
                logDebug { "$name attempt ${attempt + 1} timed out after $timeoutMs ms" }
                lastFailureWasNetwork = false
                // Partial text means the stream was alive but too slow; a
                // retry won't be faster.
                if (streamedAnything[0]) return ProviderOutcome.Exhausted()
            } catch (e: CancellationException) {
                throw e
            } catch (e: FatalAiException) {
                return ProviderOutcome.Fatal(e.result)
            } catch (e: TransientAiException) {
                logDebug { "$name attempt ${attempt + 1} transient: ${e.message}" }
                lastFailureWasNetwork = false
            } catch (e: HttpException) {
                when {
                    e.code() == 429 || e.code() in 500..599 -> {
                        logDebug { "$name attempt ${attempt + 1} HTTP ${e.code()}" }
                        lastFailureWasNetwork = false
                    }
                    else -> return ProviderOutcome.Fatal(AiResult.Error(httpErrorMessage(e.code())))
                }
            } catch (e: IOException) {
                logDebug { "$name attempt ${attempt + 1} IO: ${e.message}" }
                sawNetworkIo = true
                lastFailureWasNetwork = true
                if (!networkObserver.isCurrentlyOnline()) {
                    return ProviderOutcome.Exhausted(networkProblem = true)
                }
                // Stream that broke before any text may mean no SSE support.
                if (useStreaming && !streamedAnything[0]) useStreaming = false
            }
        }
        return ProviderOutcome.Exhausted(networkProblem = sawNetworkIo)
    }

    private fun backoffDelay(attempt: Int): Long =
        BACKOFF_BASE_MS * (1L shl (attempt - 1)) + Random.nextLong(BACKOFF_JITTER_MS)

    private fun attemptTimeout(imageCount: Int): Long =
        (ATTEMPT_TIMEOUT_BASE_MS +
            ATTEMPT_TIMEOUT_PER_EXTRA_IMAGE_MS * (imageCount - 1).coerceAtLeast(0))
            .coerceAtMost(ATTEMPT_TIMEOUT_MAX_MS)

    // ------------------------------------------------------------------------
    // Streaming requests (SSE)
    // ------------------------------------------------------------------------

    /**
     * Send one streaming request and return the full accumulated text,
     * emitting [AiEvent.Delta]s (throttled) as tokens arrive.
     */
    private suspend fun requestStreaming(
        config: ProviderConfig,
        systemInstruction: String?,
        prompt: String,
        imageJpegs: List<ByteArray>,
        streamedAnything: BooleanArray,
        emit: suspend (AiEvent) -> Unit
    ): String {
        val body: ResponseBody = when (config.kind) {
            ProviderKind.OPENAI_COMPAT -> openAiApi.chatCompletionStream(
                url = config.url ?: throw FatalAiException(AiResult.Error("Missing endpoint URL")),
                authorization = "Bearer ${config.apiKey}",
                request = openAiRequest(config, systemInstruction, prompt, imageJpegs, stream = true)
            )
            ProviderKind.ANTHROPIC -> claudeApi.messagesStream(
                apiKey = config.apiKey,
                request = claudeRequest(config, systemInstruction, prompt, imageJpegs, stream = true)
            )
            ProviderKind.GEMINI -> geminiApi.streamGenerateContent(
                model = config.model,
                apiKey = config.apiKey,
                request = geminiRequest(systemInstruction, prompt, imageJpegs)
            )
            ProviderKind.MISTRAL_OCR -> error("Mistral OCR does not stream")
        }
        emit(AiEvent.Stage(AiStage.PROCESSING))

        val accumulated = StringBuilder()
        var firstTokenAt = 0L
        var lastEmit = 0L
        val requestStart = SystemClock.elapsedRealtime()

        consumeSse(body) { payload ->
            val piece = when (config.kind) {
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

        if (accumulated.isNotEmpty()) {
            emit(AiEvent.Delta(accumulated.toString()))
            logDebug {
                "first token ${firstTokenAt - requestStart} ms, " +
                    "full response ${SystemClock.elapsedRealtime() - requestStart} ms"
            }
        }
        return accumulated.toString().trim()
    }

    /**
     * Read `data:` lines off an SSE body. Checks for cancellation between
     * lines so a user cancel is honored at token granularity; a fully stalled
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

    // ------------------------------------------------------------------------
    // Non-streaming requests
    // ------------------------------------------------------------------------

    /** Send one plain request and return the response text. */
    private suspend fun requestOnce(
        config: ProviderConfig,
        systemInstruction: String?,
        prompt: String,
        imageJpegs: List<ByteArray>
    ): String = when (config.kind) {
        ProviderKind.OPENAI_COMPAT -> {
            val url = config.url ?: throw FatalAiException(AiResult.Error("Missing endpoint URL"))
            val response = openAiApi.chatCompletion(
                url, "Bearer ${config.apiKey}",
                openAiRequest(config, systemInstruction, prompt, imageJpegs, stream = false)
            )
            response.error?.let { err ->
                if (err.code == 429) throw TransientAiException(err.message ?: "rate limited")
                throw FatalAiException(AiResult.Error(err.message ?: "Provider error"))
            }
            response.choices.firstOrNull()?.message?.content.orEmpty().trim()
        }
        ProviderKind.ANTHROPIC -> {
            val response = claudeApi.messages(
                apiKey = config.apiKey,
                request = claudeRequest(config, systemInstruction, prompt, imageJpegs, stream = false)
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
                geminiRequest(systemInstruction, prompt, imageJpegs)
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
        ProviderKind.MISTRAL_OCR -> mistralOcr(config, systemInstruction, prompt, imageJpegs)
    }

    /**
     * Mistral OCR: one request per image, pages joined as markdown. Text-only
     * prompts (txt files, translation) go to Mistral's chat API instead.
     * If the primary OCR model is rejected, retries once with the older model.
     */
    private suspend fun mistralOcr(
        config: ProviderConfig,
        systemInstruction: String?,
        prompt: String,
        imageJpegs: List<ByteArray>
    ): String {
        if (imageJpegs.isEmpty()) {
            val chatConfig = ProviderConfig(
                ProviderKind.OPENAI_COMPAT, MISTRAL_CHAT_MODEL, config.apiKey, MistralApi.CHAT_URL
            )
            return requestOnce(chatConfig, systemInstruction, prompt, imageJpegs)
        }

        val auth = "Bearer ${config.apiKey}"
        val results = imageJpegs.map { jpeg ->
            val document = MistralDocument(
                type = "image_url",
                image_url = "data:image/jpeg;base64,${base64(jpeg)}"
            )
            val response = try {
                mistralApi.ocr(auth, MistralOcrRequest(config.model, document))
            } catch (e: HttpException) {
                if (e.code() in 400..499 && e.code() != 429 && config.model != MISTRAL_OCR_FALLBACK_MODEL) {
                    logDebug { "Mistral ${config.model} rejected (HTTP ${e.code()}), trying $MISTRAL_OCR_FALLBACK_MODEL" }
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

    // ------------------------------------------------------------------------
    // Request builders
    // ------------------------------------------------------------------------

    private fun openAiRequest(
        config: ProviderConfig,
        systemInstruction: String?,
        prompt: String,
        imageJpegs: List<ByteArray>,
        stream: Boolean
    ): ChatRequest {
        val userParts = buildList {
            add(ContentPart.Text(prompt))
            imageJpegs.forEach {
                add(ContentPart.Image(ImageUrl("data:image/jpeg;base64,${base64(it)}")))
            }
        }
        val messages = buildList {
            if (systemInstruction != null) {
                add(ChatMessage("system", listOf(ContentPart.Text(systemInstruction))))
            }
            add(ChatMessage("user", userParts))
        }
        return ChatRequest(model = config.model, messages = messages, stream = stream)
    }

    private fun claudeRequest(
        config: ProviderConfig,
        systemInstruction: String?,
        prompt: String,
        imageJpegs: List<ByteArray>,
        stream: Boolean
    ): ClaudeRequest {
        val content = buildList {
            add(ClaudeContent.Text(prompt))
            imageJpegs.forEach {
                add(ClaudeContent.Image(ClaudeImageSource(mediaType = "image/jpeg", data = base64(it))))
            }
        }
        return ClaudeRequest(
            model = config.model,
            system = systemInstruction,
            messages = listOf(ClaudeMessage(role = "user", content = content)),
            stream = stream
        )
    }

    private fun geminiRequest(
        systemInstruction: String?,
        prompt: String,
        imageJpegs: List<ByteArray>
    ): GeminiRequest {
        // Gemma models have no system role, so fold the instruction into the prompt.
        val fullPrompt = if (systemInstruction != null) "$systemInstruction\n\n$prompt" else prompt
        val parts = buildList {
            add(GeminiPart(text = fullPrompt))
            imageJpegs.forEach {
                add(GeminiPart(inlineData = GeminiInlineData("image/jpeg", base64(it))))
            }
        }
        return GeminiRequest(contents = listOf(GeminiContent(role = "user", parts = parts)))
    }

    // ------------------------------------------------------------------------
    // Misc helpers
    // ------------------------------------------------------------------------

    private fun httpErrorMessage(code: Int): String = when (code) {
        429 -> "Provider is rate-limited right now. Try again shortly, or add " +
            "your own API key in Settings → AI Providers."
        401, 403 -> "Invalid or unauthorized API key. Check it in Settings → AI Providers."
        else -> "Request failed (HTTP $code)"
    }

    /** Read a string setting, trimmed, or null if unset/blank. */
    private suspend fun settingValue(key: SettingsKeys): String? =
        settingsRepository.getString(key).first().trim().ifBlank { null }

    private fun missingConfigError(provider: AiProvider): AiResult.Error = AiResult.Error(
        "${provider.displayName} isn't set up yet.\n\n" +
            "Add your ${provider.displayName} details in Settings → AI Providers."
    )

    private inline fun logDebug(message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message())
    }

    private inline fun <T> timed(label: String, block: () -> T): T {
        val start = SystemClock.elapsedRealtime()
        return block().also {
            logDebug { "$label took ${SystemClock.elapsedRealtime() - start} ms" }
        }
    }

    /** Downscale + JPEG-encode a bitmap into raw bytes for inline upload. */
    private fun encodeJpeg(bitmap: Bitmap): ByteArray {
        val scaled = downscale(bitmap)
        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        if (scaled !== bitmap) scaled.recycle()
        return output.toByteArray()
    }

    private fun base64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    /**
     * Downscale a bitmap so its largest dimension is at most [MAX_IMAGE_DIMENSION].
     * Returns the original bitmap when no scaling is needed.
     */
    private fun downscale(bitmap: Bitmap): Bitmap {
        val maxDim = maxOf(bitmap.width, bitmap.height)
        if (maxDim <= MAX_IMAGE_DIMENSION) return bitmap
        val ratio = MAX_IMAGE_DIMENSION.toFloat() / maxDim
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    /**
     * Load raw file bytes from URI.
     */
    private fun loadFileBytes(uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Load a Bitmap from a content URI (for images).
     */
    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Render up to [MAX_PDF_PAGES] pages of a PDF to bitmaps.
     */
    private fun renderPdfPages(uri: Uri): List<Bitmap> {
        val bitmaps = mutableListOf<Bitmap>()
        var tempFile: File? = null
        try {
            tempFile = File(context.cacheDir, "temp_pdf_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            }

            ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    val pageCount = minOf(renderer.pageCount, MAX_PDF_PAGES)
                    for (i in 0 until pageCount) {
                        renderer.openPage(i).use { page ->
                            val scale = 2.0f
                            val width = (page.width * scale).toInt()
                            val height = (page.height * scale).toInt()
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            // White background — PDFs render transparent otherwise.
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmaps.add(bitmap)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            tempFile?.delete()
        }
        return bitmaps
    }
}
