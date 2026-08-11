package com.pandora.mobile

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicInteger

internal data class AgentNotificationResult(
    val sent: Boolean,
    val error: String? = null,
)

/** Posts user-visible completion and attention updates requested by the local agent. */
internal class AgentNotificationController(context: Context) {
    private val appContext = context.applicationContext
    private val notifications = NotificationManagerCompat.from(appContext)
    private val nextId = AtomicInteger(FIRST_NOTIFICATION_ID)

    init {
        appContext.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Agent updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Updates when a Pandora agent finishes work or needs your attention"
            },
        )
    }

    fun send(title: String, message: String): AgentNotificationResult {
        val cleanTitle = title.trim().ifEmpty { "Pandora" }.take(MAX_TITLE_LENGTH)
        val cleanMessage = message.trim().take(MAX_MESSAGE_LENGTH)
        if (cleanMessage.isEmpty()) return AgentNotificationResult(false, "message_required")
        if (!AppSettings.agentNotificationsEnabled(appContext)) {
            return AgentNotificationResult(false, "notifications_disabled")
        }
        if (!notificationsAllowed()) return AgentNotificationResult(false, "notifications_disabled")

        val openPandora = PendingIntent.getActivity(
            appContext,
            0,
            Intent(appContext, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.app_icon)
            .setContentTitle(cleanTitle)
            .setContentText(cleanMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(cleanMessage))
            .setContentIntent(openPandora)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        return runCatching {
            postNotification(notification)
            AgentNotificationResult(true)
        }.getOrElse { AgentNotificationResult(false, "notification_failed") }
    }

    @SuppressLint("MissingPermission")
    private fun postNotification(notification: android.app.Notification) {
        // send() checks the runtime permission immediately before this call. Keep the
        // runCatching guard because Android can still revoke access between the two.
        notifications.notify(nextId.getAndIncrement(), notification)
    }

    private fun notificationsAllowed(): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permissionGranted && notifications.areNotificationsEnabled()
    }

    private companion object {
        const val CHANNEL_ID = "agent_updates"
        const val FIRST_NOTIFICATION_ID = 5200
        const val MAX_TITLE_LENGTH = 80
        const val MAX_MESSAGE_LENGTH = 500
    }
}
