package com.digitalsignage.tv.domain.model

import com.digitalsignage.tv.core.database.entity.AssetDownloadStatus
import com.digitalsignage.tv.core.database.entity.MediaType
import com.digitalsignage.tv.core.database.entity.PairingStatus

data class DeviceIdentity(
    val hardwareFingerprint: String,
    val deviceId: String?,
    val tenantId: String?,
    val deviceName: String?,
    val pairingStatus: PairingStatus,
    val hasCredentials: Boolean
)

data class PairingSession(
    val pairingCode: String,
    val qrPayload: String,
    val tempToken: String,
    val expiresInSeconds: Long,
    val hardwareFingerprint: String
)

data class Campaign(
    val id: String,
    val name: String,
    val priority: Int,
    val validFrom: Long,
    val validTo: Long,
    val activeDaysOfWeek: List<Int>,
    val isActive: Boolean
)

data class Playlist(
    val id: String,
    val campaignId: String,
    val name: String,
    val loopCount: Int,
    val items: List<PlaylistItem>
)

data class PlaylistItem(
    val id: String,
    val playlistId: String,
    val assetId: String,
    val orderIndex: Int,
    val displayDurationSec: Long,
    val transitionType: String,
    val fitMode: String,
    val asset: MediaAsset? = null
)

data class MediaAsset(
    val id: String,
    val remoteUrl: String,
    val localFilePath: String?,
    val mimeType: String,
    val mediaType: MediaType,
    val fileSizeBytes: Long,
    val checksumSha256: String,
    val downloadStatus: AssetDownloadStatus,
    val downloadedAt: Long
)

data class PlaybackMetric(
    val id: Long = 0L,
    val playlistItemId: String,
    val assetId: String,
    val playedAt: Long,
    val renderedDurationMs: Long,
    val completedNormally: Boolean,
    val failureReason: String? = null,
    val isSynced: Boolean = false
)
