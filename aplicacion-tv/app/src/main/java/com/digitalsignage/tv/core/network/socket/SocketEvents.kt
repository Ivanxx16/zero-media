package com.digitalsignage.tv.core.network.socket

object SocketEvents {

    // Handshake and lifecycle
    const val EVENT_CONNECT = "connect"
    const val EVENT_DISCONNECT = "disconnect"
    const val EVENT_CONNECT_ERROR = "connect_error"

    // Server -> Client Events
    const val INCOMING_DEVICE_PAIRED = "device:paired"
    const val INCOMING_PLAYLIST_UPDATED = "playlist:updated"
    const val INCOMING_DEVICE_COMMAND = "device:command"
    const val INCOMING_DEVICE_REVOKED = "device:revoked"
    const val INCOMING_OVERLAY_ALERT = "device:overlay_alert"
    const val INCOMING_PING = "device:ping"

    // Client -> Server Events
    const val OUTGOING_STATE_CHANGED = "device:state_changed"
    const val OUTGOING_COMMAND_ACK = "device:command_ack"
    const val OUTGOING_PONG = "device:pong"
}

enum class SocketConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    AUTHENTICATED,
    ERROR
}

sealed class SocketIncomingEvent {
    data class DevicePaired(
        val deviceId: String,
        val permanentToken: String,
        val tenantId: String?
    ) : SocketIncomingEvent()

    data class DeviceRevoked(
        val reason: String? = null
    ) : SocketIncomingEvent()

    data class PlaylistUpdated(
        val manifestVersion: String,
        val changeSummary: String?
    ) : SocketIncomingEvent()

    data class DeviceCommand(
        val commandId: String,
        val action: String,
        val params: Map<String, Any>? = null
    ) : SocketIncomingEvent()

    data class OverlayAlert(
        val alertId: String,
        val text: String,
        val durationSec: Int
    ) : SocketIncomingEvent()
}
