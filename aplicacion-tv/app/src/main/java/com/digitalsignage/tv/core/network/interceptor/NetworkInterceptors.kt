package com.digitalsignage.tv.core.network.interceptor

import com.digitalsignage.tv.core.common.util.HardwareIdProvider
import com.digitalsignage.tv.core.security.SecureTokenStorage
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val tokenStorage: SecureTokenStorage
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Skip adding auth header if the request already has Authorization or x-temp-token
        if (originalRequest.header("Authorization") != null || originalRequest.header("x-temp-token") != null) {
            return chain.proceed(originalRequest)
        }

        val token = tokenStorage.getDeviceToken()
        return if (!token.isNullOrBlank()) {
            val authenticatedRequest = originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
            chain.proceed(authenticatedRequest)
        } else {
            chain.proceed(originalRequest)
        }
    }
}

class DeviceHeaderInterceptor(
    private val hardwareIdProvider: HardwareIdProvider
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val deviceInfo = hardwareIdProvider.getDeviceInfo()
        val builder = chain.request().newBuilder()
            .header("x-hardware-fingerprint", deviceInfo.fingerprint)
            .header("x-client-type", "ANDROID_TV")
            .header("x-app-version", deviceInfo.appVersion)
            .header("x-device-model", "${deviceInfo.manufacturer} ${deviceInfo.model}")

        if (chain.request().header("Accept") == null) {
            builder.header("Accept", "*/*")
        }

        return chain.proceed(builder.build())
    }
}

class DynamicHostInterceptor : Interceptor {
    companion object {
        private const val TAG = "DynamicHostInterceptor"
        @Volatile
        var activeHost: String? = null
        private val CANDIDATE_HOSTS = listOf("192.168.110.129", "10.0.2.2", "10.0.3.2")
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url
        val host = originalUrl.host
        val port = originalUrl.port

        val isTarget = CANDIDATE_HOSTS.contains(host) || port == 3000
        if (!isTarget) {
            return chain.proceed(originalRequest)
        }

        val currentActive = activeHost
        if (currentActive != null && currentActive != host) {
            val redirectedUrl = originalUrl.newBuilder().host(currentActive).build()
            val redirectedRequest = originalRequest.newBuilder().url(redirectedUrl).build()
            try {
                return chain.proceed(redirectedRequest)
            } catch (e: Exception) {
                activeHost = null
            }
        }

        try {
            val response = chain.proceed(originalRequest)
            if (response.isSuccessful || response.code < 500) {
                activeHost = host
                return response
            }
        } catch (e: Exception) {
            com.digitalsignage.tv.core.common.logging.SignageLogger.w(TAG, "Request to $host failed: ${e.message}")
        }

        for (candidate in CANDIDATE_HOSTS) {
            if (candidate == host) continue
            try {
                val candidateUrl = originalUrl.newBuilder().host(candidate).build()
                val candidateRequest = originalRequest.newBuilder().url(candidateUrl).build()
                val response = chain.proceed(candidateRequest)
                if (response.isSuccessful || response.code < 500) {
                    com.digitalsignage.tv.core.common.logging.SignageLogger.i(TAG, "Connected via candidate host: $candidate")
                    activeHost = candidate
                    return response
                }
            } catch (e: Exception) {
                // Try next candidate
            }
        }

        return chain.proceed(originalRequest)
    }
}
