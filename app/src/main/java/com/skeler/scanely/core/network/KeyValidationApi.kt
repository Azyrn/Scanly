package com.skeler.scanely.core.network

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.Url

// Cheap auth probe (usually GET /models). Raw Response so 4xx isn't thrown.
interface KeyValidationApi {

    @GET
    suspend fun get(
        @Url url: String,
        @HeaderMap headers: Map<String, String>
    ): Response<ResponseBody>

    // NVIDIA NIM only authenticates via POST.
    @POST
    suspend fun post(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @Body body: RequestBody
    ): Response<ResponseBody>

    companion object {
        // Dummy base; real endpoint always passed via @Url.
        const val BASE_URL = "https://api.openai.com/"
    }
}
