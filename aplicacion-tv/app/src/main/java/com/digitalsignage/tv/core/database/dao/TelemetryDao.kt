package com.digitalsignage.tv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.digitalsignage.tv.core.database.entity.ContentVersionEntity
import com.digitalsignage.tv.core.database.entity.PlaybackMetricEntity

@Dao
interface TelemetryDao {

    @Insert
    suspend fun insertMetric(metric: PlaybackMetricEntity): Long

    @Query("SELECT * FROM playback_metrics WHERE isSynced = 0 ORDER BY playedAt ASC LIMIT :limit")
    suspend fun getUnsyncedMetrics(limit: Int = 100): List<PlaybackMetricEntity>

    @Query("UPDATE playback_metrics SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markMetricsSynced(ids: List<Long>)

    @Query("DELETE FROM playback_metrics WHERE isSynced = 1 AND playedAt < :olderThanTimestamp")
    suspend fun purgeSyncedMetricsOlderThan(olderThanTimestamp: Long)

    @Query("SELECT COUNT(*) FROM playback_metrics WHERE isSynced = 0")
    suspend fun countUnsyncedMetrics(): Int
}

@Dao
interface ContentVersionDao {

    @Query("SELECT * FROM content_versions WHERE `key` = :key LIMIT 1")
    suspend fun getVersion(key: String): ContentVersionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setVersion(version: ContentVersionEntity)
}
