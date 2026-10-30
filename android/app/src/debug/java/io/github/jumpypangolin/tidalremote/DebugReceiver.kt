package io.github.jumpypangolin.tidalremote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

    /** Tethered = talk to the Connect IQ simulator on the PC (needs `adb forward tcp:7381 tcp:7381`). */
    private fun switchConnection(ctx: Context, tethered: Boolean) {
        WatchLink.connectType = if (tethered) ConnectIQ.IQConnectType.TETHERED else ConnectIQ.IQConnectType.WIRELESS
        ctx.stopService(Intent(ctx, WatchService::class.java))
        WatchService.start(ctx)
        CommandLog.add(ctx, "Connection: ${WatchLink.connectType}")
    }
}
