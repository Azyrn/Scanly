package com.skeler.scanely.core.ocr

enum class TextOcrEngine(val id: String, val displayName: String, val description: String) {
    PADDLE(
        id = "paddle",
        displayName = "PaddleOCR (PP-OCRv6)",
        description = "Higher accuracy, 50+ languages, fully offline"
    ),
    ML_KIT(
        id = "mlkit",
        displayName = "ML Kit",
        description = "Google's on-device recognizer, fastest"
    );

    companion object {
        val DEFAULT = PADDLE

        fun fromId(id: String?): TextOcrEngine = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
