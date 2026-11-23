package com.digitalsignage.tv.core.network

import com.digitalsignage.tv.BuildConfig
import com.digitalsignage.tv.core.common.logging.SignageLogger
import com.digitalsignage.tv.core.common.util.HardwareIdProvider
import com.digitalsignage.tv.core.network.api.DeviceApi
import com.digitalsignage.tv.core.network.api.SyncApi
import com.digitalsignage.tv.core.network.api.TelemetryApi
import com.digitalsignage.tv.core.network.interceptor.AuthInterceptor
import com.digitalsignage.tv.core.network.interceptor.DeviceHeaderInterceptor
import com.digitalsignage.tv.core.security.SecureTokenStorage
import com.digitalsignage.tv.core.storage.ResolvedEnvironment
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class NetworkClientProvider(
    private val hardwareIdProvider: HardwareIdProvider,
    private val tokenStorage: SecureTokenStorage
) {
    companion object {
        private const val CONNECT_TIMEOUT_SEC = 5L
        private const val READ_TIMEOUT_SEC = 60L
        private const val WRITE_TIMEOUT_SEC = 60L
        private const val TAG = "NetworkClientProvider"
    }

    val okHttpClient: OkHttpClient by lazy {
        buildOkHttpClient()
    }

    private fun buildOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            SignageLogger.d("HTTP", message)
        }.apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.BASIC
            }
        }

        return OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(com.digitalsignage.tv.core.network.interceptor.DynamicHostInterceptor())
            .addInterceptor(DeviceHeaderInterceptor(hardwareIdProvider))
            .addInterceptor(AuthInterceptor(tokenStorage))
            .addInterceptor(loggingInterceptor)
            .build()
    }

    fun createRetrofit(environment: ResolvedEnvironment): Retrofit {
        val baseUrl = environment.formatRestUrl()
        SignageLogger.i(TAG, "Creating Retrofit instance for baseUrl: $baseUrl (${environment.environment.name})")

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    fun createDeviceApi(environment: ResolvedEnvironment): DeviceApi {
        return createRetrofit(environment).create(DeviceApi::class.java)
    }

    fun createSyncApi(environment: ResolvedEnvironment): SyncApi {
        return createRetrofit(environment).create(SyncApi::class.java)
    }

    fun createTelemetryApi(environment: ResolvedEnvironment): TelemetryApi {
        return createRetrofit(environment).create(TelemetryApi::class.java)
    }
}
