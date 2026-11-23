package com.digitalsignage.tv.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playback_metrics",
    indices = [
        Index(value = ["isSynced"]),
        Index(value = ["playedAt"])
    ]
)
data class PlaybackMetricEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val playlistItemId: String,
    val assetId: String,
    val playedAt: Long,
    val renderedDurationMs: Long,
    val completedNormally: Boolean = true,
    val failureReason: String? = null,
    val isSynced: Boolean = false
)

@Entity(tableName = "content_versions")
data class ContentVersionEntity(
    @PrimaryKey
    val key: String,
    val version: String,
    val updatedAt: Long
)
