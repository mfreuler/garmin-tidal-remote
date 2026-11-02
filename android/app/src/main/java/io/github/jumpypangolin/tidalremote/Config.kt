package io.github.jumpypangolin.tidalremote

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

/** User settings, editable in the app. Lists are stored one entry per line. */
class Config(private val ctx: Context) {

    companion object {
        /** Watch app ids; must match the id in the Connect IQ manifest. A list, so further variants can be added. */
        val WATCH_APP_IDS = listOf("b911268758cd4f2ba9a0948bbf36cfe5")

        val DEFAULT_GENRES = listOf(
            "Dance & Electronic", "Rock / Indie", "Reggae / Dancehall", "R&B / Soul", "Blues", "Jazz",
        )
        val DEFAULT_SEARCH_TERMS = listOf("House", "Deep House", "Dancehall", "Rock", "Indie Rock")

        /** The songs hearted in Tidal, which Tidal offers as one playable list. */
        val FAVORITE_TRACKS = "MY_COLLECTION_TRACKS" to "Favorite tracks"

        /** Favorite tracks first, then by name, as the watch shows them. */
        fun sortFavorites(list: List<Pair<String, String>>) =
            list.sortedWith(compareBy({ it.first != FAVORITE_TRACKS.first }, { it.second.lowercase() }))
    }

    private val prefs = ctx.getSharedPreferences("config", Context.MODE_PRIVATE)

    var genres: List<String>
        get() = lines("genres", DEFAULT_GENRES)
        set(v) = setLines("genres", v)

    var searchTerms: List<String>
        get() = lines("searchTerms", DEFAULT_SEARCH_TERMS)
        set(v) = setLines("searchTerms", v)

    /** Genre names last read from Tidal, so the settings list shows even when Tidal is slow. */
    var cachedGenres: List<String>
        get() = lines("cachedGenres", DEFAULT_GENRES)
        set(v) = setLines("cachedGenres", v)

    /** Mix names last read from Tidal, so the watch menu loads even when Tidal is slow. */
    var cachedMixes: List<String>
        get() = lines("cachedMixes", (1..8).map { "My Mix $it" })
        set(v) = setLines("cachedMixes", v)

    /** The playlists for the watch's Favorites menu, as Tidal media ID and name, sorted by name. */
    var favorites: List<Pair<String, String>>
        get() = pairs("favorites")
        set(v) = setPairs("favorites", v)

    /** The playlists in the Tidal collection, last read from Tidal, for the picker. */
    var cachedPlaylists: List<Pair<String, String>>
        get() = pairs("cachedPlaylists")
        set(v) = setPairs("cachedPlaylists", v)

    var clientId: String
        get() = prefs.getString("clientId", "")!!
        set(v) = prefs.edit().putString("clientId", v.trim()).apply()

    var clientSecret: String
        get() = prefs.getString("clientSecret", "")!!
        set(v) = prefs.edit().putString("clientSecret", v.trim()).apply()

    /** Country code for the Tidal API; defaults to the SIM card's country, then the phone's region. */
    var country: String
        get() = prefs.getString("country", null)
            ?: ctx.getSystemService(TelephonyManager::class.java)?.simCountryIso?.uppercase()?.ifBlank { null }
            ?: Locale.getDefault().country.ifBlank { "US" }
        set(v) = prefs.edit().putString("country", v.trim().uppercase()).apply()

    var autoShuffle: Boolean
        get() = prefs.getBoolean("autoShuffle", false)
        set(v) = prefs.edit().putBoolean("autoShuffle", v).apply()

    /** Watch app ids registered for the dropped wake-up experiment; WatchLink removes them again. */
    var wakeApps: Set<String>
        get() = prefs.getStringSet("wakeApps", emptySet())!!.toSet()
        set(v) = prefs.edit().putStringSet("wakeApps", v).apply()

    private fun lines(key: String, default: List<String>): List<String> =
        prefs.getString(key, null)?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() } ?: default

    /** ID and name per line, separated by a tab; the ID tells playlists with the same name apart. */
    private fun pairs(key: String): List<Pair<String, String>> =
        lines(key, emptyList()).mapNotNull { line ->
            line.split('\t', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
        }

    private fun setPairs(key: String, v: List<Pair<String, String>>) =
        setLines(key, v.map { "${it.first}\t${it.second.replace('\t', ' ').replace('\n', ' ')}" })

    private fun setLines(key: String, v: List<String>) =
        prefs.edit().putString(key, v.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")).apply()
}
