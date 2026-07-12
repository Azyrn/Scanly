package com.skeler.scanely.core.ai

import android.util.Log
import com.skeler.scanely.BuildConfig

// More-specific key prefixes first so shorter super-strings don't win.
private val SECRET_PATTERNS: List<Pair<Regex, String>> = listOf(
    Regex("(?i)(bearer\\s+)[A-Za-z0-9._\\-]+") to "$1***",
    Regex("(?i)([?&](?:key|api[_-]?key|access_token|token)=)[^&\\s]+") to "$1***",
    Regex("(?i)(x-(?:goog-)?api-key[\"'\\s:=]+)[A-Za-z0-9._\\-]+") to "$1***",
    Regex("sk-ant-[A-Za-z0-9._\\-]+") to "***",
    Regex("sk-or-[A-Za-z0-9._\\-]+") to "***",
    Regex("sk-[A-Za-z0-9._\\-]+") to "***",
    Regex("AIza[A-Za-z0-9._\\-]+") to "***",
    Regex("nvapi-[A-Za-z0-9._\\-]+") to "***",
    Regex("gsk_[A-Za-z0-9._\\-]+") to "***",
    Regex("hf_[A-Za-z0-9._\\-]+") to "***",
    Regex("cfut_[A-Za-z0-9._\\-]+") to "***"
)

internal fun redactSecrets(text: String): String =
    SECRET_PATTERNS.fold(text) { acc, (regex, replacement) -> regex.replace(acc, replacement) }

internal inline fun aiDebug(message: () -> String) {
    if (BuildConfig.DEBUG) Log.d("GenerativeAi", redactSecrets(message()))
}
