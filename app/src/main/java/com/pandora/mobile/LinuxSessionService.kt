package com.pandora.mobile

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat

/** Keeps the active Linux PTY and its network access alive while Pandora is backgrounded. */
class LinuxSessionService : Service() {
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Linux session", NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val openPandora = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.app_icon)
            .setContentTitle("Pandora Linux is running")
            .setContentText("Tap to return to your terminal")
            .setContentIntent(openPandora)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "linux_session"
        private const val NOTIFICATION_ID = 4102

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, LinuxSessionService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LinuxSessionService::class.java))
        }
    }
}
