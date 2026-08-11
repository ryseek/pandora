package com.pandora.mobile

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat

/** Keeps pairing discovery alive while Android Settings is in front. */
class AdbPairingService : Service() {
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "ADB phone setup", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "One-time Android wireless-debugging pairing"
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = (application as PandoraApplication).adbPlugin
        if (intent?.action == ACTION_SUBMIT_CODE) {
            val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_PAIRING_CODE)?.toString().orEmpty()
            manager.submitPairingCode(code)
        }
        startForeground(NOTIFICATION_ID, buildNotification(manager.state.value))
        return START_NOT_STICKY
    }

    private fun buildNotification(state: AdbPluginState): android.app.Notification {
        val openPandora = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.app_icon)
            .setContentTitle(notificationTitle(state.stage))
            .setContentText(state.detail ?: "Open Android Wireless debugging to continue.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(state.detail ?: "Open Android Wireless debugging to continue."))
            .setContentIntent(openPandora)
            .setOnlyAlertOnce(state.stage != AdbPluginStage.WAITING_FOR_CODE)
            .setOngoing(state.stage !in setOf(AdbPluginStage.CONNECTED, AdbPluginStage.ERROR))
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (state.stage == AdbPluginStage.WAITING_FOR_CODE) {
            val submitIntent = Intent(this, AdbPairingService::class.java).setAction(ACTION_SUBMIT_CODE)
            val submit = PendingIntent.getService(
                this,
                1,
                submitIntent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val input = RemoteInput.Builder(KEY_PAIRING_CODE)
                .setLabel("Six-digit pairing code")
                .build()
            builder.addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.app_icon,
                    "Enter pairing code",
                    submit,
                ).addRemoteInput(input).build(),
            )
        }
        return builder.build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "adb_pairing"
        private const val NOTIFICATION_ID = 4110
        private const val ACTION_REFRESH = "com.pandora.mobile.action.REFRESH_ADB_PAIRING"
        private const val ACTION_SUBMIT_CODE = "com.pandora.mobile.action.SUBMIT_ADB_PAIRING_CODE"
        private const val KEY_PAIRING_CODE = "pairing_code"

        fun refresh(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AdbPairingService::class.java).setAction(ACTION_REFRESH),
            )
        }

        fun complete(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.notify(
                NOTIFICATION_ID,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.app_icon)
                    .setContentTitle("ADB phone control is ready")
                    .setContentText("Pandora can now connect without pairing again.")
                    .setAutoCancel(true)
                    .build(),
            )
            context.stopService(Intent(context, AdbPairingService::class.java))
        }

        private fun notificationTitle(stage: AdbPluginStage): String = when (stage) {
            AdbPluginStage.PREPARING -> "Preparing ADB phone control"
            AdbPluginStage.WAITING_FOR_PAIRING -> "Open Wireless debugging"
            AdbPluginStage.WAITING_FOR_CODE -> "Enter the pairing code"
            AdbPluginStage.PAIRING -> "Pairing with this phone"
            AdbPluginStage.CONNECTING -> "Connecting to this phone"
            AdbPluginStage.CONNECTED -> "ADB phone control is ready"
            AdbPluginStage.ERROR -> "ADB pairing needs attention"
            else -> "Set up ADB phone control"
        }
    }
}
