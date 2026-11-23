package com.digitalsignage.tv.core.common.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.digitalsignage.tv.BuildConfig

data class DeviceHardwareInfo(
    val fingerprint: String,
    val manufacturer: String,
    val model: String,
    val osVersion: String,
    val appVersion: String,
    val board: String
)

interface HardwareIdProvider {
    fun getHardwareFingerprint(): String
    fun getDeviceInfo(): DeviceHardwareInfo
}

class AndroidHardwareIdProvider(private val context: Context) : HardwareIdProvider {

    @SuppressLint("HardwareIds")
    override fun getHardwareFingerprint(): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "UNKNOWN_ANDROID_ID"

        val rawFingerprint = "${Build.MANUFACTURER}_${Build.MODEL}_${Build.BOARD}_${Build.FINGERPRINT}_$androidId"
        return HashUtils.sha256(rawFingerprint)
    }

    override fun getDeviceInfo(): DeviceHardwareInfo {
        return DeviceHardwareInfo(
            fingerprint = getHardwareFingerprint(),
            manufacturer = Build.MANUFACTURER ?: "Generic",
            model = Build.MODEL ?: "Android TV Box",
            osVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            board = Build.BOARD ?: "Unknown"
        )
    }
}
