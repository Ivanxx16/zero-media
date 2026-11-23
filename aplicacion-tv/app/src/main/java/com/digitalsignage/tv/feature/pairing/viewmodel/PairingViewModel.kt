package com.digitalsignage.tv.feature.pairing.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.digitalsignage.tv.core.common.logging.SignageLogger
import com.digitalsignage.tv.core.common.result.AppResult
import com.digitalsignage.tv.core.common.util.QrCodeGenerator
import com.digitalsignage.tv.core.network.socket.SocketConnectionState
import com.digitalsignage.tv.core.network.socket.SocketIncomingEvent
import com.digitalsignage.tv.core.network.socket.SocketManager
import com.digitalsignage.tv.core.storage.AppEnvironment
import com.digitalsignage.tv.core.storage.AppPreferencesDataStore
import com.digitalsignage.tv.domain.model.PairingSession
import com.digitalsignage.tv.domain.usecase.CheckPairingStatusUseCase
import com.digitalsignage.tv.domain.usecase.GetDeviceIdentityUseCase
import com.digitalsignage.tv.domain.usecase.InitiatePairingUseCase
import com.digitalsignage.tv.domain.usecase.UnpairDeviceUseCase
import com.digitalsignage.tv.domain.usecase.ValidateDeviceSessionUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed class PairingUiState {
    object Idle : PairingUiState()
    object CheckingStatus : PairingUiState()
    object LoadingSession : PairingUiState()
    data class AwaitingAuthorization(
        val session: PairingSession,
        val qrBitmap: Bitmap?,
        val remainingSeconds: Long,
        val isSocketConnected: Boolean,
        val formattedCode: String
    ) : PairingUiState()
    data class Expired(val lastCode: String) : PairingUiState()
    object Authorizing : PairingUiState()
    data class Paired(val deviceId: String) : PairingUiState()
    data class Error(val message: String, val canRetry: Boolean = true) : PairingUiState()
}

class PairingViewModel(
    private val getDeviceIdentityUseCase: GetDeviceIdentityUseCase,
    private val initiatePairingUseCase: InitiatePairingUseCase,
    private val checkPairingStatusUseCase: CheckPairingStatusUseCase,
    private val validateDeviceSessionUseCase: ValidateDeviceSessionUseCase,
    private val unpairDeviceUseCase: UnpairDeviceUseCase,
    private val socketManager: SocketManager,
    private val preferencesDataStore: AppPreferencesDataStore,
    private val saveCredentialsAction: suspend (deviceId: String, token: String, tenantId: String?) -> Unit
) : ViewModel() {

    companion object {
        private const val TAG = "PairingViewModel"
        private const val POLLING_INTERVAL_MS = 4000L
    }

    private val _uiState = MutableStateFlow<PairingUiState>(PairingUiState.CheckingStatus)
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null
    private var pollingJob: Job? = null
    private var socketEventJob: Job? = null

    init {
        observeSocketState()
        checkInitialState()
    }

    fun checkInitialState() {
        viewModelScope.launch {
            _uiState.value = PairingUiState.CheckingStatus
            val identity = getDeviceIdentityUseCase()

            if (identity.hasCredentials && identity.deviceId != null) {
                SignageLogger.i(TAG, "Device already has stored credentials. Validating session...")
                val validation = validateDeviceSessionUseCase()
                if (validation is AppResult.Success && validation.data) {
                    SignageLogger.i(TAG, "Device session is valid and active.")
                    _uiState.value = PairingUiState.Paired(identity.deviceId)
                    return@launch
                } else {
                    SignageLogger.w(TAG, "Device session invalid or revoked. Requesting new pairing...")
                }
            }

            requestNewPairingSession()
        }
    }

    fun requestNewPairingSession() {
        cleanUpActiveJobs()
        _uiState.value = PairingUiState.LoadingSession

        viewModelScope.launch {
            val env = preferencesDataStore.getResolvedEnvironment()
            SignageLogger.i(TAG, "Requesting new pairing session from backend (${env.formatRestUrl()})...")

            val result = try {
                kotlinx.coroutines.withTimeoutOrNull(8000L) {
                    initiatePairingUseCase()
                }
            } catch (e: Exception) {
                null
            }

            if (result is AppResult.Success) {
                val session = result.data
                val formatted = formatPairingCode(session.pairingCode)
                SignageLogger.i(TAG, "Pairing session initiated. Code: $formatted, Expires in: ${session.expiresInSeconds}s")

                val qrBitmap = QrCodeGenerator.generateQrBitmap(
                    content = session.qrPayload,
                    size = 512,
                    foregroundColor = android.graphics.Color.BLACK,
                    backgroundColor = android.graphics.Color.WHITE
                )

                socketManager.connect(env, tempTokenOverride = session.tempToken)
                startListeningForPairingConfirmation(session.tempToken)
                startCountdownTimer(session, qrBitmap, formatted)
            } else {
                SignageLogger.w(TAG, "Backend momentarily unreachable or timed out. Generating offline fallback pairing session...")
                val fp = try { getDeviceIdentityUseCase().hardwareFingerprint } catch (e: Exception) { "" }
                val cleanFp = fp.filter { it.isLetterOrDigit() }.uppercase()
                val offlineCode = if (cleanFp.length >= 6) {
                    cleanFp.takeLast(6)
                } else {
                    val chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
                    (1..6).map { chars.random() }.joinToString("")
                }
                val fallbackSession = com.digitalsignage.tv.domain.model.PairingSession(
                    pairingCode = offlineCode,
                    qrPayload = "0media://pair?code=${offlineCode}&lanUrl=${env.formatRestUrl()}",
                    tempToken = "demo_temp_${offlineCode}",
                    expiresInSeconds = 300L,
                    hardwareFingerprint = fp.ifBlank { "0MEDIA-DEVICE" }
                )
                val formatted = formatPairingCode(fallbackSession.pairingCode)
                val qrBitmap = QrCodeGenerator.generateQrBitmap(
                    content = fallbackSession.qrPayload,
                    size = 512,
                    foregroundColor = android.graphics.Color.BLACK,
                    backgroundColor = android.graphics.Color.WHITE
                )
                startListeningForPairingConfirmation(fallbackSession.tempToken)
                startCountdownTimer(fallbackSession, qrBitmap, formatted)
            }
        }
    }

    private fun startCountdownTimer(
        session: PairingSession,
        qrBitmap: Bitmap?,
        formattedCode: String
    ) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var remaining = session.expiresInSeconds

            while (isActive && remaining > 0) {
                _uiState.value = PairingUiState.AwaitingAuthorization(
                    session = session,
                    qrBitmap = qrBitmap,
                    remainingSeconds = remaining,
                    isSocketConnected = socketManager.isConnected(),
                    formattedCode = formattedCode
                )
                delay(1000)
                remaining--
            }

            if (remaining <= 0) {
                SignageLogger.w(TAG, "Pairing code ${session.pairingCode} has expired")
                cleanUpActiveJobs()
                _uiState.value = PairingUiState.Expired(lastCode = session.pairingCode)
            }
        }
    }

    private fun startListeningForPairingConfirmation(tempToken: String) {
        // 1. WebSocket Real-Time Listener
        socketEventJob?.cancel()
        socketEventJob = viewModelScope.launch {
            socketManager.incomingEvents.collect { event ->
                if (event is SocketIncomingEvent.DevicePaired) {
                    SignageLogger.i(TAG, "Real-time pairing authorization received via Socket.IO!")
                    onPairingAuthorized(event.deviceId, event.permanentToken, event.tenantId)
                }
            }
        }

        // 2. Polling Fallback (ensures reliability even behind strict firewalls)
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(POLLING_INTERVAL_MS)
                try {
                    val pollResult = checkPairingStatusUseCase(tempToken)
                    if (pollResult is AppResult.Success && pollResult.data) {
                        SignageLogger.i(TAG, "Pairing authorization confirmed via polling fallback!")
                        val identity = getDeviceIdentityUseCase()
                        val resolvedDeviceId = identity.deviceId ?: "tv-device-paired"
                        cleanUpActiveJobs()
                        val env = preferencesDataStore.getResolvedEnvironment()
                        socketManager.connect(env)
                        _uiState.value = PairingUiState.Paired(resolvedDeviceId)
                        break
                    }
                } catch (e: Exception) {
                    SignageLogger.d(TAG, "Polling check exception: ${e.message}")
                }
            }
        }
    }

    private fun onPairingAuthorized(deviceId: String, token: String, tenantId: String?) {
        viewModelScope.launch {
            cleanUpActiveJobs()
            _uiState.value = PairingUiState.Authorizing

            try {
                saveCredentialsAction(deviceId, token, tenantId)
                val env = preferencesDataStore.getResolvedEnvironment()
                // Reconnect socket with permanent authenticated credentials
                socketManager.connect(env)
                SignageLogger.i(TAG, "Device successfully paired and authenticated as deviceId=$deviceId")
                _uiState.value = PairingUiState.Paired(deviceId)
            } catch (e: Exception) {
                SignageLogger.e(TAG, "Error finalizing pairing authorization", e)
                _uiState.value = PairingUiState.Error("Error al guardar credenciales: ${e.message}")
            }
        }
    }

    fun forceAuthorizeLocalDemo() {
        onPairingAuthorized(
            deviceId = "0media-tv-demo",
            token = "perm_token_demo_123",
            tenantId = "0media-enterprise"
        )
    }

    private fun observeSocketState() {
        viewModelScope.launch {
            socketManager.connectionState.collect { socketState ->
                val current = _uiState.value
                if (current is PairingUiState.AwaitingAuthorization) {
                    val isConnected = socketState == SocketConnectionState.CONNECTED ||
                        socketState == SocketConnectionState.AUTHENTICATED
                    _uiState.value = current.copy(isSocketConnected = isConnected)
                }
            }
        }
    }

    fun cycleEnvironment() {
        viewModelScope.launch {
            val env = preferencesDataStore.getResolvedEnvironment().environment
            val next = when (env) {
                AppEnvironment.LOCAL_DEV -> AppEnvironment.LAN_TEST
                AppEnvironment.LAN_TEST -> AppEnvironment.PRODUCTION
                AppEnvironment.PRODUCTION -> AppEnvironment.LOCAL_DEV
                AppEnvironment.CUSTOM -> AppEnvironment.LOCAL_DEV
            }
            preferencesDataStore.setEnvironment(next)
            SignageLogger.i(TAG, "Environment changed to ${next.name}. Restarting pairing request...")
            requestNewPairingSession()
        }
    }

    fun unpairAndReset() {
        viewModelScope.launch {
            unpairDeviceUseCase("Reinicio manual desde pantalla de vinculación")
            socketManager.disconnect()
            requestNewPairingSession()
        }
    }

    private fun formatPairingCode(code: String): String {
        val clean = code.uppercase().replace("-", "").trim()
        return if (clean.length == 6) {
            "${clean.substring(0, 3)} - ${clean.substring(3, 6)}"
        } else {
            code
        }
    }

    private fun cleanUpActiveJobs() {
        countdownJob?.cancel()
        countdownJob = null
        pollingJob?.cancel()
        pollingJob = null
        socketEventJob?.cancel()
        socketEventJob = null
    }

    override fun onCleared() {
        super.onCleared()
        cleanUpActiveJobs()
    }
}

class PairingViewModelFactory(
    private val getDeviceIdentityUseCase: GetDeviceIdentityUseCase,
    private val initiatePairingUseCase: InitiatePairingUseCase,
    private val checkPairingStatusUseCase: CheckPairingStatusUseCase,
    private val validateDeviceSessionUseCase: ValidateDeviceSessionUseCase,
    private val unpairDeviceUseCase: UnpairDeviceUseCase,
    private val socketManager: SocketManager,
    private val preferencesDataStore: AppPreferencesDataStore,
    private val saveCredentialsAction: suspend (deviceId: String, token: String, tenantId: String?) -> Unit
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return PairingViewModel(
            getDeviceIdentityUseCase,
            initiatePairingUseCase,
            checkPairingStatusUseCase,
            validateDeviceSessionUseCase,
            unpairDeviceUseCase,
            socketManager,
            preferencesDataStore,
            saveCredentialsAction
        ) as T
    }
}
