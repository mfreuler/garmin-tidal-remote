package io.github.jumpypangolin.tidalremote

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Executes one command from the watch and builds the reply.
 *
 * Watch -> phone:  ["menu"] | ["mix", name] | ["genre", name] | ["search", term] | ["radio"]
 *                  | ["shuffle"] | ["playpause"] | ["next"] | ["prev"] | ["info"] | ["favon"] | ["favoff"]
 * Phone -> watch:  ["menu", mixes[], genres[], searchTerms[], radioReady] | ["ok", text] | ["err", text]
 */
class CommandHandler(private val ctx: Context) {

    private val config = Config(ctx)
    private val tidal = TidalController(ctx)
    private var api: TidalApi? = null

    /** Must be called on the main thread. */
    suspend fun handle(msg: List<String>): List<Any> {
        val cmd = msg.firstOrNull() ?: return err("Empty command")
        val arg = msg.getOrNull(1) ?: ""
        CommandLog.add(ctx, "→ ${msg.joinToString(" ")}")
        val reply: List<Any> = try {
            val shuffle = config.autoShuffle
            when (cmd) {
                "menu" -> menu()
                "mix" -> ok(tidal.playMix(arg, shuffle))
                "genre" -> ok(tidal.playGenre(arg, shuffle))
                "search" -> ok(tidal.playSearch(arg, shuffle))
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
            err(e.message ?: "Failed")
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            err("Tidal did not answer")
        } catch (e: Exception) {
            err("Error: ${e.javaClass.simpleName}")
        }
        // One short line per reply: the menu lists are long and song info spans two lines.
        val summary = if (reply.firstOrNull() == "menu") "menu (${(reply[1] as List<*>).size} mixes)" else reply.joinToString(" ")
        CommandLog.add(ctx, "← ${summary.replace("\n", " / ").take(80)}")
        return reply
    }

    private suspend fun menu(): List<Any> {
        val mixes = try {
            tidal.mixNames().also { if (it.isNotEmpty()) config.cachedMixes = it }
        } catch (e: Exception) {
            config.cachedMixes
        }
        return listOf("menu", mixes, config.genres, config.searchTerms, radioReady())
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

    private fun ok(text: String) = listOf("ok", text)
    private fun err(text: String) = listOf("err", text)
}
