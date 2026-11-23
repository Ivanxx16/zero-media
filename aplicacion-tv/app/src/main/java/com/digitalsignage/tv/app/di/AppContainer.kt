package com.digitalsignage.tv.app.di

import android.content.Context
import com.digitalsignage.tv.core.common.dispatchers.CoroutineDispatchers
import com.digitalsignage.tv.core.common.dispatchers.DefaultCoroutineDispatchers
import com.digitalsignage.tv.core.common.util.AndroidHardwareIdProvider
import com.digitalsignage.tv.core.common.util.HardwareIdProvider
import com.digitalsignage.tv.core.common.util.LiveNetworkMonitor
import com.digitalsignage.tv.core.common.util.NetworkMonitor
import com.digitalsignage.tv.core.database.SignageDatabase
import com.digitalsignage.tv.core.network.NetworkClientProvider
import com.digitalsignage.tv.core.network.socket.SignageSocketManager
import com.digitalsignage.tv.core.network.socket.SocketManager
import com.digitalsignage.tv.core.security.EncryptedSecureTokenStorage
import com.digitalsignage.tv.core.security.KeystoreHelper
import com.digitalsignage.tv.core.security.SecureTokenStorage
import com.digitalsignage.tv.core.storage.AppPreferencesDataStore
import com.digitalsignage.tv.data.repository.DeviceRepository
import com.digitalsignage.tv.data.repository.DeviceRepositoryImpl
import com.digitalsignage.tv.data.repository.SyncRepository
import com.digitalsignage.tv.data.repository.SyncRepositoryImpl
import com.digitalsignage.tv.data.repository.TelemetryRepository
import com.digitalsignage.tv.data.repository.TelemetryRepositoryImpl
import com.digitalsignage.tv.domain.usecase.CheckPairingStatusUseCase
import com.digitalsignage.tv.domain.usecase.GetDeviceIdentityUseCase
import com.digitalsignage.tv.domain.usecase.InitiatePairingUseCase
import com.digitalsignage.tv.domain.usecase.TestBackendConnectionUseCase

interface AppContainer {
    val dispatchers: CoroutineDispatchers
    val hardwareIdProvider: HardwareIdProvider
    val networkMonitor: NetworkMonitor
    val keystoreHelper: KeystoreHelper
    val secureTokenStorage: SecureTokenStorage
    val preferencesDataStore: AppPreferencesDataStore
    val database: SignageDatabase
    val networkClientProvider: NetworkClientProvider
    val socketManager: SocketManager
    val downloadManager: com.digitalsignage.tv.core.downloader.DownloadManager
    val playerEngine: com.digitalsignage.tv.core.media.SignagePlayerEngine

    val deviceRepository: DeviceRepository
    val syncRepository: SyncRepository
    val telemetryRepository: TelemetryRepository

    val getDeviceIdentityUseCase: GetDeviceIdentityUseCase
    val initiatePairingUseCase: InitiatePairingUseCase
    val checkPairingStatusUseCase: CheckPairingStatusUseCase
    val testBackendConnectionUseCase: TestBackendConnectionUseCase
    val unpairDeviceUseCase: com.digitalsignage.tv.domain.usecase.UnpairDeviceUseCase
    val validateDeviceSessionUseCase: com.digitalsignage.tv.domain.usecase.ValidateDeviceSessionUseCase
    val getActivePlaylistUseCase: com.digitalsignage.tv.domain.usecase.GetActivePlaylistUseCase
    val recordProofOfPlayUseCase: com.digitalsignage.tv.domain.usecase.RecordProofOfPlayUseCase
    suspend fun seedDefaultMediaAndCampaign()
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    override val dispatchers: CoroutineDispatchers by lazy {
        DefaultCoroutineDispatchers()
    }

    override val hardwareIdProvider: HardwareIdProvider by lazy {
        AndroidHardwareIdProvider(context)
    }

    override val networkMonitor: NetworkMonitor by lazy {
        LiveNetworkMonitor(context)
    }

    override val keystoreHelper: KeystoreHelper by lazy {
        KeystoreHelper()
    }

    override val secureTokenStorage: SecureTokenStorage by lazy {
        EncryptedSecureTokenStorage(context, keystoreHelper)
    }

    override val preferencesDataStore: AppPreferencesDataStore by lazy {
        AppPreferencesDataStore(context)
    }

    override val database: SignageDatabase by lazy {
        SignageDatabase.getInstance(context)
    }

    override val networkClientProvider: NetworkClientProvider by lazy {
        NetworkClientProvider(hardwareIdProvider, secureTokenStorage)
    }

    override val socketManager: SocketManager by lazy {
        SignageSocketManager(hardwareIdProvider, secureTokenStorage)
    }

    override val downloadManager: com.digitalsignage.tv.core.downloader.DownloadManager by lazy {
        com.digitalsignage.tv.core.downloader.SignageDownloadManager(
            context = context,
            mediaAssetDao = database.mediaAssetDao(),
            okHttpClient = networkClientProvider.okHttpClient
        )
    }

    override val playerEngine: com.digitalsignage.tv.core.media.SignagePlayerEngine by lazy {
        com.digitalsignage.tv.core.media.ExoSignagePlayerEngine(context)
    }

    override val deviceRepository: DeviceRepository by lazy {
        DeviceRepositoryImpl(
            deviceDao = database.deviceDao(),
            hardwareIdProvider = hardwareIdProvider,
            tokenStorage = secureTokenStorage,
            preferencesDataStore = preferencesDataStore,
            networkClientProvider = networkClientProvider
        )
    }

    override val syncRepository: SyncRepository by lazy {
        SyncRepositoryImpl(
            campaignDao = database.campaignDao(),
            playlistDao = database.playlistDao(),
            mediaAssetDao = database.mediaAssetDao(),
            contentVersionDao = database.contentVersionDao(),
            preferencesDataStore = preferencesDataStore,
            networkClientProvider = networkClientProvider
        )
    }

    override val telemetryRepository: TelemetryRepository by lazy {
        TelemetryRepositoryImpl(
            telemetryDao = database.telemetryDao(),
            preferencesDataStore = preferencesDataStore,
            networkClientProvider = networkClientProvider
        )
    }

    override val getDeviceIdentityUseCase: GetDeviceIdentityUseCase by lazy {
        GetDeviceIdentityUseCase(deviceRepository)
    }

    override val initiatePairingUseCase: InitiatePairingUseCase by lazy {
        InitiatePairingUseCase(deviceRepository)
    }

    override val checkPairingStatusUseCase: CheckPairingStatusUseCase by lazy {
        CheckPairingStatusUseCase(deviceRepository)
    }

    override val testBackendConnectionUseCase: TestBackendConnectionUseCase by lazy {
        TestBackendConnectionUseCase(deviceRepository)
    }

    override val unpairDeviceUseCase: com.digitalsignage.tv.domain.usecase.UnpairDeviceUseCase by lazy {
        com.digitalsignage.tv.domain.usecase.UnpairDeviceUseCase(deviceRepository)
    }

    override val validateDeviceSessionUseCase: com.digitalsignage.tv.domain.usecase.ValidateDeviceSessionUseCase by lazy {
        com.digitalsignage.tv.domain.usecase.ValidateDeviceSessionUseCase(deviceRepository)
    }

    override val getActivePlaylistUseCase: com.digitalsignage.tv.domain.usecase.GetActivePlaylistUseCase by lazy {
        com.digitalsignage.tv.domain.usecase.GetActivePlaylistUseCase(
            campaignDao = database.campaignDao(),
            playlistDao = database.playlistDao(),
            mediaAssetDao = database.mediaAssetDao()
        )
    }

    override val recordProofOfPlayUseCase: com.digitalsignage.tv.domain.usecase.RecordProofOfPlayUseCase by lazy {
        com.digitalsignage.tv.domain.usecase.RecordProofOfPlayUseCase(telemetryRepository)
    }

    override suspend fun seedDefaultMediaAndCampaign(): Unit = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            // Purge old mock default campaigns so only real uploaded photos are shown
            val existing = database.campaignDao().getActiveCampaigns()
            val hasDefaultMock = existing.any { it.id == "camp_default_01" }
            if (hasDefaultMock) {
                database.campaignDao().clearAll()
                database.playlistDao().clearAll()
                database.mediaAssetDao().clearAll()
                com.digitalsignage.tv.core.common.logging.SignageLogger.i("AppContainer", "Purged mock demo campaigns and assets from database.")
            }
            try {
                val mediaDir = java.io.File(context.filesDir, "signage_media")
                if (mediaDir.exists()) {
                    listOf("promo_food.jpg", "promo_tech.jpg", "promo_retail.jpg").forEach { name ->
                        val f = java.io.File(mediaDir, name)
                        if (f.exists()) f.delete()
                    }
                }
            } catch (ignored: Exception) {}
        } catch (e: Exception) {
            com.digitalsignage.tv.core.common.logging.SignageLogger.e("AppContainer", "Failed to clean media assets", e)
        }
    }
}
