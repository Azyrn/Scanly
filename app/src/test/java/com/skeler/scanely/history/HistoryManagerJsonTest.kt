package com.skeler.scanely.history

import com.skeler.scanely.history.data.HistoryItem
import com.skeler.scanely.history.data.historyToJson
import com.skeler.scanely.history.data.parseHistoryJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryManagerJsonTest {

    @Test
    fun `truncated json is corrupt, not an empty history`() {
        val full = historyToJson(
            listOf(HistoryItem(text = "receipt", imageUri = "file:///img.jpg"))
        )
        val truncated = full.dropLast(10)

        assertNull(parseHistoryJson(truncated))
    }

    @Test
    fun `empty array is a valid empty history, not corrupt`() {
        val parsed = parseHistoryJson("[]")

        assertNotNull(parsed)
        assertEquals(emptyList<HistoryItem>(), parsed)
    }

    @Test
    fun `garbage is corrupt`() {
        assertNull(parseHistoryJson("not json at all"))
    }

    @Test
    fun `fifty items round-trip losslessly`() {
        val items = (1..50).map { i ->
            HistoryItem(
                id = "id-$i",
                text = "scan number $i\nwith a second line",
                imageUri = "file:///data/history_images/$i.jpg",
                timestamp = 1_700_000_000_000 + i
            )
        }

        assertEquals(items, parseHistoryJson(historyToJson(items)))
    }
}
