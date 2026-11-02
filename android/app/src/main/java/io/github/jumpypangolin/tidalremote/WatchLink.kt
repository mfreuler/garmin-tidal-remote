package io.github.jumpypangolin.tidalremote

import android.content.Context
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The connection to the watch via the Connect IQ SDK, kept alive by WatchService.
 *
 * Tried and dropped (2026-10-29): the SDK's "binder service" mode, where Garmin Connect would start
 * the app per message. Garmin Connect 5.30.1 accepted the registration but never delivered a message.
 */
object WatchLink {

    private const val CHECK_TIMEOUT = 10_000L

    /** Set by the debug build to use the Connect IQ simulator over adb instead of the real watch. */
    var connectType = ConnectIQ.IQConnectType.WIRELESS

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val apps = Config.WATCH_APP_IDS.map { IQApp(it) }
    private var ciq: ConnectIQ? = null
    private var handler: CommandHandler? = null

    /** Must be called on the main thread. */
    fun start(context: Context) {
        val ctx = context.applicationContext
        stop(ctx)
        handler = CommandHandler(ctx)
        val instance = ConnectIQ.getInstance(ctx, connectType)
        ciq = instance
        instance.initialize(ctx, false, object : ConnectIQ.ConnectIQListener {
            override fun onSdkReady() {
                CommandLog.add(ctx, "Connect IQ ready")
                if (!removeBinderRegistration(instance, ctx)) registerDevices(instance, ctx)
            }

            override fun onInitializeError(status: ConnectIQ.IQSdkErrorStatus) {
                CommandLog.add(ctx, "Connect IQ error: $status")
            }

            override fun onSdkShutDown() {
                CommandLog.add(ctx, "Connect IQ shut down")
            }
        })
    }

    fun stop(ctx: Context) {
        try {
            ciq?.unregisterAllForEvents()
            ciq?.shutdown(ctx)
        } catch (e: Exception) {
            // SDK already shut down
        }
        ciq = null
    }

    /** Settings changed: drop the cached API client so new credentials are used. */
    fun reset() {
        handler?.reset()
    }

    /**
     * Undoes a binder-service registration left by the dropped experiment; while it exists Garmin Connect
     * delivers nothing. The SDK shuts down after each removal, so this restarts it. Returns true if it did.
     */
    private fun removeBinderRegistration(instance: ConnectIQ, ctx: Context): Boolean {
        val config = Config(ctx)
        val id = config.wakeApps.firstOrNull() ?: return false
        config.wakeApps = config.wakeApps - id
        try {
            instance.unregisterAppFromUsingBinderService(id, ctx)
            CommandLog.add(ctx, "Removed old wake-up registration")
        } catch (e: Exception) {
            CommandLog.add(ctx, "Wake-up removal failed: ${e.javaClass.simpleName}")
        }
        start(ctx)
        return true
    }

    private fun registerDevices(instance: ConnectIQ, ctx: Context) {
        val devices = try {
            instance.knownDevices ?: emptyList()
        } catch (e: Exception) {
            CommandLog.add(ctx, "Cannot list watches: ${e.javaClass.simpleName}")
            emptyList()
        }
        if (devices.isEmpty()) CommandLog.add(ctx, "No watch found in Garmin Connect")
        for (device in devices) {
            CommandLog.add(ctx, "Listening to ${device.friendlyName}")
            // Logged so the command list shows when Garmin Connect loses the watch.
            instance.registerForDeviceEvents(device) { _, status ->
                CommandLog.add(ctx, "${device.friendlyName}: ${status.name.lowercase().replace('_', ' ')}")
            }
            for (app in apps) {
                register(instance, ctx, device, app, app)
            }
            // The simulator delivers messages with an empty app id instead of the real one.
            if (connectType == ConnectIQ.IQConnectType.TETHERED) register(instance, ctx, device, IQApp(""), apps[0])
        }
    }

    /**
     * Asks Garmin Connect for each watch's state and whether Tidal Remote is installed on it. The second
     * answer comes from the watch itself, so it tests the same channel the watch app's commands use.
     */
    fun check(ctx: Context) {
        val instance = ciq ?: return CommandLog.add(ctx, "Check: not connected to Garmin Connect")
        try {
            for (device in instance.knownDevices ?: emptyList()) {
                CommandLog.add(ctx, "Check ${device.friendlyName}: ${instance.getDeviceStatus(device).name.lowercase().replace('_', ' ')}")
                var answered = false
                instance.getApplicationInfo(Config.WATCH_APP_IDS[0], device, object : ConnectIQ.IQApplicationInfoListener {
                    override fun onApplicationInfoReceived(app: IQApp) {
                        answered = true
                        CommandLog.add(ctx, "Check: watch answered, Tidal Remote installed")
                    }

                    override fun onApplicationNotInstalled(applicationId: String) {
                        answered = true
                        CommandLog.add(ctx, "Check: watch answered, Tidal Remote not installed")
                    }
                })
                // "Connected" only means Bluetooth; silence here means the watch's app channel is stuck.
                scope.launch {
                    delay(CHECK_TIMEOUT)
                    if (!answered) CommandLog.add(ctx, "Check: NO answer from the watch. Restart the watch")
                }
            }
        } catch (e: Exception) {
            CommandLog.add(ctx, "Check failed: ${e.javaClass.simpleName}")
        }
    }

    /** Listens for messages from [listenApp] and sends the replies to [replyApp]. */
    private fun register(instance: ConnectIQ, ctx: Context, device: IQDevice, listenApp: IQApp, replyApp: IQApp) {
        instance.registerForAppEvents(device, listenApp) { dev, _, message, status ->
            if (status == ConnectIQ.IQMessageStatus.SUCCESS) {
                onMessage(instance, ctx, dev, replyApp, message)
            } else {
                CommandLog.add(ctx, "Unreadable message from watch: $status")
            }
        }
    }

    private fun onMessage(instance: ConnectIQ, ctx: Context, device: IQDevice, app: IQApp, message: List<Any>) {
        // A Monkey C array arrives as one List element; flatten it to strings.
        val parts = (if (message.size == 1 && message[0] is List<*>) message[0] as List<*> else message)
            .map { it.toString() }
        val handler = handler ?: return
        scope.launch {
            val reply = handler.handle(parts)
            // Off the main thread: in simulator mode the SDK writes to a network socket directly.
            withContext(Dispatchers.IO) {
                try {
                    instance.sendMessage(device, app, reply) { _, _, status ->
                        if (status != ConnectIQ.IQMessageStatus.SUCCESS) CommandLog.add(ctx, "Reply failed: $status")
                    }
                } catch (e: Exception) {
                    CommandLog.add(ctx, "Reply failed: ${e.javaClass.simpleName}")
                }
            }
        }
    }
}
