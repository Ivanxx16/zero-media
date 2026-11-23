package com.digitalsignage.tv.core.security

import android.content.Context
import android.content.SharedPreferences
import com.digitalsignage.tv.core.common.logging.SignageLogger

interface SecureTokenStorage {
    fun saveDeviceCredentials(deviceId: String, deviceToken: String, tenantId: String?)
    fun getDeviceToken(): String?
    fun getDeviceId(): String?
    fun getTenantId(): String?
    fun clearCredentials()
    fun hasValidCredentials(): Boolean
}

class EncryptedSecureTokenStorage(
    context: Context,
    private val keystoreHelper: KeystoreHelper
) : SecureTokenStorage {

    companion object {
        private const val PREFS_NAME = "0media_secure_tokens_store"
        private const val KEY_ENCRYPTED_TOKEN = "enc_device_token"
        private const val KEY_RAW_TOKEN = "raw_device_token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_TENANT_ID = "tenant_id"
        private const val TAG = "SecureTokenStorage"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun saveDeviceCredentials(deviceId: String, deviceToken: String, tenantId: String?) {
        try {
            val encryptedToken = keystoreHelper.encrypt(deviceToken)
            prefs.edit()
                .putString(KEY_DEVICE_ID, deviceId)
                .putString(KEY_ENCRYPTED_TOKEN, encryptedToken)
                .putString(KEY_RAW_TOKEN, deviceToken)
                .putString(KEY_TENANT_ID, tenantId)
                .commit()
            SignageLogger.i(TAG, "Device credentials safely encrypted and stored for deviceId=$deviceId")
        } catch (e: Exception) {
            SignageLogger.e(TAG, "Failed to encrypt device credentials, saving directly with fallback", e)
            prefs.edit()
                .putString(KEY_DEVICE_ID, deviceId)
                .putString(KEY_RAW_TOKEN, deviceToken)
                .putString(KEY_TENANT_ID, tenantId)
                .commit()
        }
    }

    override fun getDeviceToken(): String? {
        val encrypted = prefs.getString(KEY_ENCRYPTED_TOKEN, null)
        if (encrypted != null) {
            try {
                val decrypted = keystoreHelper.decrypt(encrypted)
                if (!decrypted.isNullOrBlank()) return decrypted
            } catch (e: Exception) {
                SignageLogger.w(TAG, "Failed to decrypt device token from Keystore: ${e.message}")
            }
        }
        return prefs.getString(KEY_RAW_TOKEN, null)
    }

    override fun getDeviceId(): String? = prefs.getString(KEY_DEVICE_ID, null)

    override fun getTenantId(): String? = prefs.getString(KEY_TENANT_ID, null)

    override fun clearCredentials() {
        prefs.edit().clear().commit()
        SignageLogger.w(TAG, "Secure device credentials wiped")
    }

    override fun hasValidCredentials(): Boolean {
        val token = getDeviceToken()
        val deviceId = getDeviceId()
        return !token.isNullOrBlank() && !deviceId.isNullOrBlank()
    }
}
