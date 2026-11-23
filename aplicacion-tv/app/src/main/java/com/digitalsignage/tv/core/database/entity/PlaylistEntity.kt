package com.digitalsignage.tv.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlists",
    foreignKeys = [
        ForeignKey(
            entity = CampaignEntity::class,
            parentColumns = ["id"],
            childColumns = ["campaignId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["campaignId"])]
)
data class PlaylistEntity(
    @PrimaryKey
    val id: String,
    val campaignId: String,
    val name: String,
    val loopCount: Int = 0,
    val totalDurationMs: Long = 0L
)

@Entity(
    tableName = "playlist_items",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["assetId"])
    ]
)
data class PlaylistItemEntity(
    @PrimaryKey
    val id: String,
    val playlistId: String,
    val assetId: String,
    val orderIndex: Int,
    val displayDurationSec: Long = 15L,
    val transitionType: String = "CUT", // CUT, FADE, SLIDE
    val fitMode: String = "FIT_CENTER" // FIT_CENTER, CROP_CENTER
)
