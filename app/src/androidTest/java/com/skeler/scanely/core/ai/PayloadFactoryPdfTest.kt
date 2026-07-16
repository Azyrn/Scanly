package com.skeler.scanely.core.ai

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises the real PdfRenderer path, so it catches page-cap regressions without any network. */
@RunWith(AndroidJUnit4::class)
class PayloadFactoryPdfTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val factory = PayloadFactory(context)

    private fun writePdf(pages: Int): Uri {
        val doc = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 12f
        }
        repeat(pages) { p ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, p + 1).create())
            page.canvas.drawText("Page ${p + 1} of $pages", 60f, 80f, paint)
            doc.finishPage(page)
        }
        // Via FileProvider, so the resolver reports a real MIME type like a SAF pick does.
        val dir = File(context.cacheDir, "scans").apply { mkdirs() }
        val file = File(dir, "payload_test_$pages.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    @Test
    fun rendersEveryPageOfAShortPdf() {
        val payload = factory.create(listOf(writePdf(8)), AiMode.EXTRACT_PDF_TEXT)

        assertEquals("all 8 pages should render", 8, payload.images.size)
        assertTrue("pages must not be blank", payload.images.all { it.isNotBlank() })
    }

    /** 20 is asserted literally: comparing against MAX_SCAN_PAGES would pass at any cap. */
    @Test
    fun longPdfIsCappedAtTwentyPages() {
        val payload = factory.create(listOf(writePdf(25)), AiMode.EXTRACT_PDF_TEXT)

        assertEquals("must stop at 20 pages", 20, payload.images.size)
    }

    @Test
    fun pdfSourceIsCarriedOnlyWhenRequested() {
        val uri = writePdf(4)

        val withSource = factory.create(listOf(uri), AiMode.EXTRACT_PDF_TEXT, includePdfSource = true)
        assertNotNull("Mistral needs the source PDF", withSource.pdfBase64)
        assertTrue(withSource.pdfBase64!!.isNotBlank())

        val withoutSource = factory.create(listOf(uri), AiMode.EXTRACT_PDF_TEXT)
        assertNull("no provider asked for the PDF, so don't hold it", withoutSource.pdfBase64)
    }

    @Test
    fun skippingRenderStillYieldsTheSourcePdf() {
        val payload = factory.create(
            uris = listOf(writePdf(12)),
            mode = AiMode.EXTRACT_PDF_TEXT,
            includePdfSource = true,
            renderPages = false
        )

        assertTrue("rendering was skipped", payload.images.isEmpty())
        assertNotNull("Mistral-only chain still needs the document", payload.pdfBase64)
    }

    @Test
    fun aMistralOnlyChainSendsOneDocumentAndNoPages() {
        val payload = factory.create(
            uris = listOf(writePdf(25)),
            mode = AiMode.EXTRACT_PDF_TEXT,
            includePdfSource = true,
            renderPages = false
        )
        val sent = ScanBudget.trimFor(AiProvider.MISTRAL, payload)

        assertEquals("a 25-page PDF must not truncate on Mistral", 0, sent.dropped)
        assertNotNull(sent.pdfBase64)
        assertTrue(sent.images.isEmpty())
    }

    @Test
    fun groqGetsFivePagesFromATwentyPagePdf() {
        val payload = factory.create(listOf(writePdf(25)), AiMode.EXTRACT_PDF_TEXT)
        val sent = ScanBudget.trimFor(AiProvider.GROQ, payload)

        assertEquals(5, sent.images.size)
        assertEquals(15, sent.dropped)
    }
}
