package com.skeler.scanely.core.ai

import com.skeler.scanely.core.network.ChatMessage
import com.skeler.scanely.core.network.ChatRequest
import com.skeler.scanely.core.network.ClaudeContent
import com.skeler.scanely.core.network.ClaudeImageSource
import com.skeler.scanely.core.network.ClaudeMessage
import com.skeler.scanely.core.network.ClaudeRequest
import com.skeler.scanely.core.network.ContentPart
import com.skeler.scanely.core.network.GeminiContent
import com.skeler.scanely.core.network.GeminiInlineData
import com.skeler.scanely.core.network.GeminiPart
import com.skeler.scanely.core.network.GeminiRequest
import com.skeler.scanely.core.network.ImageUrl

internal object AiRequestFactory {
    fun openAi(
        config: ProviderConfig,
        systemInstruction: String?,
        prompt: String,
        images: List<String>,
        stream: Boolean
    ): ChatRequest {
        val userParts = buildList {
            add(ContentPart.Text(prompt))
            images.forEach {
                add(ContentPart.Image(ImageUrl("data:image/jpeg;base64,$it")))
            }
        }
        val messages = buildList {
            if (systemInstruction != null) {
                add(ChatMessage("system", listOf(ContentPart.Text(systemInstruction))))
            }
            add(ChatMessage("user", userParts))
        }
        // Groq Qwen3 otherwise emits <think>; "none" disables reasoning.
        val reasoningEffort = if (
            config.url?.contains("api.groq.com") == true &&
            config.model.contains("qwen", ignoreCase = true)
        ) "none" else null
        return ChatRequest(
            model = config.model,
            messages = messages,
            stream = stream,
            reasoningEffort = reasoningEffort
        )
    }

    fun claude(
        config: ProviderConfig,
        systemInstruction: String?,
        prompt: String,
        images: List<String>,
        stream: Boolean
    ): ClaudeRequest {
        val content = buildList {
            add(ClaudeContent.Text(prompt))
            images.forEach {
                add(ClaudeContent.Image(ClaudeImageSource(mediaType = "image/jpeg", data = it)))
            }
        }
        return ClaudeRequest(
            model = config.model,
            system = systemInstruction,
            messages = listOf(ClaudeMessage(role = "user", content = content)),
            stream = stream
        )
    }

    fun gemini(
        systemInstruction: String?,
        prompt: String,
        images: List<String>
    ): GeminiRequest {
        // Gemma has no system role — fold instruction into prompt.
        val fullPrompt = if (systemInstruction != null) "$systemInstruction\n\n$prompt" else prompt
        val parts = buildList {
            add(GeminiPart(text = fullPrompt))
            images.forEach {
                add(GeminiPart(inlineData = GeminiInlineData("image/jpeg", it)))
            }
        }
        return GeminiRequest(contents = listOf(GeminiContent(role = "user", parts = parts)))
    }
}
