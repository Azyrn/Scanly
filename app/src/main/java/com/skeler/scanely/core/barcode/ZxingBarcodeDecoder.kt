package com.skeler.scanely.core.barcode

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.skeler.scanely.core.actions.ScanAction
import zxingcpp.BarcodeReader

class ZxingBarcodeDecoder {

    // Max-capability decode: every readable format plus all robustness passes
    // (rotated/inverted/downscaled/denoised, tryHarder for small & damaged codes).
    private val reader = BarcodeReader(
        BarcodeReader.Options(
            formats = emptySet(),
            tryHarder = true,
            tryRotate = true,
            tryInvert = true,
            tryDownscale = true,
            tryDenoise = true,
            textMode = BarcodeReader.TextMode.HRI,
        )
    )

    fun decode(imageProxy: ImageProxy): List<DecodedBarcode> = reader.read(imageProxy).toDecoded()

    fun decode(bitmap: Bitmap): List<DecodedBarcode> = reader.read(bitmap).toDecoded()

    private fun List<BarcodeReader.Result>.toDecoded(): List<DecodedBarcode> =
        mapNotNull { result ->
            val text = result.text?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            DecodedBarcode(text = text, format = result.format)
        }.distinctBy { it.text }
}

data class DecodedBarcode(val text: String, val format: BarcodeReader.Format) {

    val isProductCode: Boolean get() = format in PRODUCT_FORMATS

    fun toActions(): List<ScanAction> = BarcodeContentParser.parse(this)

    private companion object {
        val PRODUCT_FORMATS = setOf(
            BarcodeReader.Format.EAN_8,
            BarcodeReader.Format.EAN_13,
            BarcodeReader.Format.UPC_A,
            BarcodeReader.Format.UPC_E,
            BarcodeReader.Format.ISBN,
        )
    }
}
