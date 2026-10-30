package io.github.jumpypangolin.tidalremote

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Keeps the last commands and results, shown in the app. */
object CommandLog {
    private const val MAX = 30
    private const val TAG = "TidalRemote"

    fun add(ctx: Context, line: String) {
        Log.i(TAG, line)
        val prefs = ctx.getSharedPreferences("log", Context.MODE_PRIVATE)
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val lines = (listOf("$time  $line") + read(ctx)).take(MAX)
        prefs.edit().putString("lines", lines.joinToString("\n")).apply()
    }

    fun read(ctx: Context): List<String> =
        ctx.getSharedPreferences("log", Context.MODE_PRIVATE).getString("lines", "")!!
            .lines().filter { it.isNotEmpty() }
}
