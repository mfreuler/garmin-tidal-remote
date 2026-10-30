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

    private fun setLines(key: String, v: List<String>) =
        prefs.edit().putString(key, v.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")).apply()
}
