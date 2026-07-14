package com.skeler.scanely.core.ai

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Retrofit resolves a scheme-less @Url against its base URL, so an endpoint typed as
 * "my-host/v1/chat/completions" would POST the user's key to openrouter.ai. Only an
 * absolute http(s) URL may ever reach the client.
 */
internal object EndpointUrl {

    fun normalize(raw: String): String? {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null

        val absolute = if (HAS_SCHEME.containsMatchIn(trimmed)) trimmed else "https://$trimmed"
        val url = absolute.toHttpUrlOrNull() ?: return null

        val path = url.encodedPath
        return when {
            path.endsWith(CHAT_PATH) -> url.toString()
            path.trimEnd('/').endsWith("/v1") ->
                url.newBuilder().addPathSegments(CHAT_SEGMENTS).build().toString()
            else -> url.toString()
        }
    }

    fun modelsUrl(chatUrl: String): String? {
        val idx = chatUrl.indexOf(CHAT_PATH)
        return if (idx >= 0) chatUrl.substring(0, idx) + "/models" else null
    }

    private const val CHAT_PATH = "/chat/completions"
    private const val CHAT_SEGMENTS = "chat/completions"
    private val HAS_SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
}
