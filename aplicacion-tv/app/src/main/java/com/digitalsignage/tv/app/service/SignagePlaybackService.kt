package com.digitalsignage.tv.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.digitalsignage.tv.R
import com.digitalsignage.tv.core.common.logging.SignageLogger

class SignagePlaybackService : Service() {

    companion object {
        private const val CHANNEL_ID = "0media_playback_service_channel"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "SignagePlaybackService"

        fun start(context: Context) {
            val intent = Intent(context, SignagePlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SignagePlaybackService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        SignageLogger.i(TAG, "Creating SignagePlaybackService")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        SignageLogger.d(TAG, "SignagePlaybackService onStartCommand")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "0Media Signage Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Reproducción continua de cartelería digital en pantalla completa"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("0Media Digital Signage")
            .setContentText("Reproductor activo 24/7")
            .setSmallIcon(R.drawable.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        SignageLogger.i(TAG, "SignagePlaybackService destroyed")
    }
}
