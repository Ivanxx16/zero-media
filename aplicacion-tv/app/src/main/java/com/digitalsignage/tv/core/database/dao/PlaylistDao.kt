package com.digitalsignage.tv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.digitalsignage.tv.core.database.entity.CampaignEntity
import com.digitalsignage.tv.core.database.entity.PlaylistEntity
import com.digitalsignage.tv.core.database.entity.PlaylistItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CampaignDao {

    @Query("SELECT * FROM campaigns WHERE isActive = 1 ORDER BY priority ASC")
    fun observeActiveCampaigns(): Flow<List<CampaignEntity>>

    @Query("SELECT * FROM campaigns WHERE isActive = 1 ORDER BY priority ASC")
    suspend fun getActiveCampaigns(): List<CampaignEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCampaigns(campaigns: List<CampaignEntity>)

    @Query("DELETE FROM campaigns WHERE id NOT IN (:validIds)")
    suspend fun deleteCampaignsNotIn(validIds: List<String>)

    @Query("DELETE FROM campaigns")
    suspend fun clearAll()
}

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists WHERE campaignId = :campaignId LIMIT 1")
    suspend fun getPlaylistForCampaign(campaignId: String): PlaylistEntity?

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    suspend fun getItemsForPlaylist(playlistId: String): List<PlaylistItemEntity>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    fun observeItemsForPlaylist(playlistId: String): Flow<List<PlaylistItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<PlaylistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistItems(items: List<PlaylistItemEntity>)

    @Query("DELETE FROM playlist_items")
    suspend fun clearAllItems()

    @Transaction
    suspend fun replacePlaylistWithItems(
        playlists: List<PlaylistEntity>,
        items: List<PlaylistItemEntity>
    ) {
        clearAllItems()
        clearAll()
        if (playlists.isNotEmpty()) {
            insertPlaylists(playlists)
        }
        if (items.isNotEmpty()) {
            insertPlaylistItems(items)
        }
    }

    @Query("DELETE FROM playlists")
    suspend fun clearAll()
}
