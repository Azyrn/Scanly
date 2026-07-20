package com.skeler.scanely.core.ai.deepscan

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Pre-integration validation harness for PaddleOCR-VL-1.6 (.litertlm).
 * Not shipped: measures quality + latency + RSS against real documents pushed to
 * the app's external files dir. Writes a full report next to the inputs.
 */
@RunWith(AndroidJUnit4::class)
class PaddleVlHarnessTest {

    private data class Case(val label: String, val file: String, val prompt: String)

    @Test
    fun runOcrSuite() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(ctx.getExternalFilesDir(null), "deepscan")
        val model = File(dir, "PaddleOCR-VL-1.6.litertlm")
        assertTrue("model missing at ${model.absolutePath}", model.exists())

        val report = StringBuilder()
        fun log(line: String) {
            report.appendLine(line)
            File(dir, "report.txt").writeText(report.toString())
        }

        log("device RSS before init: ${rssMb()} MB")
        val engine = Engine(
            EngineConfig(
                modelPath = model.absolutePath,
                backend = Backend.CPU(),
                maxNumTokens = 4096,
                maxNumImages = 1,
                cacheDir = ctx.cacheDir.absolutePath
            )
        )
        val t0 = SystemClock.elapsedRealtime()
        engine.initialize()
        log("engine init: ${SystemClock.elapsedRealtime() - t0} ms, RSS after init: ${rssMb()} MB")

        val cases = listOf(
            Case("table-image OCR", "marks_table.jpg", "OCR:"),
            Case("table-image TableRec", "marks_table.jpg", "Table Recognition:"),
            Case("screen-photo OCR", "DOC_20260711_045538.jpg", "OCR:"),
            Case("dense-spec-pdf-page OCR", "spec-02.png", "OCR:")
        )

        try {
            for (case in cases) {
                val image = File(dir, case.file)
                assertTrue("image missing: ${case.file}", image.exists())

                val tCreate = SystemClock.elapsedRealtime()
                val conversation: Conversation = engine.createConversation(
                    ConversationConfig(
                        samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 1.0)
                    )
                )
                val createMs = SystemClock.elapsedRealtime() - tCreate

                val tInfer = SystemClock.elapsedRealtime()
                val result = try {
                    val message = conversation.sendMessage(
                        Contents.of(
                            Content.ImageFile(image.absolutePath),
                            Content.Text(case.prompt)
                        )
                    )
                    message.contents.contents
                        .filterIsInstance<Content.Text>()
                        .joinToString("") { it.text }
                } catch (e: Exception) {
                    "EXCEPTION: ${e::class.simpleName}: ${e.message}"
                } finally {
                    conversation.close()
                }
                val inferMs = SystemClock.elapsedRealtime() - tInfer

                log("")
                log("=== ${case.label} [${case.prompt}] ${case.file} ===")
                log("session create: $createMs ms, inference: $inferMs ms, RSS: ${rssMb()} MB")
                log("--- output (${result.length} chars) ---")
                log(result)
                log("--- end ---")
            }
        } finally {
            engine.close()
            log("")
            log("RSS after engine.close(): ${rssMb()} MB")
        }
    }

    private fun rssMb(): Long {
        val line = File("/proc/self/status").readLines().firstOrNull { it.startsWith("VmRSS:") }
        return line?.filter { it.isDigit() }?.toLongOrNull()?.div(1024) ?: -1
    }
}
