package com.digitalsignage.tv

import com.digitalsignage.tv.core.common.result.AppException
import com.digitalsignage.tv.core.common.result.AppResult
import com.digitalsignage.tv.core.common.util.HashUtils
import com.digitalsignage.tv.core.database.entity.AssetDownloadStatus
import com.digitalsignage.tv.core.database.entity.DeviceEntity
import com.digitalsignage.tv.core.database.entity.MediaAssetEntity
import com.digitalsignage.tv.core.database.entity.MediaType
import com.digitalsignage.tv.core.database.entity.PairingStatus
import com.digitalsignage.tv.core.network.socket.SocketConnectionState
import com.digitalsignage.tv.core.network.socket.SocketEvents
import com.digitalsignage.tv.core.storage.AppEnvironment
import com.digitalsignage.tv.core.storage.ResolvedEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InfrastructureTests {

    @Test
    fun testSha256ChecksumCalculation() {
        val input = "0Media-Digital-Signage-2026"
        val hash = HashUtils.sha256(input)
        assertNotNull(hash)
        assertEquals(64, hash.length) // SHA-256 is 64 hex characters

        // Deterministic check
        val hash2 = HashUtils.sha256(input)
        assertEquals(hash, hash2)
    }

    @Test
    fun testAppResultSuccessAndErrorHandling() {
        val success: AppResult<String> = AppResult.Success("DEVICE_AUTHORIZED")
        assertTrue(success.isSuccess)
        assertFalse(success.isError)
        assertEquals("DEVICE_AUTHORIZED", success.getOrNull())

        val networkError = AppException.NetworkError(httpCode = 503, message = "Servicio no disponible")
        val failure: AppResult<String> = AppResult.Error(networkError)
        assertFalse(failure.isSuccess)
        assertTrue(failure.isError)
        assertEquals(null, failure.getOrNull())
        assertEquals(503, (failure as AppResult.Error).exception.let { (it as AppException.NetworkError).httpCode })
    }

    @Test
    fun testEnvironmentConfigUrls() {
        val dev = ResolvedEnvironment(
            environment = AppEnvironment.LOCAL_DEV,
            restBaseUrl = "http://10.0.2.2:3000",
            socketBaseUrl = "http://10.0.2.2:3000"
        )
        assertEquals("http://10.0.2.2:3000/", dev.formatRestUrl())
        assertEquals("http://10.0.2.2:3000", dev.formatSocketUrl())

        val prod = ResolvedEnvironment(
            environment = AppEnvironment.PRODUCTION,
            restBaseUrl = "https://api.digitalsignage.io/",
            socketBaseUrl = "https://socket.digitalsignage.io/"
        )
        assertEquals("https://api.digitalsignage.io/", prod.formatRestUrl())
        assertEquals("https://socket.digitalsignage.io", prod.formatSocketUrl())
    }

    @Test
    fun testRoomEntitiesCreationAndDefaults() {
        val device = DeviceEntity(
            hardwareFingerprint = "fingerprint_abc_123",
            deviceId = "dev_999",
            tenantId = "org_1",
            pairingStatus = PairingStatus.PAIRED
        )
        assertEquals("fingerprint_abc_123", device.hardwareFingerprint)
        assertEquals(PairingStatus.PAIRED, device.pairingStatus)

        val asset = MediaAssetEntity(
            id = "ast_video_1",
            remoteUrl = "https://cdn.example.com/ad.mp4",
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO,
            checksumSha256 = "dummy_hash_sha256",
            downloadStatus = AssetDownloadStatus.PENDING
        )
        assertEquals(AssetDownloadStatus.PENDING, asset.downloadStatus)
        assertEquals(MediaType.VIDEO, asset.mediaType)
    }

    @Test
    fun testSocketEventsAndStateTransitions() {
        assertEquals("device:paired", SocketEvents.INCOMING_DEVICE_PAIRED)
        assertEquals("playlist:updated", SocketEvents.INCOMING_PLAYLIST_UPDATED)
        assertEquals("device:command", SocketEvents.INCOMING_DEVICE_COMMAND)
        assertEquals("device:state_changed", SocketEvents.OUTGOING_STATE_CHANGED)

        val disconnected = SocketConnectionState.DISCONNECTED
        val connecting = SocketConnectionState.CONNECTING
        val connected = SocketConnectionState.CONNECTED

        assertTrue(disconnected != connected)
        assertTrue(connecting != connected)
    }
}
