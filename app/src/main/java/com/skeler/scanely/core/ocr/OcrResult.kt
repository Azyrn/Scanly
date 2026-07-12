package com.skeler.scanely.core.ocr

/** Which engine produced a result; only Paddle carries per-line boxes and confidences. */
enum class OcrSource { ML_KIT, PADDLE }

sealed class OcrResult {
    data class Success(
        val text: String,
        val blocks: List<TextBlockData> = emptyList(),
        val confidence: Float = 1.0f,
        val source: OcrSource = OcrSource.ML_KIT,
        /** Layout-aware Markdown; only the offline structure pipeline produces it. */
        val markdown: String? = null
    ) : OcrResult()

    data class Error(val message: String) : OcrResult()

    data object Empty : OcrResult()
}

data class TextBlockData(
    val text: String,
    val boundingBoxLeft: Int,
    val boundingBoxTop: Int,
    val boundingBoxRight: Int,
    val boundingBoxBottom: Int,
    val confidence: Float = 1.0f,
    val page: Int = 1
)
