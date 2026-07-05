package com.vektor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ScanHistoryCodecTest {
    @Test
    fun keepsTenItemsAndMovesDuplicatesToTop() {
        var items = emptyList<ScanHistoryItem>()
        repeat(11) { index ->
            items = upsertScanHistory(
                items = items,
                url = "https://example.com/list-$index.m3u",
                title = "List $index",
                now = index.toLong()
            )
        }

        assertEquals(10, items.size)
        assertEquals("https://example.com/list-10.m3u", items.first().url)
        assertFalse(items.any { it.url == "https://example.com/list-0.m3u" })

        items = upsertScanHistory(
            items = items,
            url = "https://example.com/list-5.m3u",
            title = "Updated",
            now = 99
        )

        assertEquals(10, items.size)
        assertEquals("https://example.com/list-5.m3u", items.first().url)
        assertEquals("Updated", items.first().title)
        assertEquals(99, items.first().lastUsedAt)
    }

    @Test
    fun encodesAndDecodesHistory() {
        val items = listOf(
            ScanHistoryItem(
                id = "https://example.com/list.m3u",
                url = "https://example.com/list.m3u",
                title = "List",
                lastUsedAt = 20,
                createdAt = 10
            )
        )

        assertEquals(items, decodeScanHistory(encodeScanHistory(items)))
    }

    @Test
    fun corruptPayloadFallsBackToEmptyList() {
        assertEquals(emptyList<ScanHistoryItem>(), decodeScanHistory("not-json"))
    }
}
