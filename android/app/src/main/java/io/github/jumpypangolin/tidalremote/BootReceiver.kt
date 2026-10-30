package io.github.jumpypangolin.tidalremote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Starts the watch service after a reboot or app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        WatchService.start(ctx)
    }
}
