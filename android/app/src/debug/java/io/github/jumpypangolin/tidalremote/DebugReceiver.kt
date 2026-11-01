package io.github.jumpypangolin.tidalremote

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.support.v4.media.MediaBrowserCompat
import android.util.Log
import com.garmin.android.connectiq.ConnectIQ
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug builds only: run a watch command from adb without a watch. The reply goes to the log (tag TidalRemote).
 * adb shell am broadcast -n io.github.jumpypangolin.tidalremote/.DebugReceiver --esa cmd "mix,My Mix 3"
 * adb shell am broadcast -n io.github.jumpypangolin.tidalremote/.DebugReceiver --ez tethered true
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.hasExtra("tethered")) {
            switchConnection(ctx, intent.getBooleanExtra("tethered", false))
            return
        }
        if (intent.hasExtra("browse")) {
            browse(ctx, intent.getStringExtra("browse") ?: "")
            return
        }
        val cmd = intent.getStringArrayExtra("cmd")?.toList() ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                CommandHandler(ctx.applicationContext).handle(cmd)
            } finally {
                pending.finish()
            }
        }
    }

    /** Logs the children of a Tidal media ID (empty = the root), to explore what the media browser offers. */
    private fun browse(ctx: Context, id: String) {
        lateinit var b: MediaBrowserCompat
        b = MediaBrowserCompat(ctx.applicationContext, ComponentName(TidalController.TIDAL, "com.aspiro.wamp.player.MusicService"),
            object : MediaBrowserCompat.ConnectionCallback() {
                override fun onConnected() {
                    val parent = id.ifEmpty { b.root }
                    Log.i("TidalRemote", "browse $parent")
                    b.subscribe(parent, object : MediaBrowserCompat.SubscriptionCallback() {
                        override fun onChildrenLoaded(p: String, children: List<MediaBrowserCompat.MediaItem>) {
                            children.forEach {
                                Log.i("TidalRemote", "  ${it.description.title} | ${it.mediaId} | browsable=${it.isBrowsable} playable=${it.isPlayable}")
                            }
                            Log.i("TidalRemote", "browse end (${children.size})")
                            b.disconnect()
                        }

                        override fun onError(p: String) {
                            Log.i("TidalRemote", "browse error $p")
                            b.disconnect()
                        }
                    })
                }
            }, null)
        b.connect()
    }

    /** Tethered = talk to the Connect IQ simulator on the PC (needs `adb forward tcp:7381 tcp:7381`). */
    private fun switchConnection(ctx: Context, tethered: Boolean) {
        WatchLink.connectType = if (tethered) ConnectIQ.IQConnectType.TETHERED else ConnectIQ.IQConnectType.WIRELESS
        ctx.stopService(Intent(ctx, WatchService::class.java))
        WatchService.start(ctx)
        CommandLog.add(ctx, "Connection: ${WatchLink.connectType}")
    }
}
