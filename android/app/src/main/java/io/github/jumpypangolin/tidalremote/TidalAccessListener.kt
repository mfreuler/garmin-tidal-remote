package io.github.jumpypangolin.tidalremote

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService

/**
 * Does nothing itself. Tidal only lets apps with notification access connect to its media browser,
 * so the user enables this listener once (Settings → Notification access).
 */
class TidalAccessListener : NotificationListenerService() {
    companion object {
        fun isEnabled(ctx: Context): Boolean {
            val me = ComponentName(ctx, TidalAccessListener::class.java).flattenToString()
            val enabled = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
            return enabled.split(":").any { it == me }
        }
    }
}
