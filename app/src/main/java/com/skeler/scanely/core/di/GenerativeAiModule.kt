package com.skeler.scanely.core.di

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.skeler.scanely.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GenerativeAiModule {

    private const val MODEL_NAME = "gemini-2.5-flash"

    /**
     * ULTRATHINK System Instruction Rationale:
     * - Focuses model on OCR accuracy over creative interpretation
     * - Explicit formatting preservation prevents unwanted summarization
     * - Temperature 0.1 minimizes hallucination in text extraction
     */
    private const val SYSTEM_INSTRUCTION = """You are a precise document text extraction assistant.
Your task is to extract text exactly as it appears in images and documents.

Rules:
1. Extract ALL visible text with 100% accuracy
2. Preserve original formatting, line breaks, and structure
3. Do NOT summarize, interpret, or modify any content
4. Do NOT add any commentary or descriptions
5. For tables, maintain column alignment using spaces
6. For multi-language documents, preserve all languages as-is
7. If text is unclear, mark it with [unclear] but attempt best guess"""

    @Provides
    @Singleton
    fun provideGenerativeModel(): GenerativeModel = GenerativeModel(
        modelName = MODEL_NAME,
        apiKey = BuildConfig.GEMINI_API_KEY,
        systemInstruction = content { text(SYSTEM_INSTRUCTION) },
        generationConfig = generationConfig {
            temperature = 0.1f
        }
    )
}

