package com.vektor.app

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "vektor")

class PreferenceStore(private val context: Context) {
    private val lastUrlKey = stringPreferencesKey("last_valid_m3u_url")
    private val lastChannelIndexKey = intPreferencesKey("last_channel_index")
    private val scanHistoryKey = stringPreferencesKey("scan_history")
    private val hdrCompatibilityModeKey = booleanPreferencesKey("hdr_compatibility_mode")
    private val fullscreenFillModeKey = booleanPreferencesKey("fullscreen_fill_mode")

    suspend fun loadLastUrl(): String? = context.dataStore.data.first()[lastUrlKey]

    suspend fun saveLastUrl(url: String) {
        context.dataStore.edit { it[lastUrlKey] = url }
    }

    suspend fun clearLastUrl() {
        context.dataStore.edit { it.remove(lastUrlKey) }
    }

    suspend fun loadLastChannelIndex(): Int = context.dataStore.data.first()[lastChannelIndexKey] ?: 0

    suspend fun saveLastChannelIndex(index: Int) {
        context.dataStore.edit { it[lastChannelIndexKey] = index }
    }

    suspend fun loadHdrCompatibilityMode(): Boolean =
        context.dataStore.data.first()[hdrCompatibilityModeKey] ?: true

    suspend fun saveHdrCompatibilityMode(enabled: Boolean) {
        context.dataStore.edit { it[hdrCompatibilityModeKey] = enabled }
    }

    suspend fun loadFullscreenFillMode(): Boolean =
        context.dataStore.data.first()[fullscreenFillModeKey] ?: true

    suspend fun saveFullscreenFillMode(enabled: Boolean) {
        context.dataStore.edit { it[fullscreenFillModeKey] = enabled }
    }

    suspend fun loadScanHistory(): List<ScanHistoryItem> =
        decodeScanHistory(context.dataStore.data.first()[scanHistoryKey])

    suspend fun upsertScanHistory(url: String, title: String) {
        context.dataStore.edit { preferences ->
            val current = decodeScanHistory(preferences[scanHistoryKey])
            preferences[scanHistoryKey] = encodeScanHistory(com.vektor.app.upsertScanHistory(current, url, title))
        }
    }

    suspend fun deleteScanHistory(id: String) {
        context.dataStore.edit { preferences ->
            val current = decodeScanHistory(preferences[scanHistoryKey])
            val removed = current.firstOrNull { it.id == id }
            preferences[scanHistoryKey] = encodeScanHistory(current.filterNot { it.id == id })
            if (removed?.url == preferences[lastUrlKey]) {
                preferences.remove(lastUrlKey)
            }
        }
    }

    suspend fun clearScanHistory() {
        context.dataStore.edit { preferences ->
            val lastUrl = preferences[lastUrlKey]
            val containsLastUrl = lastUrl != null && decodeScanHistory(preferences[scanHistoryKey]).any { it.url == lastUrl }
            preferences.remove(scanHistoryKey)
            if (containsLastUrl) {
                preferences.remove(lastUrlKey)
            }
        }
    }
}
