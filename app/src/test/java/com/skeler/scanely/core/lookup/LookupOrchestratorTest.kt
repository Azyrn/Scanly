package com.skeler.scanely.core.lookup

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LookupOrchestratorTest {
    @Test
    fun `all engine errors produce an error`() = runBlocking {
        val result = orchestrator(
            LookupResult.Error("one", Exception("offline")),
            LookupResult.Error("two", Exception("dns failure"))
        ).lookup(BARCODE)

        assertTrue("expected Error, got $result", result is LookupResult.Error)
    }

    @Test
    fun `all engines reporting not found produce not found`() = runBlocking {
        val result = orchestrator(
            LookupResult.NotFound("one"),
            LookupResult.NotFound("two")
        ).lookup(BARCODE)

        assertTrue("expected NotFound, got $result", result is LookupResult.NotFound)
    }

    @Test
    fun `an error mixed with not found produces not found`() = runBlocking {
        val result = orchestrator(
            LookupResult.Error("one", Exception("offline")),
            LookupResult.NotFound("two")
        ).lookup(BARCODE)

        assertTrue("expected NotFound, got $result", result is LookupResult.NotFound)
    }

    @Test
    fun `a found result wins`() = runBlocking {
        val found = LookupResult.Found(
            ProductInfo(
                barcode = BARCODE,
                source = "one",
                category = ProductCategory.GENERIC,
                name = "Product",
                brand = null,
                description = null,
                imageUrl = null
            ),
            source = "one"
        )

        val result = orchestrator(found, LookupResult.NotFound("two")).lookup(BARCODE)

        assertSame(found, result)
    }

    private fun orchestrator(vararg results: LookupResult): LookupOrchestrator {
        val engines = results.mapIndexed { index, result ->
            FakeEngine(name = "engine-$index", priority = index, result = result)
        }.toSet()
        return LookupOrchestrator(engines)
    }

    private class FakeEngine(
        override val name: String,
        override val priority: Int,
        private val result: LookupResult
    ) : LookupEngine {
        override val category = ProductCategory.GENERIC

        override fun supports(barcode: String) = true

        override suspend fun lookup(barcode: String) = result
    }

    private companion object {
        const val BARCODE = "1234567890123"
    }
}
