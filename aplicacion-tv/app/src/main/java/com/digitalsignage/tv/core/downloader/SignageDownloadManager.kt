package com.digitalsignage.tv.core.downloader

import android.content.Context
import android.os.StatFs
import com.digitalsignage.tv.core.common.logging.SignageLogger
import com.digitalsignage.tv.core.common.result.AppException
import com.digitalsignage.tv.core.common.result.AppResult
import com.digitalsignage.tv.core.common.util.HashUtils
import com.digitalsignage.tv.core.database.dao.MediaAssetDao
import com.digitalsignage.tv.core.database.entity.AssetDownloadStatus
import com.digitalsignage.tv.core.database.entity.MediaAssetEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

data class DownloadProgress(
    val totalAssets: Int,
    val completedAssets: Int,
    val currentAssetId: String? = null,
    val currentProgressPercent: Int = 0,
    val isDownloading: Boolean = false
)

interface DownloadManager {
    val progress: StateFlow<DownloadProgress>
    suspend fun downloadPendingAssets(): AppResult<Int>
    suspend fun purgeOrphanedAssets(): Long
    fun getMediaDirectory(): File
}

class SignageDownloadManager(
    private val context: Context,
    private val mediaAssetDao: MediaAssetDao,
    private val okHttpClient: OkHttpClient
) : DownloadManager {

    companion object {
        private const val TAG = "DownloadManager"
        private const val BUFFER_SIZE = 8192
        private const val MIN_FREE_DISK_MB = 300L
        private const val MEDIA_SUBDIR = "signage_media"
    }

    private val mutex = Mutex()
    private val _progress = MutableStateFlow(DownloadProgress(0, 0))
    override val progress: StateFlow<DownloadProgress> = _progress.asStateFlow()

    override fun getMediaDirectory(): File {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val mediaDir = File(baseDir, MEDIA_SUBDIR)
        if (!mediaDir.exists()) {
            mediaDir.mkdirs()
        }
        return mediaDir
    }

    override suspend fun downloadPendingAssets(): AppResult<Int> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val pending = mediaAssetDao.getAssetsByStatus(AssetDownloadStatus.PENDING)
            val failed = mediaAssetDao.getAssetsByStatus(AssetDownloadStatus.FAILED)
            val queue = (pending + failed).distinctBy { it.id }

            if (queue.isEmpty()) {
                SignageLogger.d(TAG, "No pending assets to download.")
                return@withContext AppResult.Success(0)
            }

            SignageLogger.i(TAG, "Starting download queue of ${queue.size} assets...")
            _progress.value = DownloadProgress(totalAssets = queue.size, completedAssets = 0, isDownloading = true)

            ensureDiskSpaceAvailable()

            var successCount = 0
            for ((index, asset) in queue.withIndex()) {
                _progress.value = _progress.value.copy(
                    currentAssetId = asset.id,
                    completedAssets = index,
                    currentProgressPercent = 0
                )

                val result = downloadSingleAsset(asset)
                if (result is AppResult.Success) {
                    successCount++
                }
            }

            _progress.value = DownloadProgress(
                totalAssets = queue.size,
                completedAssets = successCount,
                isDownloading = false
            )

            SignageLogger.i(TAG, "Finished download queue. Successfully downloaded: $successCount/${queue.size}")
            return@withContext AppResult.Success(successCount)
        }
    }

    private suspend fun downloadSingleAsset(asset: MediaAssetEntity): AppResult<File> {
        val mediaDir = getMediaDirectory()
        val extension = getExtensionFromMime(asset.mimeType)
        val finalFile = File(mediaDir, "${asset.id}$extension")
        val tmpFile = File(mediaDir, "${asset.id}.tmp")

        // 1. Verify if final file already exists and has correct hash
        if (finalFile.exists() && finalFile.length() > 0) {
            val currentHash = HashUtils.sha256(finalFile)
            if (currentHash.equals(asset.checksumSha256, ignoreCase = true)) {
                SignageLogger.d(TAG, "Asset ${asset.id} already exists locally with valid hash. Skipping download.")
                mediaAssetDao.markCompleted(
                    id = asset.id,
                    localPath = finalFile.absolutePath,
                    timestamp = System.currentTimeMillis()
                )
                return AppResult.Success(finalFile)
            }
        }

        // 2. Download from remote URL to temporary file
        mediaAssetDao.update(asset.copy(downloadStatus = AssetDownloadStatus.DOWNLOADING))
        SignageLogger.i(TAG, "Downloading asset ${asset.id} from ${asset.remoteUrl}")

        try {
            val request = Request.Builder().url(asset.remoteUrl).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                SignageLogger.e(TAG, "HTTP error ${response.code} downloading asset ${asset.id}")
                mediaAssetDao.markFailed(asset.id)
                return AppResult.Error(AppException.ServerError(response.code, "Failed to download asset"))
            }

            val body = response.body ?: run {
                mediaAssetDao.markFailed(asset.id)
                return AppResult.Error(AppException.ServerError(500, "Empty response body"))
            }

            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            body.byteStream().use { input: InputStream ->
                FileOutputStream(tmpFile).use { output: FileOutputStream ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            val percent = ((downloadedBytes * 100) / totalBytes).toInt()
                            _progress.value = _progress.value.copy(currentProgressPercent = percent)
                        }
                    }
                    output.flush()
                }
            }

            // 3. Verify SHA-256 Checksum
            val calculatedHash = HashUtils.sha256(tmpFile)
            if (!calculatedHash.equals(asset.checksumSha256, ignoreCase = true)) {
                SignageLogger.e(TAG, "Hash mismatch for asset ${asset.id}! Expected: ${asset.checksumSha256}, got: $calculatedHash")
                if (tmpFile.exists()) tmpFile.delete()
                mediaAssetDao.markFailed(asset.id, AssetDownloadStatus.CORRUPTED)
                return AppResult.Error(AppException.IntegrityVerificationError(asset.checksumSha256, calculatedHash))
            }

            // 4. Atomic Rename to final file
            if (finalFile.exists()) {
                finalFile.delete()
            }
            val renamed = tmpFile.renameTo(finalFile)
            if (!renamed) {
                SignageLogger.e(TAG, "Failed to rename temp file to ${finalFile.absolutePath}")
                mediaAssetDao.markFailed(asset.id)
                return AppResult.Error(AppException.DiskStorageError("Could not rename temp media file"))
            }

            SignageLogger.i(TAG, "Asset ${asset.id} successfully downloaded and verified at ${finalFile.absolutePath}")
            mediaAssetDao.markCompleted(
                id = asset.id,
                localPath = finalFile.absolutePath,
                timestamp = System.currentTimeMillis()
            )
            return AppResult.Success(finalFile)

        } catch (e: Exception) {
            SignageLogger.e(TAG, "Exception while downloading asset ${asset.id}", e)
            if (tmpFile.exists()) tmpFile.delete()
            mediaAssetDao.markFailed(asset.id)
            return AppResult.Error(AppException.NetworkError(message = e.localizedMessage ?: "Download failure", cause = e))
        }
    }

    private fun ensureDiskSpaceAvailable() {
        try {
            val stat = StatFs(getMediaDirectory().path)
            val availableMb = (stat.availableBlocksLong * stat.blockSizeLong) / (1024 * 1024)
            SignageLogger.d(TAG, "Available disk space: ${availableMb}MB (Min required: ${MIN_FREE_DISK_MB}MB)")

            if (availableMb < MIN_FREE_DISK_MB) {
                SignageLogger.w(TAG, "Low disk space! Triggering purge of unreferenced media assets...")
                // In background coroutine or sequential
                purgeOrphanedAssetsSync()
            }
        } catch (e: Exception) {
            SignageLogger.e(TAG, "Error checking disk space", e)
        }
    }

    override suspend fun purgeOrphanedAssets(): Long = withContext(Dispatchers.IO) {
        purgeOrphanedAssetsSync()
    }

    private fun purgeOrphanedAssetsSync(): Long {
        var bytesFreed = 0L
        try {
            // Find assets not present in current playlist
            val mediaDir = getMediaDirectory()
            val files = mediaDir.listFiles() ?: return 0L

            for (file in files) {
                if (file.name.endsWith(".tmp")) {
                    bytesFreed += file.length()
                    file.delete()
                }
            }
            SignageLogger.i(TAG, "Cleaned up temporary download files, freed: $bytesFreed bytes")
        } catch (e: Exception) {
            SignageLogger.e(TAG, "Error purging assets", e)
        }
        return bytesFreed
    }

    private fun getExtensionFromMime(mimeType: String): String {
        return when (mimeType.lowercase()) {
            "video/mp4" -> ".mp4"
            "image/jpeg", "image/jpg" -> ".jpg"
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            else -> if (mimeType.startsWith("video/")) ".mp4" else ".jpg"
        }
    }
}
