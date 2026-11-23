package com.digitalsignage.tv.data.repository

import com.digitalsignage.tv.core.common.logging.SignageLogger
import com.digitalsignage.tv.core.common.result.AppException
import com.digitalsignage.tv.core.common.result.AppResult
import com.digitalsignage.tv.core.database.dao.CampaignDao
import com.digitalsignage.tv.core.database.dao.ContentVersionDao
import com.digitalsignage.tv.core.database.dao.MediaAssetDao
import com.digitalsignage.tv.core.database.dao.PlaylistDao
import com.digitalsignage.tv.core.database.dao.TelemetryDao
import com.digitalsignage.tv.core.database.entity.AssetDownloadStatus
import com.digitalsignage.tv.core.database.entity.CampaignEntity
import com.digitalsignage.tv.core.database.entity.ContentVersionEntity
import com.digitalsignage.tv.core.database.entity.MediaAssetEntity
import com.digitalsignage.tv.core.database.entity.MediaType
import com.digitalsignage.tv.core.database.entity.PlaybackMetricEntity
import com.digitalsignage.tv.core.database.entity.PlaylistEntity
import com.digitalsignage.tv.core.database.entity.PlaylistItemEntity
import com.digitalsignage.tv.core.network.NetworkClientProvider
import com.digitalsignage.tv.core.network.model.BatchMetricsRequest
import com.digitalsignage.tv.core.network.model.DeviceHealthMetricsPayload
import com.digitalsignage.tv.core.network.model.HeartbeatRequest
import com.digitalsignage.tv.core.network.model.MetricEntryPayload
import com.digitalsignage.tv.core.storage.AppPreferencesDataStore
import kotlinx.coroutines.flow.Flow

interface SyncRepository {
    suspend fun syncManifest(): AppResult<Boolean>
    fun observeCampaigns(): Flow<List<CampaignEntity>>
    fun observeAssets(): Flow<List<MediaAssetEntity>>
}

class SyncRepositoryImpl(
    private val campaignDao: CampaignDao,
    private val playlistDao: PlaylistDao,
    private val mediaAssetDao: MediaAssetDao,
    private val contentVersionDao: ContentVersionDao,
    private val preferencesDataStore: AppPreferencesDataStore,
    private val networkClientProvider: NetworkClientProvider
) : SyncRepository {

    companion object {
        private const val TAG = "SyncRepository"
        private const val MANIFEST_VERSION_KEY = "manifest_version"
    }

    override suspend fun syncManifest(): AppResult<Boolean> {
        return try {
            val environment = preferencesDataStore.getResolvedEnvironment()
            val api = networkClientProvider.createSyncApi(environment)

            val currentVersion = contentVersionDao.getVersion(MANIFEST_VERSION_KEY)?.version
            val response = api.getManifest(etag = currentVersion)

            if (response.code() == 304) {
                SignageLogger.i(TAG, "Manifest is up to date (HTTP 304 Not Modified)")
                return AppResult.Success(false)
            }

            if (response.isSuccessful && response.body()?.data != null) {
                val manifest = response.body()!!.data!!
                SignageLogger.i(TAG, "New manifest version: ${manifest.manifestVersion} with ${manifest.campaigns.size} campaigns, ${manifest.assets.size} assets")

                // 1. Process Assets
                val newAssets = manifest.assets.map { dto ->
                    val existing = mediaAssetDao.getAssetById(dto.id)
                    val mediaType = if (dto.mimeType.startsWith("video")) MediaType.VIDEO else MediaType.IMAGE
                    val status = if (existing != null && existing.downloadStatus == AssetDownloadStatus.COMPLETED && existing.checksumSha256 == dto.checksumSha256) {
                        AssetDownloadStatus.COMPLETED
                    } else {
                        AssetDownloadStatus.PENDING
                    }

                    MediaAssetEntity(
                        id = dto.id,
                        remoteUrl = dto.url,
                        localFilePath = existing?.localFilePath,
                        mimeType = dto.mimeType,
                        mediaType = mediaType,
                        fileSizeBytes = dto.fileSizeBytes,
                        checksumSha256 = dto.checksumSha256,
                        downloadStatus = status,
                        downloadedAt = existing?.downloadedAt ?: 0L,
                        lastAccessed = System.currentTimeMillis()
                    )
                }
                mediaAssetDao.insertOrUpdateAssets(newAssets)

                // 2. Process Campaigns, Playlists and Items
                val campaignEntities = mutableListOf<CampaignEntity>()
                val playlistEntities = mutableListOf<PlaylistEntity>()
                val itemEntities = mutableListOf<PlaylistItemEntity>()

                manifest.campaigns.forEach { cDto ->
                    campaignEntities.add(
                        CampaignEntity(
                            id = cDto.id,
                            name = cDto.name,
                            priority = cDto.priority,
                            activeDaysOfWeek = cDto.activeDaysOfWeek
                        )
                    )

                    val pDto = cDto.playlist
                    playlistEntities.add(
                        PlaylistEntity(
                            id = pDto.id,
                            campaignId = cDto.id,
                            name = pDto.name
                        )
                    )

                    pDto.items.forEach { iDto ->
                        itemEntities.add(
                            PlaylistItemEntity(
                                id = iDto.itemId,
                                playlistId = pDto.id,
                                assetId = iDto.assetId,
                                orderIndex = iDto.orderIndex,
                                displayDurationSec = iDto.displayDurationSec,
                                transitionType = iDto.transition,
                                fitMode = iDto.fitMode
                            )
                        )
                    }
                }

                if (campaignEntities.isNotEmpty()) {
                    campaignDao.insertCampaigns(campaignEntities)
                    campaignDao.deleteCampaignsNotIn(campaignEntities.map { it.id })
                } else {
                    campaignDao.clearAll()
                }
                playlistDao.replacePlaylistWithItems(playlistEntities, itemEntities)
                if (manifest.assets.isNotEmpty()) {
                    mediaAssetDao.deleteAssetsNotIn(manifest.assets.map { it.id })
                } else {
                    mediaAssetDao.clearAll()
                }

                // 3. Update version & timestamp
                contentVersionDao.setVersion(
                    ContentVersionEntity(
                        key = MANIFEST_VERSION_KEY,
                        version = manifest.manifestVersion,
                        updatedAt = manifest.serverTimestamp
                    )
                )
                preferencesDataStore.updateLastSyncTimestamp(System.currentTimeMillis())

                AppResult.Success(true)
            } else {
                AppResult.Error(AppException.ServerError(response.code(), response.message()))
            }
        } catch (e: Exception) {
            SignageLogger.e(TAG, "Manifest sync failed", e)
            AppResult.Error(AppException.NetworkError(message = e.localizedMessage ?: "Sync network failure", cause = e))
        }
    }

    override fun observeCampaigns(): Flow<List<CampaignEntity>> = campaignDao.observeActiveCampaigns()
    override fun observeAssets(): Flow<List<MediaAssetEntity>> = mediaAssetDao.observeAllAssets()
}

interface TelemetryRepository {
    suspend fun recordProofOfPlay(playlistItemId: String, assetId: String, durationMs: Long, completedNormally: Boolean, reason: String? = null)
    suspend fun flushMetrics(): AppResult<Int>
    suspend fun sendHeartbeat(status: String, connectionType: String): AppResult<Boolean>
}

class TelemetryRepositoryImpl(
    private val telemetryDao: TelemetryDao,
    private val preferencesDataStore: AppPreferencesDataStore,
    private val networkClientProvider: NetworkClientProvider
) : TelemetryRepository {

    override suspend fun recordProofOfPlay(
        playlistItemId: String,
        assetId: String,
        durationMs: Long,
        completedNormally: Boolean,
        reason: String?
    ) {
        telemetryDao.insertMetric(
            PlaybackMetricEntity(
                playlistItemId = playlistItemId,
                assetId = assetId,
                playedAt = System.currentTimeMillis(),
                renderedDurationMs = durationMs,
                completedNormally = completedNormally,
                failureReason = reason,
                isSynced = false
            )
        )
    }

    override suspend fun flushMetrics(): AppResult<Int> {
        val unsynced = telemetryDao.getUnsyncedMetrics(50)
        if (unsynced.isEmpty()) return AppResult.Success(0)

        return try {
            val environment = preferencesDataStore.getResolvedEnvironment()
            val api = networkClientProvider.createTelemetryApi(environment)

            val payload = BatchMetricsRequest(
                metrics = unsynced.map {
                    MetricEntryPayload(
                        assetId = it.assetId,
                        playlistItemId = it.playlistItemId,
                        playedAt = it.playedAt,
                        renderedDurationMs = it.renderedDurationMs,
                        completedNormally = it.completedNormally
                    )
                }
            )

            val response = api.sendBatchMetrics(payload)
            if (response.isSuccessful && response.body()?.data?.success == true) {
                val ids = unsynced.map { it.id }
                telemetryDao.markMetricsSynced(ids)
                AppResult.Success(ids.size)
            } else {
                AppResult.Error(AppException.ServerError(response.code(), response.message()))
            }
        } catch (e: Exception) {
            AppResult.Error(AppException.NetworkError(message = e.localizedMessage ?: "Flush telemetry failure", cause = e))
        }
    }

    override suspend fun sendHeartbeat(status: String, connectionType: String): AppResult<Boolean> {
        return try {
            val environment = preferencesDataStore.getResolvedEnvironment()
            val api = networkClientProvider.createTelemetryApi(environment)

            val request = HeartbeatRequest(
                timestamp = System.currentTimeMillis(),
                status = status,
                metrics = DeviceHealthMetricsPayload(
                    networkType = connectionType,
                    freeInternalStorageMb = 2048L
                )
            )

            val response = api.sendHeartbeat(request)
            if (response.isSuccessful) {
                AppResult.Success(true)
            } else {
                AppResult.Error(AppException.ServerError(response.code(), response.message()))
            }
        } catch (e: Exception) {
            AppResult.Error(AppException.NetworkError(message = e.localizedMessage ?: "Heartbeat failure", cause = e))
        }
    }
}
