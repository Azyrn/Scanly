package com.skeler.scanely.core.ocr.paddle

/**
 * Recognition model per script. Detection is script-agnostic and always bundled.
 * Bundled packs live in assets; the rest download once from the official
 * PaddlePaddle Hugging Face repos and then run fully offline.
 */
enum class ScriptPack(
    val id: String,
    val displayName: String,
    val languages: String,
    val repo: String?,
    val bundledModel: String? = null,
    val bundledDict: String? = null,
    val sizeMb: Int = 0
) {
    UNIVERSAL(
        id = "universal",
        displayName = "Universal (PP-OCRv6)",
        languages = "English, Chinese, Japanese + 46 more",
        repo = null,
        bundledModel = "ocr/rec_v6_small.onnx",
        bundledDict = "ocr/dict_v6.txt"
    ),
    ARABIC(
        id = "arabic",
        displayName = "Arabic",
        languages = "Arabic, Persian, Urdu, Uyghur",
        repo = null,
        bundledModel = "ocr/rec_v5_arabic.onnx",
        bundledDict = "ocr/dict_arabic.txt"
    ),
    KOREAN("korean", "Korean", "Korean", "korean_PP-OCRv5_mobile_rec_onnx", sizeMb = 14),
    CYRILLIC(
        "cyrillic",
        "Cyrillic",
        "Russian, Ukrainian, Bulgarian, Serbian",
        "cyrillic_PP-OCRv5_mobile_rec_onnx",
        sizeMb = 8
    ),
    DEVANAGARI(
        "devanagari",
        "Devanagari",
        "Hindi, Marathi, Nepali, Sanskrit",
        "devanagari_PP-OCRv5_mobile_rec_onnx",
        sizeMb = 9
    ),
    THAI("thai", "Thai", "Thai", "th_PP-OCRv5_mobile_rec_onnx", sizeMb = 8),
    GREEK("greek", "Greek", "Greek", "el_PP-OCRv5_mobile_rec_onnx", sizeMb = 8),
    TAMIL("tamil", "Tamil", "Tamil", "ta_PP-OCRv5_mobile_rec_onnx", sizeMb = 9),
    TELUGU("telugu", "Telugu", "Telugu", "te_PP-OCRv5_mobile_rec_onnx", sizeMb = 9);

    val isBundled get() = bundledModel != null

    companion object {
        val DEFAULT = UNIVERSAL
        val DOWNLOADABLE = entries.filter { !it.isBundled }

        fun fromId(id: String?): ScriptPack = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
