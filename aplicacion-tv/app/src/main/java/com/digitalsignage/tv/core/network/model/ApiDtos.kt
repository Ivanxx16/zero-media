package com.digitalsignage.tv.core.network.model

import com.google.gson.annotations.SerializedName

data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: T? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("message") val message: String? = null
)

// Pairing DTOs
data class InitiatePairingRequest(
    @SerializedName("hardwareFingerprint") val hardwareFingerprint: String,
    @SerializedName("deviceInfo") val deviceInfo: DeviceInfoPayload
)

data class DeviceInfoPayload(
    @SerializedName("manufacturer") val manufacturer: String,
    @SerializedName("model") val model: String,
    @SerializedName("osVersion") val osVersion: String,
    @SerializedName("screenResolution") val screenResolution: String,
    @SerializedName("appVersion") val appVersion: String
)

data class InitiatePairingResponse(
    @SerializedName("pairingCode") val pairingCode: String,
    @SerializedName("qrPayload") val qrPayload: String,
    @SerializedName("tempToken") val tempToken: String,
    @SerializedName("expiresInSeconds") val expiresInSeconds: Long
)

data class PairingStatusResponse(
    @SerializedName("paired") val paired: Boolean,
    @SerializedName("deviceId") val deviceId: String? = null,
    @SerializedName("permanentToken") val permanentToken: String? = null,
    @SerializedName("tenantId") val tenantId: String? = null
)

// Sync DTOs
data class ManifestResponse(
    @SerializedName("manifestVersion") val manifestVersion: String,
    @SerializedName("serverTimestamp") val serverTimestamp: Long,
    @SerializedName("settings") val settings: DeviceRemoteSettingsDto? = null,
    @SerializedName("campaigns") val campaigns: List<CampaignDto> = emptyList(),
    @SerializedName("assets") val assets: List<MediaAssetDto> = emptyList()
)

data class DeviceRemoteSettingsDto(
    @SerializedName("orientation") val orientation: String? = "LANDSCAPE",
    @SerializedName("audioMuted") val audioMuted: Boolean = false,
    @SerializedName("heartbeatIntervalSeconds") val heartbeatIntervalSeconds: Int = 60
)

data class CampaignDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("priority") val priority: Int = 1,
    @SerializedName("validFrom") val validFrom: String? = null,
    @SerializedName("validTo") val validTo: String? = null,
    @SerializedName("activeDaysOfWeek") val activeDaysOfWeek: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
    @SerializedName("playlist") val playlist: PlaylistDto
)

data class PlaylistDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("items") val items: List<PlaylistItemDto> = emptyList()
)

data class PlaylistItemDto(
    @SerializedName("itemId") val itemId: String,
    @SerializedName("assetId") val assetId: String,
    @SerializedName("orderIndex") val orderIndex: Int,
    @SerializedName("displayDurationSec") val displayDurationSec: Long = 15L,
    @SerializedName("transition") val transition: String = "CUT",
    @SerializedName("fitMode") val fitMode: String = "FIT_CENTER"
)

data class MediaAssetDto(
    @SerializedName("id") val id: String,
    @SerializedName("url") val url: String,
    @SerializedName("mimeType") val mimeType: String,
    @SerializedName("fileSizeBytes") val fileSizeBytes: Long = 0L,
    @SerializedName("checksumSha256") val checksumSha256: String
)

// Telemetry DTOs
data class HeartbeatRequest(
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("status") val status: String,
    @SerializedName("currentPlaying") val currentPlaying: CurrentPlayingPayload? = null,
    @SerializedName("metrics") val metrics: DeviceHealthMetricsPayload
)

data class CurrentPlayingPayload(
    @SerializedName("campaignId") val campaignId: String?,
    @SerializedName("assetId") val assetId: String?,
    @SerializedName("loopCount") val loopCount: Int
)

data class DeviceHealthMetricsPayload(
    @SerializedName("cpuUsagePercent") val cpuUsagePercent: Float = 0f,
    @SerializedName("ramAvailableMb") val ramAvailableMb: Long = 0L,
    @SerializedName("freeInternalStorageMb") val freeInternalStorageMb: Long = 0L,
    @SerializedName("temperatureCelsius") val temperatureCelsius: Float = 0f,
    @SerializedName("networkType") val networkType: String = "NONE",
    @SerializedName("signalStrengthDbm") val signalStrengthDbm: Int = 0
)

data class HeartbeatResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("ack") val ack: Long
)

data class BatchMetricsRequest(
    @SerializedName("metrics") val metrics: List<MetricEntryPayload>
)

data class MetricEntryPayload(
    @SerializedName("assetId") val assetId: String,
    @SerializedName("playlistItemId") val playlistItemId: String,
    @SerializedName("playedAt") val playedAt: Long,
    @SerializedName("renderedDurationMs") val renderedDurationMs: Long,
    @SerializedName("completedNormally") val completedNormally: Boolean
)

data class BatchMetricsResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("processedCount") val processedCount: Int
)
