package com.skeler.scanely.core.ai

/** AI processing mode for image/document analysis. */
enum class AiMode {
    EXTRACT_TEXT,
    EXTRACT_PDF_TEXT,
    ICON_TRANSLATE
}

/** Terminal result of an AI operation. */
sealed class AiResult {
    data class Success(val text: String) : AiResult()
    data class Error(val message: String) : AiResult()
}

/**
 * The provider, model and key source that actually served a successful request.
 * [usesBundledKey] is false when the user's own API key made the call.
 */
data class AiRunInfo(
    val provider: AiProvider,
    val model: String,
    val usesBundledKey: Boolean
)

/** User-visible phases of one extraction, in order. */
enum class AiStage {
    PREPARING,
    UPLOADING,
    PROCESSING,
    GENERATING,
    COMPLETE
}

/**
 * Progress events emitted while an extraction runs.
 *
 * [Stage] marks a phase change (with an optional human-readable note);
 * [Delta] carries the accumulated streamed text so far; [Finished] is always
 * the terminal event.
 */
sealed class AiEvent {
    data class Stage(val stage: AiStage, val message: String? = null) : AiEvent()
    data class Delta(val textSoFar: String) : AiEvent()
    data class Finished(val result: AiResult, val runInfo: AiRunInfo? = null) : AiEvent()
}
