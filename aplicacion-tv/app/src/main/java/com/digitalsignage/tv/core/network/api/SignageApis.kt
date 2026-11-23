package com.digitalsignage.tv.core.network.api

import com.digitalsignage.tv.core.network.model.ApiResponse
import com.digitalsignage.tv.core.network.model.BatchMetricsRequest
import com.digitalsignage.tv.core.network.model.BatchMetricsResponse
import com.digitalsignage.tv.core.network.model.HeartbeatRequest
import com.digitalsignage.tv.core.network.model.HeartbeatResponse
import com.digitalsignage.tv.core.network.model.InitiatePairingRequest
import com.digitalsignage.tv.core.network.model.InitiatePairingResponse
import com.digitalsignage.tv.core.network.model.ManifestResponse
import com.digitalsignage.tv.core.network.model.PairingStatusResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface DeviceApi {

    @POST("api/v1/devices/pairing/initiate")
    suspend fun initiatePairing(
        @Body request: InitiatePairingRequest
    ): Response<ApiResponse<InitiatePairingResponse>>

    @GET("api/v1/devices/pairing/poll")
    suspend fun pollPairingStatus(
        @Header("x-temp-token") tempToken: String,
        @Query("fingerprint") hardwareFingerprint: String
    ): Response<ApiResponse<PairingStatusResponse>>
}

interface SyncApi {

    @GET("api/v1/devices/sync/manifest")
    suspend fun getManifest(
        @Header("If-None-Match") etag: String? = null
    ): Response<ApiResponse<ManifestResponse>>
}

interface TelemetryApi {

    @POST("api/v1/devices/telemetry/heartbeat")
    suspend fun sendHeartbeat(
        @Body request: HeartbeatRequest
    ): Response<ApiResponse<HeartbeatResponse>>

    @POST("api/v1/devices/telemetry/metrics")
    suspend fun sendBatchMetrics(
        @Body request: BatchMetricsRequest
    ): Response<ApiResponse<BatchMetricsResponse>>
}
