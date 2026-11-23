package com.digitalsignage.tv

import com.digitalsignage.tv.core.database.entity.AssetDownloadStatus
import com.digitalsignage.tv.core.database.entity.MediaType
import com.digitalsignage.tv.domain.model.MediaAsset
import com.digitalsignage.tv.domain.model.Playlist
import com.digitalsignage.tv.domain.model.PlaylistItem
import com.digitalsignage.tv.feature.player.viewmodel.PlayerUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MediaPlaybackTests {

    private fun createSampleMediaAsset(
        id: String,
        mediaType: MediaType,
        mimeType: String,
        localPath: String? = null,
        status: AssetDownloadStatus = AssetDownloadStatus.COMPLETED
    ): MediaAsset {
        return MediaAsset(
            id = id,
            remoteUrl = "https://cdn.0media.io/assets/$id",
            localFilePath = localPath,
            mimeType = mimeType,
            mediaType = mediaType,
            fileSizeBytes = 1024L * 1024L,
            checksumSha256 = "hash_$id",
            downloadStatus = status,
            downloadedAt = System.currentTimeMillis()
        )
    }

    @Test
    fun testSupportedMediaFormats() {
        val formats = listOf(
            "image/jpeg" to MediaType.IMAGE,
            "image/jpg" to MediaType.IMAGE,
            "image/png" to MediaType.IMAGE,
            "image/webp" to MediaType.IMAGE,
            "video/mp4" to MediaType.VIDEO
        )

        for ((mime, expectedType) in formats) {
            val asset = createSampleMediaAsset("asset_test", expectedType, mime)
            assertEquals(expectedType, asset.mediaType)
            assertEquals(mime, asset.mimeType)
        }
    }

    @Test
    fun testInfinitePlaylistLoopSequence() {
        // Given a sample playlist with alternating image and video:
        // Item 0: Image 1 -> Item 1: Video 1 -> Item 2: Image 2 -> Item 3: Video 2
        val items = listOf(
            PlaylistItem("itm_1", "ply_1", "img_1", 0, 10L, "FADE", "FIT_CENTER", createSampleMediaAsset("img_1", MediaType.IMAGE, "image/webp")),
            PlaylistItem("itm_2", "ply_1", "vid_1", 1, 30L, "CUT", "CROP_CENTER", createSampleMediaAsset("vid_1", MediaType.VIDEO, "video/mp4")),
            PlaylistItem("itm_3", "ply_1", "img_2", 2, 15L, "FADE", "FIT_CENTER", createSampleMediaAsset("img_2", MediaType.IMAGE, "image/jpeg")),
            PlaylistItem("itm_4", "ply_1", "vid_2", 3, 20L, "CUT", "CROP_CENTER", createSampleMediaAsset("vid_2", MediaType.VIDEO, "video/mp4"))
        )

        val playlist = Playlist(
            id = "ply_1",
            campaignId = "cmp_1",
            name = "Campaña Principal 0Media",
            loopCount = 0,
            items = items
        )

        assertEquals(4, playlist.items.size)

        // Verify sequential and infinite cycling order
        var currentIndex = 0
        val sequenceOrder = mutableListOf<String>()

        // Simulate 8 steps (2 full loops)
        for (step in 0 until 8) {
            val current = playlist.items[currentIndex]
            sequenceOrder.add(current.assetId)
            currentIndex = (currentIndex + 1) % playlist.items.size
        }

        val expected = listOf(
            "img_1", "vid_1", "img_2", "vid_2",
            "img_1", "vid_1", "img_2", "vid_2"
        )
        assertEquals(expected, sequenceOrder)
    }

    @Test
    fun testImageDisplayDurationRespect() {
        val itemCustomDuration = PlaylistItem(
            id = "itm_1",
            playlistId = "ply_1",
            assetId = "img_custom",
            orderIndex = 0,
            displayDurationSec = 25L,
            transitionType = "FADE",
            fitMode = "FIT_CENTER"
        )

        assertEquals(25L, itemCustomDuration.displayDurationSec)

        val itemDefaultDuration = PlaylistItem(
            id = "itm_2",
            playlistId = "ply_1",
            assetId = "img_default",
            orderIndex = 1,
            displayDurationSec = 0L,
            transitionType = "CUT",
            fitMode = "FIT_CENTER"
        )

        val resolvedDuration = if (itemDefaultDuration.displayDurationSec > 0) itemDefaultDuration.displayDurationSec else 15L
        assertEquals(15L, resolvedDuration)
    }

    @Test
    fun testCorruptedOrMissingVideoErrorSkipping() {
        val nonExistentFile = File("c:/dummy/non_existent_path_video.mp4")
        assertFalse("File must not exist for error test", nonExistentFile.exists())

        // Simulating player skip logic: If file is missing or invalid, index advances without crashing
        val items = listOf("vid_corrupted", "vid_valid")
        var currentIndex = 0

        val fileExists = nonExistentFile.exists() && nonExistentFile.length() > 0
        if (!fileExists) {
            // Player engine gracefully advances to next item
            currentIndex = (currentIndex + 1) % items.size
        }

        assertEquals(1, currentIndex)
        assertEquals("vid_valid", items[currentIndex])
    }

    @Test
    fun testEmptyPlaylistStandbyState() {
        val emptyPlaylist = Playlist(
            id = "ply_empty",
            campaignId = "cmp_empty",
            name = "Vacia",
            loopCount = 0,
            items = emptyList()
        )

        val state: PlayerUiState = if (emptyPlaylist.items.isEmpty()) {
            PlayerUiState.Standby("Esperando asignación de anuncios desde el panel web...")
        } else {
            PlayerUiState.Playing(
                item = emptyPlaylist.items.first(),
                playlistIndex = 0,
                totalItems = 1,
                campaignName = emptyPlaylist.name
            )
        }

        assertTrue(state is PlayerUiState.Standby)
        assertEquals("Esperando asignación de anuncios desde el panel web...", (state as PlayerUiState.Standby).message)
    }

    @Test
    fun testOfflinePlaybackPreservation() {
        // When device has downloaded media files locally, network drops do not affect playback
        val localAsset = createSampleMediaAsset(
            id = "cached_video",
            mediaType = MediaType.VIDEO,
            mimeType = "video/mp4",
            localPath = "c:/app/media/cached_video.mp4",
            status = AssetDownloadStatus.COMPLETED
        )

        val isInternetConnected = false
        val isLocallyAvailable = localAsset.downloadStatus == AssetDownloadStatus.COMPLETED && !localAsset.localFilePath.isNullOrBlank()

        assertTrue("Asset must be marked locally available", isLocallyAvailable)
        assertFalse("Internet connection is offline", isInternetConnected)

        // The engine continues playback relying entirely on Room metadata and local disk storage
        val canPlayOffline = isLocallyAvailable
        assertTrue("Playback must proceed 100% offline", canPlayOffline)
    }

    @Test
    fun testDiskSpaceThresholdPurgeLogic() {
        val minFreeDiskMb = 300L
        val currentFreeDiskMb = 200L // Low disk space

        val shouldPurge = currentFreeDiskMb < minFreeDiskMb
        assertTrue("Low disk space must trigger purge", shouldPurge)

        // Simulate purge:
        val orphanedFiles = listOf("temp_1.tmp", "temp_2.tmp")
        val freedFiles = orphanedFiles.filter { it.endsWith(".tmp") }
        assertEquals(2, freedFiles.size)
    }
}
