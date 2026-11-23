package com.digitalsignage.tv.core.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.digitalsignage.tv.core.common.logging.SignageLogger
import java.io.File

interface PlayerEngineListener {
    fun onVideoCompleted(assetId: String)
    fun onVideoError(assetId: String, error: PlaybackException)
    fun onPlaybackStarted(assetId: String)
}

interface SignagePlayerEngine {
    val exoPlayer: ExoPlayer
    fun playVideo(assetId: String, file: File)
    fun pause()
    fun resume()
    fun stop()
    fun setMuted(isMuted: Boolean)
    fun setListener(listener: PlayerEngineListener?)
    fun release()
}

class ExoSignagePlayerEngine(
    private val context: Context
) : SignagePlayerEngine {

    companion object {
        private const val TAG = "SignagePlayerEngine"
    }

    private var currentPlayingAssetId: String? = null
    private var engineListener: PlayerEngineListener? = null

    override val exoPlayer: ExoPlayer by lazy {
        buildExoPlayer()
    }

    private fun buildExoPlayer(): ExoPlayer {
        SignageLogger.i(TAG, "Initializing hardware-accelerated Media3 ExoPlayer instance...")
        val renderersFactory = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            setEnableDecoderFallback(true) // Crucial for low-end Android TV Boxes
        }

        return ExoPlayer.Builder(context, renderersFactory)
            .build()
            .apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF // Loop controlled by Signage Coordinator
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_ENDED -> {
                                val assetId = currentPlayingAssetId ?: return
                                SignageLogger.d(TAG, "Video playback completed for assetId=$assetId")
                                engineListener?.onVideoCompleted(assetId)
                            }
                            Player.STATE_READY -> {
                                val assetId = currentPlayingAssetId ?: return
                                engineListener?.onPlaybackStarted(assetId)
                            }
                            Player.STATE_BUFFERING -> {
                                SignageLogger.d(TAG, "ExoPlayer buffering...")
                            }
                            Player.STATE_IDLE -> {}
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        val assetId = currentPlayingAssetId ?: "unknown"
                        SignageLogger.e(TAG, "ExoPlayer error on assetId=$assetId: ${error.errorCodeName} - ${error.message}", error)
                        // Inform listener immediately to advance to next item without blank screen
                        engineListener?.onVideoError(assetId, error)
                    }
                })
            }
    }

    override fun playVideo(assetId: String, file: File) {
        if (!file.exists() || file.length() == 0L) {
            SignageLogger.e(TAG, "Cannot play video: file does not exist at ${file.absolutePath}")
            engineListener?.onVideoError(
                assetId,
                PlaybackException("File not found on disk", null, PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
            )
            return
        }

        try {
            currentPlayingAssetId = assetId
            SignageLogger.i(TAG, "Loading video: ${file.name} (${file.length()} bytes)")
            val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        } catch (e: Exception) {
            SignageLogger.e(TAG, "Exception preparing video asset $assetId", e)
            engineListener?.onVideoError(
                assetId,
                PlaybackException("Exception playing video", e, PlaybackException.ERROR_CODE_UNSPECIFIED)
            )
        }
    }

    override fun pause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        }
    }

    override fun resume() {
        if (!exoPlayer.isPlaying && exoPlayer.playbackState == Player.STATE_READY) {
            exoPlayer.play()
        }
    }

    override fun stop() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        currentPlayingAssetId = null
    }

    override fun setMuted(isMuted: Boolean) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    override fun setListener(listener: PlayerEngineListener?) {
        this.engineListener = listener
    }

    override fun release() {
        try {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.release()
            SignageLogger.i(TAG, "ExoPlayer released successfully")
        } catch (e: Exception) {
            SignageLogger.e(TAG, "Error releasing ExoPlayer", e)
        }
    }
}
