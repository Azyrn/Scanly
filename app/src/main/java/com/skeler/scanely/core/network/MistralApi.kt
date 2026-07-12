package com.skeler.scanely.core.network

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface MistralApi {

    @POST("v1/ocr")
    suspend fun ocr(
        @Header("Authorization") authorization: String,
        @Body request: MistralOcrRequest
    ): MistralOcrResponse

    companion object {
        const val BASE_URL = "https://api.mistral.ai/"
        const val CHAT_URL = "https://api.mistral.ai/v1/chat/completions"
    }
}

@Serializable
data class MistralOcrRequest(
    val model: String,
    val document: MistralDocument
)

@Serializable
data class MistralDocument(
    val type: String = "image_url",
    val image_url: String? = null,
    val document_url: String? = null
)

@Serializable
data class MistralOcrResponse(
    val pages: List<MistralOcrPage> = emptyList()
)

@Serializable
data class MistralOcrPage(
    val index: Int = 0,
    val markdown: String = ""
)
