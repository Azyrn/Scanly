package com.skeler.scanely.core.barcode

enum class BarcodeEngine(val id: String, val displayName: String) {
    ML_KIT("mlkit", "ML Kit"),
    ZXING_CPP("zxing", "ZXing-C++");

    companion object {
        val DEFAULT = ML_KIT
        fun fromId(id: String?): BarcodeEngine = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
