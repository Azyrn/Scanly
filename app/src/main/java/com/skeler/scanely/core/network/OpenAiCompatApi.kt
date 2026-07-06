package com.skeler.scanely.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * OpenAI-compatible chat-completions API.
 *
 * Shared by every provider that speaks the OpenAI wire format (OpenRouter,
 * OpenAI, and user-defined custom endpoints); the full endpoint URL and bearer
 * token are passed per request, so one client serves all of them. Images are
 * sent as base64 data URLs in the multimodal `content` array, so the configured
 * model must be vision-capable.
 */
interface OpenAiCompatApi {

    @POST
    suspend fun chatCompletion(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Body request: ChatRequest
    ): ChatResponse

    /**
     * Server-sent-events variant (request must set `stream = true`). The body
     * is a stream of `data: {json}` lines terminated by `data: [DONE]`.
     */
    @Streaming
    @POST
    suspend fun chatCompletionStream(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Body request: ChatRequest
    ): ResponseBody

    companion object {
        // Placeholder base; the real endpoint is supplied per request via @Url.
        const val BASE_URL = "https://openrouter.ai/"
    }
}

// --- Request models ---

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.1,
    val stream: Boolean = false,
    // Groq-only: "none" disables Qwen3 thinking so <think> blocks don't pollute
    // the extracted text. Omitted (null) for every other provider/model.
    @SerialName("reasoning_effort") val reasoningEffort: String? = null
)

@Serializable
data class ChatMessage(
    val role: String, // "system" | "user" | "assistant"
    val content: List<ContentPart>
)

/**
 * A single piece of message content. Serialized with a `type` discriminator so
 * it matches the OpenAI/OpenRouter wire format:
 *   {"type":"text","text":"..."}
 *   {"type":"image_url","image_url":{"url":"data:image/jpeg;base64,..."}}
 */
@Serializable
sealed class ContentPart {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : ContentPart()

    @Serializable
    @SerialName("image_url")
    data class Image(
        @SerialName("image_url") val imageUrl: ImageUrl
    ) : ContentPart()
}

@Serializable
data class ImageUrl(val url: String)

// --- Response models ---

@Serializable
data class ChatResponse(
    val choices: List<Choice> = emptyList(),
    val error: ApiError? = null
)

@Serializable
data class Choice(
    val message: ResponseMessage
)

/** Assistant replies return `content` as a plain string, not an array. */
@Serializable
data class ResponseMessage(
    val role: String? = null,
    val content: String? = null
)

@Serializable
data class ApiError(
    val message: String? = null,
    val code: Int? = null
)

// --- Streaming response models ---

/** One SSE chunk of a streamed chat completion. */
@Serializable
data class ChatStreamChunk(
    val choices: List<StreamChoice> = emptyList(),
    val error: ApiError? = null
)

@Serializable
data class StreamChoice(
    val delta: StreamDelta? = null
)

@Serializable
data class StreamDelta(
    val content: String? = null
)
