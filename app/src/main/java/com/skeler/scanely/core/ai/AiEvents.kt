package com.skeler.scanely.core.ai

enum class AiMode {
    EXTRACT_TEXT,
    EXTRACT_PDF_TEXT,
    ICON_TRANSLATE
}

enum class SummaryLength(val label: String) {
    SHORT("Short"),
    MEDIUM("Medium"),
    DETAILED("Detailed")
}

sealed class AiResult {
    data class Success(val text: String) : AiResult()
    data class Error(val message: String) : AiResult()
}

/** [usesBundledKey] false when the user's own API key served the call. */
data class AiRunInfo(
    val provider: AiProvider,
    val model: String,
    val usesBundledKey: Boolean
)

enum class AiStage {
    PREPARING,
    UPLOADING,
    PROCESSING,
    GENERATING,
    COMPLETE
}

sealed class AiEvent {
    data class Stage(val stage: AiStage, val message: String? = null) : AiEvent()
    data class Delta(val textSoFar: String) : AiEvent()
    data class Finished(val result: AiResult, val runInfo: AiRunInfo? = null) : AiEvent()
}
