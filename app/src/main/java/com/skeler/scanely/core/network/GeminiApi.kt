package com.skeler.scanely.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * Google Generative Language API (Gemini / Gemma models).
 *
 * Used as an alternative provider to OpenRouter for text extraction and
 * translation. Images are sent inline as base64 in `inline_data` parts.
 *
 * The API key is supplied per request as a `?key=` query parameter (the user
 * enters it in Settings). Gemma models "think" by default and return their
 * reasoning as separate parts flagged with `thought = true`; callers must keep
 * only the non-thought parts to get the clean answer.
 *
 * API docs: https://ai.google.dev/api/generate-content
 */
interface GeminiApi {

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse

    /**
     * Server-sent-events variant: each `data:` line is a [GeminiResponse]-shaped
     * chunk whose candidate parts hold the incremental text (thought parts must
     * still be filtered out by the caller).
     */
    @Streaming
    @POST("v1beta/models/{model}:streamGenerateContent?alt=sse")
    suspend fun streamGenerateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): ResponseBody

    companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/"
    }
}

// --- Request models ---

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig = GeminiGenerationConfig()
)

@Serializable
data class GeminiContent(
    val role: String = "user",
    val parts: List<GeminiPart>
)

/**
 * A single content part. In requests only one of [text] / [inlineData] is set.
 * In responses, reasoning parts additionally carry [thought] = true.
 */
@Serializable
data class GeminiPart(
    val text: String? = null,
    @SerialName("inline_data") val inlineData: GeminiInlineData? = null,
    val thought: Boolean? = null
)

@Serializable
data class GeminiInlineData(
    @SerialName("mime_type") val mimeType: String,
    val data: String
)

@Serializable
data class GeminiGenerationConfig(
    val temperature: Double = 0.1
)

// --- Response models ---

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
    val error: GeminiError? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null
)

@Serializable
data class GeminiError(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)
