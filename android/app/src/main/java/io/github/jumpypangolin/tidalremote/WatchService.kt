package io.github.jumpypangolin.tidalremote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings

/** Keeps the app running so WatchLink can listen for watch messages (service mode, with notification). */
class WatchService : Service() {

    companion object {
        private const val CHANNEL = "watch"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_REPOST = "io.github.jumpypangolin.tidalremote.REPOST_NOTIFICATION"

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, WatchService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground()
        WatchLink.start(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Also when the app is opened: brings the notification back after its category was switched on again.
        startForeground()
        if (intent?.action != ACTION_REPOST) WatchLink.reset()
        return START_STICKY
    }

    override fun onDestroy() {
        WatchLink.stop(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Watch connection", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val repost = PendingIntent.getService(
            this, 1, Intent(this, WatchService::class.java).setAction(ACTION_REPOST), PendingIntent.FLAG_IMMUTABLE,
        )
        val hide = PendingIntent.getActivity(
            this, 2,
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Tidal Remote")
            .setContentText("Waiting for commands from the watch")
            .setContentIntent(open)
            .setOngoing(true)
            // Android 14+ lets users swipe this away, after which Samsung may freeze the app; post it again.
            .setDeleteIntent(repost)
            // Long-pressing leads to the general notification settings on Samsung; this opens the right switch.
            .addAction(Notification.Action.Builder(null, "Hide this notification", hide).build())
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }
}
