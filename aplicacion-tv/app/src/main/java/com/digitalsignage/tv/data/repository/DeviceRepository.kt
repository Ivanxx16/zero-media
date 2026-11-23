package com.digitalsignage.tv.data.repository

import com.digitalsignage.tv.core.common.logging.SignageLogger
import com.digitalsignage.tv.core.common.result.AppException
import com.digitalsignage.tv.core.common.result.AppResult
import com.digitalsignage.tv.core.common.util.HardwareIdProvider
import com.digitalsignage.tv.core.database.dao.DeviceDao
import com.digitalsignage.tv.core.database.entity.DeviceEntity
import com.digitalsignage.tv.core.database.entity.PairingStatus
import com.digitalsignage.tv.core.network.NetworkClientProvider
import com.digitalsignage.tv.core.network.model.DeviceInfoPayload
import com.digitalsignage.tv.core.network.model.InitiatePairingRequest
import com.digitalsignage.tv.core.security.SecureTokenStorage
import com.digitalsignage.tv.core.storage.AppPreferencesDataStore
import com.digitalsignage.tv.domain.model.DeviceIdentity
import com.digitalsignage.tv.domain.model.PairingSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface DeviceRepository {
    suspend fun getDeviceIdentity(): DeviceIdentity
    fun observeDeviceIdentity(): Flow<DeviceIdentity>
    suspend fun initiatePairing(): AppResult<PairingSession>
    suspend fun pollPairingStatus(tempToken: String): AppResult<Boolean>
    suspend fun saveDeviceCredentials(deviceId: String, permanentToken: String, tenantId: String?)
    suspend fun testBackendConnection(): AppResult<Long>
    suspend fun unpairDevice(reason: String = "Desvinculación manual")
    suspend fun validateDeviceSession(): AppResult<Boolean>
    suspend fun clearDevice()
}

class DeviceRepositoryImpl(
    private val deviceDao: DeviceDao,
    private val hardwareIdProvider: HardwareIdProvider,
    private val tokenStorage: SecureTokenStorage,
    private val preferencesDataStore: AppPreferencesDataStore,
    private val networkClientProvider: NetworkClientProvider
) : DeviceRepository {

    companion object {
        private const val TAG = "DeviceRepository"
    }

    override suspend fun getDeviceIdentity(): DeviceIdentity {
        val fingerprint = hardwareIdProvider.getHardwareFingerprint()
        val localDevice = deviceDao.getDeviceByFingerprint(fingerprint)
        val hasTokens = tokenStorage.hasValidCredentials()

        val status = when {
            hasTokens -> PairingStatus.PAIRED
            localDevice != null -> localDevice.pairingStatus
            else -> PairingStatus.UNPAIRED
        }

        return DeviceIdentity(
            hardwareFingerprint = fingerprint,
            deviceId = tokenStorage.getDeviceId() ?: localDevice?.deviceId,
            tenantId = tokenStorage.getTenantId() ?: localDevice?.tenantId,
            deviceName = localDevice?.deviceName ?: hardwareIdProvider.getDeviceInfo().model,
            pairingStatus = status,
            hasCredentials = hasTokens
        )
    }

    override fun observeDeviceIdentity(): Flow<DeviceIdentity> {
        val fingerprint = hardwareIdProvider.getHardwareFingerprint()
        return deviceDao.observeCurrentDevice().map { localDevice ->
            val hasTokens = tokenStorage.hasValidCredentials()
            val status = when {
                hasTokens -> PairingStatus.PAIRED
                localDevice != null -> localDevice.pairingStatus
                else -> PairingStatus.UNPAIRED
            }

            DeviceIdentity(
                hardwareFingerprint = fingerprint,
                deviceId = tokenStorage.getDeviceId() ?: localDevice?.deviceId,
                tenantId = tokenStorage.getTenantId() ?: localDevice?.tenantId,
                deviceName = localDevice?.deviceName ?: hardwareIdProvider.getDeviceInfo().model,
                pairingStatus = status,
                hasCredentials = hasTokens
            )
        }
    }

    override suspend fun initiatePairing(): AppResult<PairingSession> {
        return try {
            val environment = preferencesDataStore.getResolvedEnvironment()
            val api = networkClientProvider.createDeviceApi(environment)
            val info = hardwareIdProvider.getDeviceInfo()

            val request = InitiatePairingRequest(
                hardwareFingerprint = info.fingerprint,
                deviceInfo = DeviceInfoPayload(
                    manufacturer = info.manufacturer,
                    model = info.model,
                    osVersion = info.osVersion,
                    screenResolution = "1920x1080",
                    appVersion = info.appVersion
                )
            )

            val response = api.initiatePairing(request)
            if (response.isSuccessful && response.body()?.data != null) {
                val data = response.body()!!.data!!
                // Update local status in Room
                deviceDao.insertOrUpdate(
                    DeviceEntity(
                        hardwareFingerprint = info.fingerprint,
                        pairingStatus = PairingStatus.PAIRING_IN_PROGRESS
                    )
                )

                AppResult.Success(
                    PairingSession(
                        pairingCode = data.pairingCode,
                        qrPayload = data.qrPayload,
                        tempToken = data.tempToken,
                        expiresInSeconds = data.expiresInSeconds,
                        hardwareFingerprint = info.fingerprint
                    )
                )
            } else {
                val errorMsg = response.errorBody()?.string() ?: response.message()
                SignageLogger.e(TAG, "Initiate pairing failed: HTTP ${response.code()} $errorMsg")
                AppResult.Error(AppException.ServerError(response.code(), errorMsg))
            }
        } catch (e: Exception) {
            SignageLogger.e(TAG, "Exception during initiatePairing", e)
            AppResult.Error(AppException.NetworkError(message = e.localizedMessage ?: "Network connection failure", cause = e))
        }
    }

    override suspend fun pollPairingStatus(tempToken: String): AppResult<Boolean> {
        return try {
            val environment = preferencesDataStore.getResolvedEnvironment()
            val api = networkClientProvider.createDeviceApi(environment)
            val fingerprint = hardwareIdProvider.getHardwareFingerprint()

            val response = api.pollPairingStatus(tempToken, fingerprint)
            if (response.isSuccessful && response.body()?.data != null) {
                val data = response.body()!!.data!!
                if (data.paired && !data.permanentToken.isNullOrBlank() && !data.deviceId.isNullOrBlank()) {
                    saveDeviceCredentials(data.deviceId, data.permanentToken, data.tenantId)
                    AppResult.Success(true)
                } else {
                    AppResult.Success(false)
                }
            } else {
                AppResult.Error(AppException.ServerError(response.code(), response.message()))
            }
        } catch (e: Exception) {
            AppResult.Error(AppException.NetworkError(message = e.localizedMessage ?: "Polling network error", cause = e))
        }
    }

    override suspend fun saveDeviceCredentials(
        deviceId: String,
        permanentToken: String,
        tenantId: String?
    ) {
        val fingerprint = hardwareIdProvider.getHardwareFingerprint()
        tokenStorage.saveDeviceCredentials(deviceId, permanentToken, tenantId)
        deviceDao.insertOrUpdate(
            DeviceEntity(
                hardwareFingerprint = fingerprint,
                deviceId = deviceId,
                tenantId = tenantId,
                pairingStatus = PairingStatus.PAIRED,
                lastRegisteredAt = System.currentTimeMillis()
            )
        )
        SignageLogger.i(TAG, "Device registered and paired successfully with deviceId=$deviceId")
    }

    override suspend fun testBackendConnection(): AppResult<Long> {
        return try {
            val start = System.currentTimeMillis()
            val environment = preferencesDataStore.getResolvedEnvironment()
            val api = networkClientProvider.createDeviceApi(environment)
            val fingerprint = hardwareIdProvider.getHardwareFingerprint()

            // Quick poll to test connectivity
            val response = api.pollPairingStatus("ping", fingerprint)
            val latency = System.currentTimeMillis() - start
            // Any response (even 401 or 404) proves reachability of the server
            if (response.code() in 200..499) {
                AppResult.Success(latency)
            } else {
                AppResult.Error(AppException.ServerError(response.code(), "Server returned ${response.code()}"))
            }
        } catch (e: Exception) {
            AppResult.Error(AppException.NetworkError(message = e.localizedMessage ?: "Could not reach server", cause = e))
        }
    }

    override suspend fun unpairDevice(reason: String) {
        SignageLogger.w(TAG, "Unpairing device: $reason")
        tokenStorage.clearCredentials()
        val fingerprint = hardwareIdProvider.getHardwareFingerprint()
        deviceDao.insertOrUpdate(
            DeviceEntity(
                hardwareFingerprint = fingerprint,
                deviceId = null,
                tenantId = null,
                pairingStatus = PairingStatus.UNPAIRED
            )
        )
    }

    override suspend fun validateDeviceSession(): AppResult<Boolean> {
        if (!tokenStorage.hasValidCredentials()) {
            return AppResult.Success(false)
        }

        return try {
            val environment = preferencesDataStore.getResolvedEnvironment()
            val syncApi = networkClientProvider.createSyncApi(environment)
            val response = syncApi.getManifest()

            if (response.code() == 401 || response.code() == 403) {
                SignageLogger.w(TAG, "Device token rejected by server (HTTP ${response.code()}). Triggering unpair...")
                unpairDevice("Token revocado por el servidor")
                AppResult.Error(AppException.UnauthorizedError("El dispositivo fue desvinculado por el administrador"))
            } else {
                AppResult.Success(true)
            }
        } catch (e: Exception) {
            // If offline, preserve current paired state!
            SignageLogger.w(TAG, "Session validation network error. Preserving offline paired state: ${e.message}")
            AppResult.Success(true)
        }
    }

    override suspend fun clearDevice() {
        tokenStorage.clearCredentials()
        deviceDao.clearDevice()
        SignageLogger.i(TAG, "Device cleared locally")
    }
}
