package com.digitalsignage.tv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.digitalsignage.tv.core.database.entity.DeviceEntity
import com.digitalsignage.tv.core.database.entity.PairingStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {

    @Query("SELECT * FROM device_identity WHERE hardwareFingerprint = :fingerprint LIMIT 1")
    suspend fun getDeviceByFingerprint(fingerprint: String): DeviceEntity?

    @Query("SELECT * FROM device_identity LIMIT 1")
    suspend fun getCurrentDevice(): DeviceEntity?

    @Query("SELECT * FROM device_identity LIMIT 1")
    fun observeCurrentDevice(): Flow<DeviceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(device: DeviceEntity)

    @Update
    suspend fun update(device: DeviceEntity)

    @Query("UPDATE device_identity SET pairingStatus = :status WHERE hardwareFingerprint = :fingerprint")
    suspend fun updatePairingStatus(fingerprint: String, status: PairingStatus)

    @Query("DELETE FROM device_identity")
    suspend fun clearDevice()
}
