package com.digitalsignage.tv.core.network.socket

import com.digitalsignage.tv.core.common.logging.SignageLogger
import com.digitalsignage.tv.core.common.util.HardwareIdProvider
import com.digitalsignage.tv.core.security.SecureTokenStorage
import com.digitalsignage.tv.core.storage.ResolvedEnvironment
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

interface SocketManager {
    val connectionState: StateFlow<SocketConnectionState>
    val incomingEvents: SharedFlow<SocketIncomingEvent>

    fun connect(environment: ResolvedEnvironment, tempTokenOverride: String? = null)
    fun disconnect()
    fun emitStateChanged(state: String, assetId: String?, error: String? = null)
    fun emitCommandAck(commandId: String, success: Boolean, message: String)
    fun isConnected(): Boolean
}

class SignageSocketManager(
    private val hardwareIdProvider: HardwareIdProvider,
    private val tokenStorage: SecureTokenStorage
) : SocketManager {

    companion object {
        private const val TAG = "SignageSocketManager"
        private const val RECONNECTION_ATTEMPTS = 9999
        private const val RECONNECTION_DELAY_MS = 3000L
        private const val RECONNECTION_DELAY_MAX_MS = 30000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _connectionState = MutableStateFlow(SocketConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<SocketConnectionState> = _connectionState.asStateFlow()

    private val _incomingEvents = MutableSharedFlow<SocketIncomingEvent>(extraBufferCapacity = 64)
    override val incomingEvents: SharedFlow<SocketIncomingEvent> = _incomingEvents.asSharedFlow()

    private var socket: Socket? = null
    private val isConnectingOrConnected = AtomicBoolean(false)
    private var currentEnvironment: ResolvedEnvironment? = null

    @Synchronized
    override fun connect(environment: ResolvedEnvironment, tempTokenOverride: String?) {
        if (isConnectingOrConnected.get() && socket?.connected() == true && currentEnvironment == environment) {
            SignageLogger.d(TAG, "Socket already connected to ${environment.socketBaseUrl}, ignoring duplicate call")
            return
        }

        // Clean up previous socket if any
        disconnect()

        currentEnvironment = environment
        val targetUrl = environment.formatSocketUrl()
        SignageLogger.i(TAG, "Initiating Socket.IO connection to: $targetUrl")
        _connectionState.value = SocketConnectionState.CONNECTING

        try {
            val token = tempTokenOverride ?: tokenStorage.getDeviceToken() ?: ""
            val deviceInfo = hardwareIdProvider.getDeviceInfo()

            val authMap = mutableMapOf<String, String>(
                "hardwareFingerprint" to deviceInfo.fingerprint,
                "clientType" to "ANDROID_TV",
                "appVersion" to deviceInfo.appVersion
            )
            if (token.isNotEmpty()) {
                authMap["token"] = if (token.startsWith("Bearer ")) token else "Bearer $token"
            }

            val options = IO.Options().apply {
                transports = arrayOf("websocket")
                reconnection = true
                reconnectionAttempts = RECONNECTION_ATTEMPTS
                reconnectionDelay = RECONNECTION_DELAY_MS
                reconnectionDelayMax = RECONNECTION_DELAY_MAX_MS
                timeout = 20000
                auth = authMap
            }

            val newSocket = IO.socket(URI.create(targetUrl), options)
            setupSocketListeners(newSocket)
            socket = newSocket
            isConnectingOrConnected.set(true)
            newSocket.connect()
        } catch (e: Exception) {
            SignageLogger.e(TAG, "Failed to initialize Socket.IO instance", e)
            _connectionState.value = SocketConnectionState.ERROR
            isConnectingOrConnected.set(false)
        }
    }

    private fun setupSocketListeners(s: Socket) {
        s.on(Socket.EVENT_CONNECT) {
            SignageLogger.i(TAG, "Socket.IO connected successfully (id=${s.id()})")
            _connectionState.value = SocketConnectionState.CONNECTED
        }

        s.on(Socket.EVENT_DISCONNECT) { args ->
            val reason = args.firstOrNull()?.toString() ?: "unknown"
            SignageLogger.w(TAG, "Socket.IO disconnected. Reason: $reason")
            _connectionState.value = SocketConnectionState.DISCONNECTED
            isConnectingOrConnected.set(false)
        }

        s.on(Socket.EVENT_CONNECT_ERROR) { args ->
            val error = args.firstOrNull()?.toString() ?: "connection error"
            SignageLogger.e(TAG, "Socket.IO connection error: $error")
            _connectionState.value = SocketConnectionState.ERROR
        }

        s.on(SocketEvents.INCOMING_DEVICE_PAIRED) { args ->
            handleDevicePairedEvent(args)
        }

        s.on(SocketEvents.INCOMING_PLAYLIST_UPDATED) { args ->
            handlePlaylistUpdatedEvent(args)
        }

        s.on(SocketEvents.INCOMING_DEVICE_COMMAND) { args ->
            handleDeviceCommandEvent(args)
        }

        s.on(SocketEvents.INCOMING_OVERLAY_ALERT) { args ->
            handleOverlayAlertEvent(args)
        }

        s.on(SocketEvents.INCOMING_DEVICE_REVOKED) { args ->
            handleDeviceRevokedEvent(args)
        }

        s.on(SocketEvents.INCOMING_PING) {
            s.emit(SocketEvents.OUTGOING_PONG, JSONObject().put("timestamp", System.currentTimeMillis()))
        }
    }

    private fun handleDeviceRevokedEvent(args: Array<Any>) {
        try {
            val json = args.firstOrNull() as? JSONObject
            val reason = json?.optString("reason", "Revocado por el administrador") ?: "Revocado por el administrador"
            SignageLogger.w(TAG, "Received device:revoked event. Reason: $reason")
            scope.launch {
                _incomingEvents.emit(SocketIncomingEvent.DeviceRevoked(reason))
            }
        } catch (e: Exception) {
            SignageLogger.e(TAG, "Error handling device:revoked event", e)
        }
    }

    private fun handleDevicePairedEvent(args: Array<Any>) {
        try {
            val json = args.firstOrNull() as? JSONObject ?: return
            val deviceId = json.optString("deviceId", "")
            val permanentToken = json.optString("permanentToken", "")
            val tenantId = if (json.has("tenantId") && !json.isNull("tenantId")) json.optString("tenantId") else null

            SignageLogger.i(TAG, "Received device:paired event for deviceId=$deviceId")
            scope.launch {
                _incomingEvents.emit(SocketIncomingEvent.DevicePaired(deviceId, permanentToken, tenantId))
            }
        } catch (e: Exception) {
            SignageLogger.e(TAG, "Error parsing device:paired event", e)
        }
    }

    private fun handlePlaylistUpdatedEvent(args: Array<Any>) {
        try {
            val json = args.firstOrNull() as? JSONObject ?: return
            val manifestVersion = json.optString("manifestVersion", "unknown")
            val summary = if (json.has("changeSummary") && !json.isNull("changeSummary")) json.optString("changeSummary") else null

            SignageLogger.i(TAG, "Received playlist:updated event (version=$manifestVersion)")
            scope.launch {
                _incomingEvents.emit(SocketIncomingEvent.PlaylistUpdated(manifestVersion, summary))
            }
        } catch (e: Exception) {
            SignageLogger.e(TAG, "Error parsing playlist:updated event", e)
        }
    }

    private fun handleDeviceCommandEvent(args: Array<Any>) {
        try {
            val json = args.firstOrNull() as? JSONObject ?: return
            val commandId = json.optString("commandId", "")
            val action = json.optString("action", "")

            SignageLogger.i(TAG, "Received device:command action=$action (cmdId=$commandId)")
            scope.launch {
                _incomingEvents.emit(SocketIncomingEvent.DeviceCommand(commandId, action))
            }
        } catch (e: Exception) {
            SignageLogger.e(TAG, "Error parsing device:command event", e)
        }
    }

    private fun handleOverlayAlertEvent(args: Array<Any>) {
        try {
            val json = args.firstOrNull() as? JSONObject ?: return
            val alertId = json.optString("alertId", "")
            val text = json.optString("text", "")
            val duration = json.optInt("durationSec", 30)

            SignageLogger.i(TAG, "Received device:overlay_alert: $text")
            scope.launch {
                _incomingEvents.emit(SocketIncomingEvent.OverlayAlert(alertId, text, duration))
            }
        } catch (e: Exception) {
            SignageLogger.e(TAG, "Error parsing overlay alert event", e)
        }
    }

    @Synchronized
    override fun disconnect() {
        try {
            socket?.let {
                it.off()
                it.disconnect()
                it.close()
            }
            socket = null
            isConnectingOrConnected.set(false)
            _connectionState.value = SocketConnectionState.DISCONNECTED
            SignageLogger.i(TAG, "Socket.IO disconnected and cleaned up")
        } catch (e: Exception) {
            SignageLogger.e(TAG, "Error during socket disconnect", e)
        }
    }

    override fun emitStateChanged(state: String, assetId: String?, error: String?) {
        val s = socket ?: return
        if (!s.connected()) return

        try {
            val payload = JSONObject().apply {
                put("state", state)
                put("assetId", assetId ?: JSONObject.NULL)
                put("error", error ?: JSONObject.NULL)
                put("timestamp", System.currentTimeMillis())
            }
            s.emit(SocketEvents.OUTGOING_STATE_CHANGED, payload)
        } catch (e: Exception) {
            SignageLogger.e(TAG, "Failed to emit state changed", e)
        }
    }

    override fun emitCommandAck(commandId: String, success: Boolean, message: String) {
        val s = socket ?: return
        if (!s.connected()) return

        try {
            val payload = JSONObject().apply {
                put("commandId", commandId)
                put("success", success)
                put("message", message)
                put("timestamp", System.currentTimeMillis())
            }
            s.emit(SocketEvents.OUTGOING_COMMAND_ACK, payload)
        } catch (e: Exception) {
            SignageLogger.e(TAG, "Failed to emit command ack", e)
        }
    }

    override fun isConnected(): Boolean = socket?.connected() == true
}
