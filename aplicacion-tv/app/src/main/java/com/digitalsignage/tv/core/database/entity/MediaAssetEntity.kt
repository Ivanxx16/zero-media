package com.digitalsignage.tv.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AssetDownloadStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CORRUPTED
}

enum class MediaType {
    IMAGE,
    VIDEO
}

@Entity(tableName = "media_assets")
data class MediaAssetEntity(
    @PrimaryKey
    val id: String,
    val remoteUrl: String,
    val localFilePath: String? = null,
    val mimeType: String,
    val mediaType: MediaType,
    val fileSizeBytes: Long = 0L,
    val checksumSha256: String,
    val downloadStatus: AssetDownloadStatus = AssetDownloadStatus.PENDING,
    val downloadedAt: Long = 0L,
    val lastAccessed: Long = 0L,
    val retryCount: Int = 0
)
