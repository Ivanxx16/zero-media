package com.digitalsignage.tv.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.digitalsignage.tv.core.common.logging.SignageLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "0media_app_preferences")

data class AppSettings(
    val environment: AppEnvironment,
    val customRestUrl: String,
    val customSocketUrl: String,
    val screenOrientation: String,
    val isAudioMuted: Boolean,
    val volumeLevel: Int,
    val diagnosticsPin: String,
    val lastSyncTimestamp: Long,
    val operatingHoursEnabled: Boolean = false,
    val turnOnTime: String = "08:00",
    val turnOffTime: String = "22:00",
    val activeDays: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7)
)

class AppPreferencesDataStore(private val context: Context) {

    companion object {
        private val KEY_ENVIRONMENT = stringPreferencesKey("app_environment")
        private val KEY_CUSTOM_REST_URL = stringPreferencesKey("custom_rest_url")
        private val KEY_CUSTOM_SOCKET_URL = stringPreferencesKey("custom_socket_url")
        private val KEY_SCREEN_ORIENTATION = stringPreferencesKey("screen_orientation")
        private val KEY_AUDIO_MUTED = booleanPreferencesKey("audio_muted")
        private val KEY_VOLUME_LEVEL = intPreferencesKey("volume_level")
        private val KEY_DIAGNOSTICS_PIN = stringPreferencesKey("diagnostics_pin")
        private val KEY_LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")
        private val KEY_SCHEDULE_ENABLED = booleanPreferencesKey("operating_hours_enabled")
        private val KEY_SCHEDULE_TURN_ON = stringPreferencesKey("operating_hours_turn_on")
        private val KEY_SCHEDULE_TURN_OFF = stringPreferencesKey("operating_hours_turn_off")
        private val KEY_SCHEDULE_DAYS = stringPreferencesKey("operating_hours_days")
        private const val TAG = "AppPreferences"
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                SignageLogger.e(TAG, "Error reading preferences", exception)
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val envName = prefs[KEY_ENVIRONMENT] ?: AppEnvironment.LAN_TEST.name
            val env = try {
                AppEnvironment.valueOf(envName)
            } catch (_: Exception) {
                AppEnvironment.LAN_TEST
            }

            val rawDays = prefs[KEY_SCHEDULE_DAYS] ?: "1,2,3,4,5,6,7"
            val activeDays = rawDays.split(",").mapNotNull { it.trim().toIntOrNull() }.ifEmpty { listOf(1, 2, 3, 4, 5, 6, 7) }

            AppSettings(
                environment = env,
                customRestUrl = prefs[KEY_CUSTOM_REST_URL] ?: "",
                customSocketUrl = prefs[KEY_CUSTOM_SOCKET_URL] ?: "",
                screenOrientation = prefs[KEY_SCREEN_ORIENTATION] ?: "LANDSCAPE",
                isAudioMuted = prefs[KEY_AUDIO_MUTED] ?: false,
                volumeLevel = prefs[KEY_VOLUME_LEVEL] ?: 100,
                diagnosticsPin = prefs[KEY_DIAGNOSTICS_PIN] ?: "0000",
                lastSyncTimestamp = prefs[KEY_LAST_SYNC_TIMESTAMP] ?: 0L,
                operatingHoursEnabled = prefs[KEY_SCHEDULE_ENABLED] ?: false,
                turnOnTime = prefs[KEY_SCHEDULE_TURN_ON] ?: "08:00",
                turnOffTime = prefs[KEY_SCHEDULE_TURN_OFF] ?: "22:00",
                activeDays = activeDays
            )
        }

    suspend fun getResolvedEnvironment(): ResolvedEnvironment {
        return try {
            val prefs = context.dataStore.data.first()
            val envName = prefs[KEY_ENVIRONMENT] ?: AppEnvironment.LAN_TEST.name
            val env = try {
                AppEnvironment.valueOf(envName)
            } catch (_: Exception) {
                AppEnvironment.LAN_TEST
            }
            val customRest = prefs[KEY_CUSTOM_REST_URL] ?: ""
            val customSocket = prefs[KEY_CUSTOM_SOCKET_URL] ?: ""

            val restUrl = if (env == AppEnvironment.CUSTOM && customRest.isNotBlank()) {
                customRest
            } else {
                env.defaultRestUrl
            }

            val socketUrl = if (env == AppEnvironment.CUSTOM && customSocket.isNotBlank()) {
                customSocket
            } else {
                env.defaultSocketUrl
            }

            ResolvedEnvironment(env, restUrl, socketUrl)
        } catch (e: Exception) {
            SignageLogger.e(TAG, "Failed to resolve environment from preferences", e)
            ResolvedEnvironment(
                environment = AppEnvironment.LAN_TEST,
                restBaseUrl = AppEnvironment.LAN_TEST.defaultRestUrl,
                socketBaseUrl = AppEnvironment.LAN_TEST.defaultSocketUrl
            )
        }
    }

    suspend fun setEnvironment(environment: AppEnvironment) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ENVIRONMENT] = environment.name
        }
    }

    suspend fun setCustomUrls(restUrl: String, socketUrl: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CUSTOM_REST_URL] = restUrl
            prefs[KEY_CUSTOM_SOCKET_URL] = socketUrl
            prefs[KEY_ENVIRONMENT] = AppEnvironment.CUSTOM.name
        }
    }

    suspend fun updateLastSyncTimestamp(timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_SYNC_TIMESTAMP] = timestamp
        }
    }

    suspend fun setAudioMuted(muted: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUDIO_MUTED] = muted
        }
    }

    suspend fun updateOperatingHours(
        enabled: Boolean,
        turnOnTime: String,
        turnOffTime: String,
        activeDays: List<Int>
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SCHEDULE_ENABLED] = enabled
            prefs[KEY_SCHEDULE_TURN_ON] = turnOnTime
            prefs[KEY_SCHEDULE_TURN_OFF] = turnOffTime
            prefs[KEY_SCHEDULE_DAYS] = activeDays.joinToString(",")
        }
    }
}
