package com.digitalsignage.tv.domain.usecase

import com.digitalsignage.tv.core.common.result.AppResult
import com.digitalsignage.tv.data.repository.DeviceRepository
import com.digitalsignage.tv.domain.model.DeviceIdentity
import com.digitalsignage.tv.domain.model.PairingSession
import kotlinx.coroutines.flow.Flow

class GetDeviceIdentityUseCase(
    private val deviceRepository: DeviceRepository
) {
    suspend operator fun invoke(): DeviceIdentity = deviceRepository.getDeviceIdentity()
    fun observe(): Flow<DeviceIdentity> = deviceRepository.observeDeviceIdentity()
}

class InitiatePairingUseCase(
    private val deviceRepository: DeviceRepository
) {
    suspend operator fun invoke(): AppResult<PairingSession> = deviceRepository.initiatePairing()
}

class CheckPairingStatusUseCase(
    private val deviceRepository: DeviceRepository
) {
    suspend operator fun invoke(tempToken: String): AppResult<Boolean> =
        deviceRepository.pollPairingStatus(tempToken)
}

class TestBackendConnectionUseCase(
    private val deviceRepository: DeviceRepository
) {
    suspend operator fun invoke(): AppResult<Long> = deviceRepository.testBackendConnection()
}

class UnpairDeviceUseCase(
    private val deviceRepository: DeviceRepository
) {
    suspend operator fun invoke(reason: String = "Desvinculación solicitada") =
        deviceRepository.unpairDevice(reason)
}

class ValidateDeviceSessionUseCase(
    private val deviceRepository: DeviceRepository
) {
    suspend operator fun invoke(): AppResult<Boolean> = deviceRepository.validateDeviceSession()
}
