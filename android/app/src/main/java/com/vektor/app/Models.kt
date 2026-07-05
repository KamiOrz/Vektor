package com.vektor.app

data class Channel(
    val id: String,
    val streamUrl: String,
    val title: String,
    val logoUrl: String?,
    val group: String,
    val index: Int
) {
    val displayNumber: String = index.toString().padStart(2, '0')
    val fallbackLogoText: String = title
        .split(" ")
        .filter { it.isNotBlank() }
        .take(3)
        .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
        .joinToString("")
        .ifBlank { displayNumber }
}

data class ScanHistoryItem(
    val id: String,
    val url: String,
    val title: String,
    val lastUsedAt: Long,
    val createdAt: Long
)

sealed interface AppScreen {
    data object Scan : AppScreen
    data class Playback(val sourceUrl: String) : AppScreen
}

sealed interface LoadingState {
    data object Idle : LoadingState
    data class Loading(val message: String) : LoadingState
    data class Failed(val message: String) : LoadingState
}
