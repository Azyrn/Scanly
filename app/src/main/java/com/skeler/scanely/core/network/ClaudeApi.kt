package com.skeler.scanely.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Streaming

/**
 * Anthropic (Claude) Messages API.
 *
 * Distinct wire format from OpenAI: auth is the `x-api-key` header, a version
 * header is required, and images are sent as base64 `source` blocks. The model
 * must be vision-capable. API docs: https://docs.anthropic.com/en/api/messages
 */
interface ClaudeApi {

    @POST("v1/messages")
    suspend fun messages(
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") version: String = ANTHROPIC_VERSION,
        @Body request: ClaudeRequest
    ): ClaudeResponse

    /**
     * Server-sent-events variant (request must set `stream = true`). Text
     * arrives as `content_block_delta` events carrying `text_delta` payloads.
     */
    @Streaming
    @POST("v1/messages")
    suspend fun messagesStream(
        @Header("x-api-key") apiKey: String,
        @Header("anthropic-version") version: String = ANTHROPIC_VERSION,
        @Body request: ClaudeRequest
    ): ResponseBody

    companion object {
        const val BASE_URL = "https://api.anthropic.com/"
        const val ANTHROPIC_VERSION = "2023-06-01"
    }
}

// --- Request models ---

@Serializable
data class ClaudeRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int = 4096,
    val system: String? = null,
    val messages: List<ClaudeMessage>,
    val stream: Boolean = false
)

@Serializable
data class ClaudeMessage(
    val role: String, // "user" | "assistant"
    val content: List<ClaudeContent>
)

@Serializable
sealed class ClaudeContent {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : ClaudeContent()

    @Serializable
    @SerialName("image")
    data class Image(val source: ClaudeImageSource) : ClaudeContent()
}

@Serializable
data class ClaudeImageSource(
    val type: String = "base64",
    @SerialName("media_type") val mediaType: String,
    val data: String
)

// --- Response models ---

@Serializable
data class ClaudeResponse(
    val content: List<ClaudeResponseContent> = emptyList(),
    val error: ClaudeError? = null
)

@Serializable
data class ClaudeResponseContent(
    val type: String? = null,
    val text: String? = null
)

@Serializable
data class ClaudeError(
    val type: String? = null,
    val message: String? = null
)

// --- Streaming response models ---

/** One SSE event of a streamed Claude message. */
@Serializable
data class ClaudeStreamEvent(
    val type: String? = null,
    val delta: ClaudeStreamDelta? = null,
    val error: ClaudeError? = null
)

@Serializable
data class ClaudeStreamDelta(
    val type: String? = null,
    val text: String? = null
)
