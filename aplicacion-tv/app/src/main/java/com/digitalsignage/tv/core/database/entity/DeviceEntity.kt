package com.digitalsignage.tv.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class PairingStatus {
    UNPAIRED,
    PAIRING_IN_PROGRESS,
    PAIRED,
    REVOKED
}

@Entity(tableName = "device_identity")
data class DeviceEntity(
    @PrimaryKey
    val hardwareFingerprint: String,
    val deviceId: String? = null,
    val tenantId: String? = null,
    val deviceName: String? = null,
    val pairingStatus: PairingStatus = PairingStatus.UNPAIRED,
    val lastRegisteredAt: Long = 0L,
    val lastSyncAt: Long = 0L
)
