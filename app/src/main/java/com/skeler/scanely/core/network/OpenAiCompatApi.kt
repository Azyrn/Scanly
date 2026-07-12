package com.skeler.scanely.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Streaming
import retrofit2.http.Url

interface OpenAiCompatApi {

    @POST
    suspend fun chatCompletion(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Body request: ChatRequest
    ): ChatResponse

    @Streaming
    @POST
    suspend fun chatCompletionStream(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Body request: ChatRequest
    ): ResponseBody

    companion object {
        const val BASE_URL = "https://openrouter.ai/"
    }
}

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.1,
    val stream: Boolean = false,
    // Groq-only: "none" disables Qwen3 thinking so <think> blocks don't pollute
    @SerialName("reasoning_effort") val reasoningEffort: String? = null
)

@Serializable
data class ChatMessage(
    val role: String, // "system" | "user" | "assistant"
    val content: List<ContentPart>
)

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

@Serializable
data class ChatResponse(
    val choices: List<Choice> = emptyList(),
    val error: ApiError? = null
)

@Serializable
data class Choice(
    val message: ResponseMessage
)

@Serializable
data class ResponseMessage(
    val role: String? = null,
    val content: String? = null
)

@Serializable
data class ApiError(
    val message: String? = null,
    val code: JsonElement? = null
)

val ApiError.codeInt: Int?
    get() = (code as? JsonPrimitive)?.intOrNull

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
