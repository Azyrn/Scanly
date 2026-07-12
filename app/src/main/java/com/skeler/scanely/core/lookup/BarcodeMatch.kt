package com.skeler.scanely.core.lookup

import kotlinx.serialization.json.Json

val LookupJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

/** EAN-8 / EAN-13 / UPC-A/E: 8-13 digit numeric barcode. */
fun isEanUpc(barcode: String): Boolean =
    barcode.length in 8..13 && barcode.all { it.isDigit() }

/** ISBN-10 (9 digits + digit|X) or ISBN-13 (978/979 prefix, 13 digits). */
fun isIsbn(barcode: String): Boolean {
    val c = barcode.replace("-", "").uppercase()
    return when (c.length) {
        10 -> c.take(9).all { it.isDigit() } && (c.last().isDigit() || c.last() == 'X')
        13 -> c.all { it.isDigit() } && (c.startsWith("978") || c.startsWith("979"))
        else -> false
    }
}
