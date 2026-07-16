package com.skeler.scanely.core.ai

/**
 * Decides what a single provider request may carry.
 *
 * Every scan is one request, so a page count above the provider's cap is trimmed rather than split
 * across requests: splitting would multiply use of a free tier's daily request quota, and rejected
 * oversized requests count against it too.
 */
internal object ScanBudget {

    data class SendSet(
        val images: List<String>,
        val pdfBase64: String? = null,
        val dropped: Int = 0
    )

    /** Mistral OCR reads every page from one document, so a source PDF is never truncated. */
    fun trimFor(provider: AiProvider, payload: PayloadFactory.Payload): SendSet {
        if (provider.kind == ProviderKind.MISTRAL_OCR && payload.pdfBase64 != null) {
            return SendSet(emptyList(), payload.pdfBase64)
        }
        val cap = provider.maxImagesPerRequest
        if (payload.images.size <= cap) return SendSet(payload.images)
        return SendSet(payload.images.take(cap), dropped = payload.images.size - cap)
    }

    fun truncationNotice(provider: AiProvider, total: Int): String {
        val cap = provider.maxImagesPerRequest
        val unit = if (cap == 1) "page" else "pages"
        return "Note: ${provider.displayName} accepts $cap $unit per request, so only the " +
            "first $cap of $total were scanned. Gemini or OpenRouter can read all $total in one go."
    }
}
