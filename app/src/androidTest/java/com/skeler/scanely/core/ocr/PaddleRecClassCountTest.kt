package com.skeler.scanely.core.ocr

import ai.onnxruntime.OrtEnvironment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.skeler.scanely.core.ocr.paddle.PaddleModelStore
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The CTC class count must equal blank + dict + space, or characters decode as neighbours. */
@RunWith(AndroidJUnit4::class)
class PaddleRecClassCountTest {

    @Test
    fun classCountMatchesCharset() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = PaddleModelStore(context, OkHttpClient())
        val env = OrtEnvironment.getEnvironment()
        val report = StringBuilder()

        val models = listOf(
            "ocr/rec_v5_arabic.onnx" to "ocr/dict_arabic.txt",
            "ocr/rec_v6_small.onnx" to "ocr/dict_v6.txt"
        )

        for ((model, dict) in models) {
            val session = env.createSession(store.asset(model))
            val outInfo = session.outputInfo.values.first()
            val shape = (outInfo.info as ai.onnxruntime.TensorInfo).shape
            val charset = PaddleModelStore.charset(store.asset(dict).decodeToString())
            report.appendLine("$model: classes=${shape.last()} charset=${charset.size}")
            session.close()

            assertEquals(
                "$model decodes against the wrong dictionary:\n$report",
                shape.last().toInt(),
                charset.size
            )
        }
        File(context.getExternalFilesDir(null), "rec_classes.txt").writeText(report.toString())
    }
}
