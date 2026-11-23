package com.digitalsignage.tv.feature.player.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digitalsignage.tv.core.common.logging.SignageLogger
import com.digitalsignage.tv.core.common.result.AppResult
import com.digitalsignage.tv.core.database.entity.MediaType
import com.digitalsignage.tv.core.downloader.DownloadManager
import com.digitalsignage.tv.core.media.PlayerEngineListener
import com.digitalsignage.tv.core.media.SignagePlayerEngine
import com.digitalsignage.tv.core.network.socket.SocketIncomingEvent
import com.digitalsignage.tv.core.network.socket.SocketManager
import com.digitalsignage.tv.core.storage.AppPreferencesDataStore
import com.digitalsignage.tv.data.repository.SyncRepository
import com.digitalsignage.tv.data.repository.TelemetryRepository
import com.digitalsignage.tv.domain.model.Playlist
import com.digitalsignage.tv.domain.model.PlaylistItem
import com.digitalsignage.tv.domain.usecase.GetActivePlaylistUseCase
import com.digitalsignage.tv.domain.usecase.RecordProofOfPlayUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

sealed class PlayerUiState {
    object Loading : PlayerUiState()
    data class Standby(val message: String) : PlayerUiState()
    data class Playing(
        val item: PlaylistItem,
        val playlistIndex: Int,
        val totalItems: Int,
        val campaignName: String,
        val overlayAlert: String? = null,
        val isMuted: Boolean = false,
        val isDiagnosticsVisible: Boolean = false
    ) : PlayerUiState()
}

class PlayerViewModel(
    val playerEngine: SignagePlayerEngine,
    private val getActivePlaylistUseCase: GetActivePlaylistUseCase,
    private val recordProofOfPlayUseCase: RecordProofOfPlayUseCase,
    private val syncRepository: SyncRepository,
    private val downloadManager: DownloadManager,
    private val telemetryRepository: TelemetryRepository,
    private val socketManager: SocketManager,
    private val preferencesDataStore: AppPreferencesDataStore
) : ViewModel(), PlayerEngineListener {

    companion object {
        private const val TAG = "PlayerViewModel"
        private const val DEFAULT_IMAGE_DURATION_SEC = 15L
        private const val HEARTBEAT_INTERVAL_MS = 10000L
    }

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var currentPlaylist: Playlist? = null
    private var readyItems: List<PlaylistItem> = emptyList()
    private var currentIndex: Int = 0

    private var imageTimerJob: Job? = null
    private var socketEventsJob: Job? = null
    private var heartbeatJob: Job? = null
    private var overlayAlertTimerJob: Job? = null

    private var currentItemStartTime: Long = 0L
    private var activeOverlayAlert: String? = null
    private var isDiagnosticsVisible: Boolean = false
    private var isMuted: Boolean = false

    init {
        playerEngine.setListener(this)
        observePreferences()
        startSocketEventsListener()
        startHeartbeatLoop()
        initializePlayback()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            preferencesDataStore.settingsFlow.collect { settings ->
                isMuted = settings.isAudioMuted
                playerEngine.setMuted(isMuted)
            }
        }
    }

    fun initializePlayback() {
        viewModelScope.launch {
            _uiState.value = PlayerUiState.Loading
            SignageLogger.i(TAG, "Initializing playback engine. Loading playlist...")

            // Initial sync if first launch or refresh
            loadPlaylistAndStart()
        }
    }

    private suspend fun loadPlaylistAndStart() {
        val playlist = getActivePlaylistUseCase()
        currentPlaylist = playlist

        if (playlist == null || playlist.items.isEmpty()) {
            SignageLogger.w(TAG, "No playlist assigned. Triggering background manifest sync...")
            _uiState.value = PlayerUiState.Standby("Esperando asignación de anuncios desde el panel web...")
            triggerBackgroundSyncAndDownload()
            return
        }

        // Filter items that have local files already downloaded and ready
        readyItems = playlist.items.filter { item ->
            val path = item.asset?.localFilePath
            path != null && File(path).exists() && File(path).length() > 0
        }

        if (readyItems.isEmpty()) {
            SignageLogger.w(TAG, "Playlist has ${playlist.items.size} items but 0 downloaded. Triggering download...")
            _uiState.value = PlayerUiState.Standby("Descargando contenido multimedia asignado...")
            triggerBackgroundSyncAndDownload()
            return
        }

        SignageLogger.i(TAG, "Loaded playlist with ${readyItems.size} ready assets. Starting playback loop...")
        currentIndex = 0
        playCurrentItem()

        // Always check backend for newest updates on launch
        triggerBackgroundSyncAndDownload()
    }

    private fun playCurrentItem() {
        imageTimerJob?.cancel()
        imageTimerJob = null

        if (readyItems.isEmpty()) {
            _uiState.value = PlayerUiState.Standby("No hay contenidos listos para reproducir.")
            return
        }

        if (currentIndex !in readyItems.indices) {
            currentIndex = 0
        }

        val item = readyItems[currentIndex]
        val asset = item.asset ?: run {
            advanceToNextItem()
            return
        }

        currentItemStartTime = System.currentTimeMillis()
        val localFile = File(asset.localFilePath ?: "")

        SignageLogger.i(TAG, "Playing [${currentIndex + 1}/${readyItems.size}]: ${asset.id} (${asset.mediaType})")

        _uiState.value = PlayerUiState.Playing(
            item = item,
            playlistIndex = currentIndex,
            totalItems = readyItems.size,
            campaignName = currentPlaylist?.name ?: "Campaña Activa",
            overlayAlert = activeOverlayAlert,
            isMuted = isMuted,
            isDiagnosticsVisible = isDiagnosticsVisible
        )

        socketManager.emitStateChanged(
            state = "PLAYING",
            assetId = asset.id
        )

        when (asset.mediaType) {
            MediaType.VIDEO -> {
                playerEngine.playVideo(asset.id, localFile)
            }
            MediaType.IMAGE -> {
                // Ensure video player is stopped during image presentation
                playerEngine.stop()
                val durationSec = if (item.displayDurationSec > 0) item.displayDurationSec else DEFAULT_IMAGE_DURATION_SEC
                SignageLogger.i(TAG, "Displaying image ${item.id} for $durationSec seconds")

                imageTimerJob = viewModelScope.launch {
                    delay(durationSec * 1000)
                    recordProofOfPlay(item, completedNormally = true)
                    advanceToNextItem()
                }
            }
        }
    }

    fun advanceToNextItem() {
        if (readyItems.isEmpty()) return

        currentIndex = (currentIndex + 1) % readyItems.size
        playCurrentItem()
    }

    // PlayerEngineListener Callbacks
    override fun onVideoCompleted(assetId: String) {
        val currentItem = readyItems.getOrNull(currentIndex)
        if (currentItem != null && currentItem.asset?.id == assetId) {
            recordProofOfPlay(currentItem, completedNormally = true)
        }
        advanceToNextItem()
    }

    override fun onVideoError(assetId: String, error: androidx.media3.common.PlaybackException) {
        SignageLogger.e(TAG, "Video playback error on $assetId. Skipping to next item...", error)
        val currentItem = readyItems.getOrNull(currentIndex)
        if (currentItem != null && currentItem.asset?.id == assetId) {
            recordProofOfPlay(currentItem, completedNormally = false, reason = error.errorCodeName)
        }
        // Advance without blank screen
        advanceToNextItem()
    }

    override fun onPlaybackStarted(assetId: String) {
        SignageLogger.d(TAG, "ExoPlayer rendering video frame for $assetId")
    }

    private fun recordProofOfPlay(item: PlaylistItem, completedNormally: Boolean, reason: String? = null) {
        val durationMs = System.currentTimeMillis() - currentItemStartTime
        viewModelScope.launch {
            recordProofOfPlayUseCase(
                playlistItemId = item.id,
                assetId = item.assetId,
                durationMs = durationMs,
                completedNormally = completedNormally,
                failureReason = reason
            )
        }
    }

    fun triggerBackgroundSyncAndDownload() {
        viewModelScope.launch {
            try {
                SignageLogger.i(TAG, "Executing background sync with backend...")
                syncRepository.syncManifest()
                val downloaded = downloadManager.downloadPendingAssets()
                SignageLogger.i(TAG, "Background sync and download finished. Downloaded: $downloaded")

                // Reload playlist
                val newPlaylist = getActivePlaylistUseCase()
                val newReady = newPlaylist?.items?.filter {
                    val p = it.asset?.localFilePath
                    p != null && File(p).exists() && File(p).length() > 0
                } ?: emptyList()

                val currentReadyIds = readyItems.map { "${it.id}_${it.asset?.id}" }
                val newReadyIds = newReady.map { "${it.id}_${it.asset?.id}" }
                val currentSignatures = readyItems.map { "${it.id}_${it.asset?.id}_${it.displayDurationSec}" }
                val newSignatures = newReady.map { "${it.id}_${it.asset?.id}_${it.displayDurationSec}" }

                if (newReady.isNotEmpty()) {
                    if (newReadyIds != currentReadyIds || _uiState.value is PlayerUiState.Standby) {
                        SignageLogger.i(TAG, "Playlist change detected! Switching to new assets: $newReadyIds")
                        currentPlaylist = newPlaylist
                        readyItems = newReady
                        currentIndex = 0
                        playCurrentItem()
                    } else if (newSignatures != currentSignatures) {
                        SignageLogger.i(TAG, "Ad duration change detected! Updating playlist in place: $newSignatures")
                        currentPlaylist = newPlaylist
                        readyItems = newReady

                        // Si actualmente se está proyectando una imagen, ajustar dinámicamente el tiempo restante
                        val currentPlayingItem = readyItems.getOrNull(currentIndex)
                        if (currentPlayingItem?.asset?.mediaType == MediaType.IMAGE) {
                            val durationSec = if (currentPlayingItem.displayDurationSec > 0) currentPlayingItem.displayDurationSec else DEFAULT_IMAGE_DURATION_SEC
                            val elapsedMs = System.currentTimeMillis() - currentItemStartTime
                            val totalMs = durationSec * 1000L
                            if (elapsedMs >= totalMs) {
                                SignageLogger.i(TAG, "New duration ($durationSec s) already elapsed ($elapsedMs ms). Advancing immediately.")
                                advanceToNextItem()
                            } else {
                                val remainingMs = totalMs - elapsedMs
                                SignageLogger.i(TAG, "Adjusting image timer: new duration=${durationSec}s, remaining=${remainingMs}ms")
                                imageTimerJob?.cancel()
                                imageTimerJob = viewModelScope.launch {
                                    delay(remainingMs)
                                    recordProofOfPlay(currentPlayingItem, completedNormally = true)
                                    advanceToNextItem()
                                }
                            }
                        }
                    }
                } else if (newReady.isEmpty() && _uiState.value !is PlayerUiState.Standby) {
                    currentPlaylist = null
                    readyItems = emptyList()
                    imageTimerJob?.cancel()
                    playerEngine.pause()
                    _uiState.value = PlayerUiState.Standby("Esperando asignación de anuncios desde el panel web...")
                }
            } catch (e: Exception) {
                SignageLogger.e(TAG, "Background sync exception", e)
            }
        }
    }

    private fun startSocketEventsListener() {
        socketEventsJob?.cancel()
        socketEventsJob = viewModelScope.launch {
            socketManager.incomingEvents.collect { event ->
                when (event) {
                    is SocketIncomingEvent.PlaylistUpdated -> {
                        SignageLogger.i(TAG, "Real-time playlist update received via Socket.IO: version=${event.manifestVersion}")
                        // Download in background without interrupting playback
                        triggerBackgroundSyncAndDownload()
                    }
                    is SocketIncomingEvent.OverlayAlert -> {
                        SignageLogger.i(TAG, "Overlay alert received: ${event.text}")
                        showOverlayAlert(event.text, event.durationSec)
                    }
                    is SocketIncomingEvent.DeviceCommand -> {
                        when (event.action) {
                            "RELOAD_PLAYER" -> initializePlayback()
                            "CLEAR_CACHE" -> downloadManager.purgeOrphanedAssets()
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun showOverlayAlert(text: String, durationSec: Int) {
        overlayAlertTimerJob?.cancel()
        activeOverlayAlert = text
        updateCurrentPlayingState()

        overlayAlertTimerJob = viewModelScope.launch {
            delay(durationSec * 1000L)
            activeOverlayAlert = null
            updateCurrentPlayingState()
        }
    }

    fun toggleDiagnostics() {
        isDiagnosticsVisible = !isDiagnosticsVisible
        updateCurrentPlayingState()
    }

    private fun updateCurrentPlayingState() {
        val current = _uiState.value
        if (current is PlayerUiState.Playing) {
            _uiState.value = current.copy(
                overlayAlert = activeOverlayAlert,
                isDiagnosticsVisible = isDiagnosticsVisible,
                isMuted = isMuted
            )
        }
    }

    private fun startHeartbeatLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                try {
                    val stateString = if (_uiState.value is PlayerUiState.Playing) "PLAYING" else "STANDBY"
                    telemetryRepository.sendHeartbeat(
                        status = stateString,
                        connectionType = "ACTIVE"
                    )
                    telemetryRepository.flushMetrics()
                    triggerBackgroundSyncAndDownload()
                } catch (e: Exception) {
                    SignageLogger.d(TAG, "Heartbeat error: ${e.message}")
                }
            }
        }
    }

    fun onPause() {
        playerEngine.pause()
    }

    fun onResume() {
        playerEngine.resume()
    }

    override fun onCleared() {
        super.onCleared()
        imageTimerJob?.cancel()
        socketEventsJob?.cancel()
        heartbeatJob?.cancel()
        overlayAlertTimerJob?.cancel()
        playerEngine.release()
    }
}
