package com.digitalsignage.tv.domain.usecase

import com.digitalsignage.tv.core.common.logging.SignageLogger
import com.digitalsignage.tv.core.database.dao.CampaignDao
import com.digitalsignage.tv.core.database.dao.MediaAssetDao
import com.digitalsignage.tv.core.database.dao.PlaylistDao
import com.digitalsignage.tv.core.database.entity.AssetDownloadStatus
import com.digitalsignage.tv.data.repository.TelemetryRepository
import com.digitalsignage.tv.domain.model.MediaAsset
import com.digitalsignage.tv.domain.model.Playlist
import com.digitalsignage.tv.domain.model.PlaylistItem
import java.io.File
import java.util.Calendar

class GetActivePlaylistUseCase(
    private val campaignDao: CampaignDao,
    private val playlistDao: PlaylistDao,
    private val mediaAssetDao: MediaAssetDao
) {
    companion object {
        private const val TAG = "GetActivePlaylistUseCase"
    }

    suspend operator fun invoke(): Playlist? {
        val campaigns = campaignDao.getActiveCampaigns()
        if (campaigns.isEmpty()) {
            SignageLogger.d(TAG, "No active campaigns found in database")
            return null
        }

        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) // 1=Sunday, 2=Monday, ...

        // 1. Filter campaigns valid for current schedule
        val validCampaigns = campaigns.filter { campaign ->
            val withinDateRange = now in campaign.validFrom..campaign.validTo
            val activeToday = campaign.activeDaysOfWeek.isEmpty() || campaign.activeDaysOfWeek.contains(currentDayOfWeek)
            withinDateRange && activeToday
        }.sortedBy { it.priority }

        val activeCampaign = validCampaigns.firstOrNull() ?: campaigns.first()

        // 2. Fetch Playlist and Items
        val playlistEntity = playlistDao.getPlaylistForCampaign(activeCampaign.id) ?: return null
        val itemEntities = playlistDao.getItemsForPlaylist(playlistEntity.id)

        if (itemEntities.isEmpty()) {
            SignageLogger.w(TAG, "Playlist ${playlistEntity.id} has no items")
            return null
        }

        // 3. Resolve each item's Media Asset and verify local file exists
        val resolvedItems = mutableListOf<PlaylistItem>()

        for (item in itemEntities) {
            val assetEntity = mediaAssetDao.getAssetById(item.assetId)
            if (assetEntity != null) {
                // Verify local file exists on disk
                val hasLocalFile = !assetEntity.localFilePath.isNullOrBlank() &&
                    File(assetEntity.localFilePath).exists() &&
                    File(assetEntity.localFilePath).length() > 0

                val asset = MediaAsset(
                    id = assetEntity.id,
                    remoteUrl = assetEntity.remoteUrl,
                    localFilePath = assetEntity.localFilePath,
                    mimeType = assetEntity.mimeType,
                    mediaType = assetEntity.mediaType,
                    fileSizeBytes = assetEntity.fileSizeBytes,
                    checksumSha256 = assetEntity.checksumSha256,
                    downloadStatus = if (hasLocalFile) AssetDownloadStatus.COMPLETED else assetEntity.downloadStatus,
                    downloadedAt = assetEntity.downloadedAt
                )

                resolvedItems.add(
                    PlaylistItem(
                        id = item.id,
                        playlistId = item.playlistId,
                        assetId = item.assetId,
                        orderIndex = item.orderIndex,
                        displayDurationSec = item.displayDurationSec,
                        transitionType = item.transitionType,
                        fitMode = item.fitMode,
                        asset = asset
                    )
                )
            }
        }

        return Playlist(
            id = playlistEntity.id,
            campaignId = playlistEntity.campaignId,
            name = playlistEntity.name,
            loopCount = playlistEntity.loopCount,
            items = resolvedItems.sortedBy { it.orderIndex }
        )
    }
}

class RecordProofOfPlayUseCase(
    private val telemetryRepository: TelemetryRepository
) {
    suspend operator fun invoke(
        playlistItemId: String,
        assetId: String,
        durationMs: Long,
        completedNormally: Boolean,
        failureReason: String? = null
    ) {
        telemetryRepository.recordProofOfPlay(
            playlistItemId = playlistItemId,
            assetId = assetId,
            durationMs = durationMs,
            completedNormally = completedNormally,
            reason = failureReason
        )
    }
}
