package com.digitalsignage.tv.core.storage

import com.digitalsignage.tv.BuildConfig

enum class AppEnvironment(
    val displayName: String,
    val defaultRestUrl: String,
    val defaultSocketUrl: String
) {
    LOCAL_DEV(
        displayName = "Red Local PC",
        defaultRestUrl = BuildConfig.LAN_BASE_URL,
        defaultSocketUrl = BuildConfig.LAN_SOCKET_URL
    ),
    LAN_TEST(
        displayName = "Pruebas Red Local (LAN)",
        defaultRestUrl = BuildConfig.LAN_BASE_URL,
        defaultSocketUrl = BuildConfig.LAN_SOCKET_URL
    ),
    PRODUCTION(
        displayName = "Producción Cloud",
        defaultRestUrl = BuildConfig.DEFAULT_BASE_URL,
        defaultSocketUrl = BuildConfig.DEFAULT_SOCKET_URL
    ),
    CUSTOM(
        displayName = "Servidor Personalizado",
        defaultRestUrl = "",
        defaultSocketUrl = ""
    )
}

data class ResolvedEnvironment(
    val environment: AppEnvironment,
    val restBaseUrl: String,
    val socketBaseUrl: String
) {
    fun formatRestUrl(): String {
        return if (restBaseUrl.endsWith("/")) restBaseUrl else "$restBaseUrl/"
    }

    fun formatSocketUrl(): String {
        return if (socketBaseUrl.endsWith("/")) socketBaseUrl.removeSuffix("/") else socketBaseUrl
    }
}
