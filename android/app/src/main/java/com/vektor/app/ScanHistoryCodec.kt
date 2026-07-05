package com.vektor.app

import org.json.JSONArray
import org.json.JSONObject

private const val HISTORY_LIMIT = 10

fun decodeScanHistory(payload: String?): List<ScanHistoryItem> {
    if (payload.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(payload)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    ScanHistoryItem(
                        id = item.getString("id"),
                        url = item.getString("url"),
                        title = item.getString("title"),
                        lastUsedAt = item.getLong("lastUsedAt"),
                        createdAt = item.getLong("createdAt")
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

fun encodeScanHistory(items: List<ScanHistoryItem>): String {
    val array = JSONArray()
    items.take(HISTORY_LIMIT).forEach { item ->
        array.put(
            JSONObject()
                .put("id", item.id)
                .put("url", item.url)
                .put("title", item.title)
                .put("lastUsedAt", item.lastUsedAt)
                .put("createdAt", item.createdAt)
        )
    }
    return array.toString()
}

fun upsertScanHistory(
    items: List<ScanHistoryItem>,
    url: String,
    title: String,
    now: Long = System.currentTimeMillis()
): List<ScanHistoryItem> {
    val normalized = url.trim()
    val existing = items.firstOrNull { it.url == normalized }
    return listOf(
        ScanHistoryItem(
            id = existing?.id ?: normalized,
            url = normalized,
            title = title,
            lastUsedAt = now,
            createdAt = existing?.createdAt ?: now
        )
    ) + items.filterNot { it.url == normalized }
        .take(HISTORY_LIMIT - 1)
}
