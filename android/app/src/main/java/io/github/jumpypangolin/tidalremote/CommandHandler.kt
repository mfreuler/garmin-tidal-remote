package io.github.jumpypangolin.tidalremote

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL

/**
 * Executes one command from the watch and builds the reply.
 *
 * Watch -> phone:  ["menu"] | ["mix", name] | ["genre", name] | ["search", term] | ["radio"]
 *                  | ["shuffle"] | ["playpause"] | ["next"] | ["prev"] | ["info"] | ["favon"] | ["favoff"] | ["poll"]
 *                  | ["playlists", offset] | ["playlist", index, name]
 * Phone -> watch:  ["menu", mixes[], genres[], searchTerms[], radioReady] | ["ok", text] | ["err", text] | ["song", text]
 *                  | ["list", names[], offset, more]
 */
class CommandHandler(private val ctx: Context) {

    private companion object {
        const val CHECK_URL = "https://connectivitycheck.gstatic.com/generate_204"
        const val CHECK_TIMEOUT = 3000L
    }

    private val config = Config(ctx)
    private val tidal = TidalController(ctx)
    private var api: TidalApi? = null

    /** Must be called on the main thread. */
    suspend fun handle(msg: List<String>): List<Any> {
        val cmd = msg.firstOrNull() ?: return err("Empty command")
        val arg = msg.getOrNull(1) ?: ""
        // Sent every 30 s while Now playing is open, so it stays out of the command log.
        if (cmd == "poll") return listOf("song", try { tidal.nowPlaying() } catch (e: Exception) { "" })
        CommandLog.add(ctx, "→ ${msg.joinToString(" ")}")
        val reply: List<Any> = try {
            val shuffle = config.autoShuffle
            when (cmd) {
                "menu" -> menu()
                "mix" -> ok(tidal.playMix(arg, shuffle))
                "genre" -> ok(tidal.playGenre(arg, shuffle))
                "search" -> ok(tidal.playSearch(arg, shuffle))
                "playlists" -> playlists(arg.toIntOrNull() ?: 0)
                "playlist" -> ok(tidal.playPlaylist(arg.toIntOrNull() ?: -1, msg.getOrNull(2) ?: "", shuffle))
                "radio" -> ok(radio(shuffle))
                "shuffle" -> ok(if (tidal.toggleShuffle()) "Shuffle on" else "Shuffle off")
                "playpause" -> ok(tidal.playPause())
                "next" -> ok(tidal.skip(forward = true))
                "prev" -> ok(tidal.skip(forward = false))
                "info" -> ok(tidal.nowPlaying())
                "favon" -> ok(if (tidal.setFavorite(true)) "Favorite added" else "Already favorite")
                "favoff" -> ok(if (tidal.setFavorite(false)) "Favorite removed" else "Not a favorite")
                else -> err("Unknown command $cmd")
            }
        } catch (e: TidalException) {
            failure(e.message ?: "Failed")
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            failure("Tidal did not answer")
        } catch (e: Exception) {
            failure("Error: ${e.javaClass.simpleName}")
        }
        // One short line per reply: the menu lists are long and song info spans two lines.
        val summary = when (reply.firstOrNull()) {
            "menu" -> "menu (${(reply[1] as List<*>).size} mixes)"
            "list" -> "list (${(reply[1] as List<*>).size} playlists)"
            else -> reply.joinToString(" ")
        }
        CommandLog.add(ctx, "← ${summary.replace("\n", " / ").take(80)}")
        return reply
    }

    private suspend fun menu(): List<Any> {
        val mixes = try {
            // Empty means Tidal could not load them, for example without internet.
            tidal.mixNames().also { if (it.isNotEmpty()) config.cachedMixes = it }.ifEmpty { config.cachedMixes }
        } catch (e: Exception) {
            config.cachedMixes
        }
        return listOf("menu", mixes, config.genres, config.searchTerms, radioReady())
    }

    private suspend fun playlists(offset: Int): List<Any> {
        val (names, more) = tidal.playlistPage(offset)
        if (names.isEmpty()) throw TidalException("No favorite playlists")
        return listOf("list", names, offset, more)
    }

    /** Track radio needs the Tidal API login; without it the watch hides the menu item. */
    private fun radioReady() = config.clientId.isNotBlank() && config.clientSecret.isNotBlank()

    private suspend fun radio(shuffle: Boolean): String {
        if (!radioReady()) throw TidalException("Track radio is not set up in the phone app")
        val (trackId, title) = tidal.currentTrack()
        val mixId = withContext(Dispatchers.IO) { api().trackRadioMixId(trackId) }
        tidal.playMixId(mixId, shuffle)
        return "Radio: $title"
    }

    private fun api(): TidalApi {
        val current = api
        if (current != null) return current
        return TidalApi(config.clientId, config.clientSecret, config.country).also { api = it }
    }

    /** Settings changed: drop the cached API client so new credentials are used. */
    fun reset() {
        api = null
    }

    /** Most errors look like something else when the phone is offline, so that is checked first. */
    private suspend fun failure(text: String) = err(if (online()) text else "No internet connection")

    /**
     * A real request, the same way Tidal's traffic goes. Android's own status isn't enough:
     * it can report a working connection while nothing gets through.
     */
    private suspend fun online(): Boolean {
        // A hanging DNS lookup ignores the timeouts below, so the wait has its own limit.
        val check = CoroutineScope(Dispatchers.IO).async {
            try {
                val c = URL(CHECK_URL).openConnection() as HttpURLConnection
                c.connectTimeout = CHECK_TIMEOUT.toInt()
                c.readTimeout = CHECK_TIMEOUT.toInt()
                c.instanceFollowRedirects = false
                try {
                    c.responseCode == 204
                } finally {
                    c.disconnect()
                }
            } catch (e: Exception) {
                false
            }
        }
        return withTimeoutOrNull(CHECK_TIMEOUT) { check.await() } ?: false
    }

    private fun ok(text: String) = listOf("ok", text)
    private fun err(text: String) = listOf("err", text)
}
