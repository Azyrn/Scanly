package com.skeler.scanely.core.ai

import android.util.Log
import com.skeler.scanely.BuildConfig

/**
 * Patterns that match API keys / bearer tokens in any shape they might appear in
 * a string (URL query, auth header, or a bare provider-prefixed key). Ordered so
 * more specific prefixes are redacted before their shorter super-strings.
 */
private val SECRET_PATTERNS: List<Pair<Regex, String>> = listOf(
    Regex("(?i)(bearer\\s+)[A-Za-z0-9._\\-]+") to "$1***",
    Regex("(?i)([?&](?:key|api[_-]?key|access_token|token)=)[^&\\s]+") to "$1***",
    Regex("(?i)(x-(?:goog-)?api-key[\"'\\s:=]+)[A-Za-z0-9._\\-]+") to "$1***",
    Regex("sk-ant-[A-Za-z0-9._\\-]+") to "***",
    Regex("sk-or-[A-Za-z0-9._\\-]+") to "***",
    Regex("sk-[A-Za-z0-9._\\-]+") to "***",
    Regex("AIza[A-Za-z0-9._\\-]+") to "***"
)

/**
 * Strip anything that looks like an API key or token from [text]. Applied to
 * every AI log line so a key can never reach Logcat — or any future crash /
 * analytics sink that reads these messages.
 */
internal fun redactSecrets(text: String): String =
    SECRET_PATTERNS.fold(text) { acc, (regex, replacement) -> regex.replace(acc, replacement) }

internal inline fun aiDebug(message: () -> String) {
    if (BuildConfig.DEBUG) Log.d("GenerativeAi", redactSecrets(message()))
}
