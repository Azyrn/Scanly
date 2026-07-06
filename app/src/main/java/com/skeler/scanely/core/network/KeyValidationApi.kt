package com.skeler.scanely.core.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.Url

/**
 * Minimal transport for API-key validation. Every provider exposes a cheap,
 * read-only endpoint (usually `/models`) that authenticates a key without
 * spending tokens. The full URL and headers are supplied per call, and the raw
 * [Response] is returned so the caller can read the status code without an
 * exception being thrown on 4xx.
 */
interface KeyValidationApi {

    @GET
    suspend fun get(
        @Url url: String,
        @HeaderMap headers: Map<String, String>
    ): Response<ResponseBody>

    companion object {
        // Placeholder base; the real endpoint is supplied per request via @Url.
        const val BASE_URL = "https://api.openai.com/"
    }
}
