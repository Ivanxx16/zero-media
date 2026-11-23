package com.digitalsignage.tv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.digitalsignage.tv.core.database.entity.AssetDownloadStatus
import com.digitalsignage.tv.core.database.entity.MediaAssetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaAssetDao {

    @Query("SELECT * FROM media_assets WHERE id = :id LIMIT 1")
    suspend fun getAssetById(id: String): MediaAssetEntity?

    @Query("SELECT * FROM media_assets")
    suspend fun getAllAssets(): List<MediaAssetEntity>

    @Query("SELECT * FROM media_assets")
    fun observeAllAssets(): Flow<List<MediaAssetEntity>>

    @Query("SELECT * FROM media_assets WHERE downloadStatus = :status")
    suspend fun getAssetsByStatus(status: AssetDownloadStatus): List<MediaAssetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAssets(assets: List<MediaAssetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(asset: MediaAssetEntity)

    @Update
    suspend fun update(asset: MediaAssetEntity)

    @Query("UPDATE media_assets SET downloadStatus = :status, localFilePath = :localPath, downloadedAt = :timestamp WHERE id = :id")
    suspend fun markCompleted(id: String, localPath: String, timestamp: Long, status: AssetDownloadStatus = AssetDownloadStatus.COMPLETED)

    @Query("UPDATE media_assets SET downloadStatus = :status, retryCount = retryCount + 1 WHERE id = :id")
    suspend fun markFailed(id: String, status: AssetDownloadStatus = AssetDownloadStatus.FAILED)

    @Query("UPDATE media_assets SET lastAccessed = :timestamp WHERE id = :id")
    suspend fun updateLastAccessed(id: String, timestamp: Long)

    @Query("SELECT * FROM media_assets WHERE id NOT IN (SELECT DISTINCT assetId FROM playlist_items) AND localFilePath IS NOT NULL ORDER BY lastAccessed ASC")
    suspend fun getOrphanedDownloadedAssets(): List<MediaAssetEntity>

    @Query("DELETE FROM media_assets WHERE id = :id")
    suspend fun deleteAsset(id: String)

    @Query("DELETE FROM media_assets WHERE id NOT IN (:validIds)")
    suspend fun deleteAssetsNotIn(validIds: List<String>)

    @Query("DELETE FROM media_assets")
    suspend fun clearAll()
}
