package com.vektor.app

import android.app.Application
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val logTag = "VektorMainViewModel"
    private val repository = M3uRepository()
    private val preferences = PreferenceStore(application)
    val playerController = PlayerController(application)
    private var scanLocked = false

    private val _screen = MutableStateFlow<AppScreen>(AppScreen.Scan)
    val screen: StateFlow<AppScreen> = _screen

    private val _loadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    val loadingState: StateFlow<LoadingState> = _loadingState

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels

    private val _selectedChannel = MutableStateFlow<Channel?>(null)
    val selectedChannel: StateFlow<Channel?> = _selectedChannel

    private val _selectedGroup = MutableStateFlow("All")
    val selectedGroup: StateFlow<String> = _selectedGroup

    private val _scanHistory = MutableStateFlow<List<ScanHistoryItem>>(emptyList())
    val scanHistory: StateFlow<List<ScanHistoryItem>> = _scanHistory

    fun bootstrap() {
        viewModelScope.launch {
            _scanHistory.value = preferences.loadScanHistory()
            val url = preferences.loadLastUrl()
            if (url == null) {
                _screen.value = AppScreen.Scan
            } else {
                load(url, "Restoring playlist...")
            }
        }
    }

    fun handleScannedText(raw: String) {
        val url = raw.trim()
        if (!M3uRepository.isHttpUrl(url)) {
            _loadingState.value = LoadingState.Failed("Scan a valid http or https M3U link.")
            return
        }
        if (scanLocked || _loadingState.value is LoadingState.Loading) return
        scanLocked = true
        _loadingState.value = LoadingState.Loading("Preparing playlist...")
        vibrateSafely()
        viewModelScope.launch { load(url, "Loading playlist...") }
    }

    fun loadClipboard(raw: String?) {
        if (raw.isNullOrBlank()) {
            _loadingState.value = LoadingState.Failed("Clipboard does not contain a playlist URL.")
            return
        }
        handleScannedText(raw)
    }

    fun setSelectedGroup(group: String) {
        _selectedGroup.value = group
    }

    fun select(channel: Channel) {
        _selectedChannel.value = channel
        playerController.load(channel)
        viewModelScope.launch { preferences.saveLastChannelIndex(channel.index) }
    }

    fun nextChannel() {
        val list = _channels.value
        val current = _selectedChannel.value ?: return
        val index = list.indexOf(current)
        if (index >= 0) select(list[(index + 1) % list.size])
    }

    fun previousChannel() {
        val list = _channels.value
        val current = _selectedChannel.value ?: return
        val index = list.indexOf(current)
        if (index >= 0) select(list[(index - 1 + list.size) % list.size])
    }

    fun resetToScan() {
        playerController.pause()
        scanLocked = false
        _channels.value = emptyList()
        _selectedChannel.value = null
        _selectedGroup.value = "All"
        _screen.value = AppScreen.Scan
    }

    fun loadFromHistory(item: ScanHistoryItem) {
        if (_loadingState.value is LoadingState.Loading) return
        viewModelScope.launch { load(item.url, "Loading history...", clearLastOnFailure = false) }
    }

    fun deleteHistoryItem(item: ScanHistoryItem) {
        viewModelScope.launch {
            preferences.deleteScanHistory(item.id)
            _scanHistory.value = preferences.loadScanHistory()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            preferences.clearScanHistory()
            _scanHistory.value = emptyList()
        }
    }

    private suspend fun load(url: String, message: String, clearLastOnFailure: Boolean = true) {
        _loadingState.value = LoadingState.Loading(message)
        val result = withContext(Dispatchers.IO) { repository.validateAndParse(url) }
        result
            .onSuccess { parsed ->
                _channels.value = parsed
                _selectedGroup.value = "All"
                _screen.value = AppScreen.Playback(url)
                preferences.saveLastUrl(url)
                preferences.upsertScanHistory(url, historyTitle(url))
                _scanHistory.value = preferences.loadScanHistory()
                val lastIndex = preferences.loadLastChannelIndex()
                select(parsed.firstOrNull { it.index == lastIndex } ?: parsed.first())
                _loadingState.value = LoadingState.Idle
            }
            .onFailure {
                if (clearLastOnFailure) {
                    preferences.clearLastUrl()
                }
                scanLocked = false
                _channels.value = emptyList()
                _selectedChannel.value = null
                _screen.value = AppScreen.Scan
                _loadingState.value = LoadingState.Failed(it.message ?: "Unable to load playlist.")
            }
    }

    private fun vibrateSafely() {
        runCatching {
            val context = getApplication<Application>()
            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }
            vibrator.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
        }.onFailure {
            Log.w(logTag, "Unable to vibrate after QR scan.", it)
        }
    }

    private fun historyTitle(url: String): String {
        val uri = Uri.parse(url)
        val name = uri.lastPathSegment
            ?.substringBeforeLast('.', missingDelimiterValue = uri.lastPathSegment ?: "")
            ?.takeIf { it.isNotBlank() }
        return name ?: uri.host ?: url
    }

    override fun onCleared() {
        playerController.release()
        super.onCleared()
    }
}
