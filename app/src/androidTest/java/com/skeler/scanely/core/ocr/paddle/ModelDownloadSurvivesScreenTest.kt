package com.skeler.scanely.core.ocr.paddle

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The settings screen's ViewModel dies when the user leaves. A download it started must not
 * die with it: the model has to land on disk and the state has to say so when they come back.
 */
@RunWith(AndroidJUnit4::class)
class ModelDownloadSurvivesScreenTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = PaddleModelStore(context, OkHttpClient())

    @After
    fun tearDown() = store.deleteTable()

    @Test
    fun tableDownloadFinishesAfterTheScreenIsClosed() = runBlocking {
        store.deleteTable()

        // Stands in for viewModelScope: alive while the screen is, gone when it isn't.
        val screenScope = CoroutineScope(SupervisorJob())
        screenScope.launch { store.downloadTableAsync() }

        withTimeout(30_000) {
            while (store.tableState.value !is PackState.Downloading) delay(50)
        }
        screenScope.cancel()

        val settled = withTimeout(180_000) {
            store.tableState.first { it is PackState.Installed || it is PackState.Failed }
        }

        assertTrue("Download ended as $settled", settled is PackState.Installed)
        assertTrue("Model is not on disk", store.hasTable())
    }
}
