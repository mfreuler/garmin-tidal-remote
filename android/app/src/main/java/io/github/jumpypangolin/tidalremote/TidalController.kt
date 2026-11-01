package io.github.jumpypangolin.tidalremote

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TidalException(message: String) : Exception(message)

/**
 * Drives the Tidal app through its media browser (the interface Android Auto uses).
 * All functions must be called on the main thread.
 */
class TidalController(private val ctx: Context) {

    companion object {
        const val TIDAL = "com.aspiro.tidal"
        private val SERVICE = ComponentName(TIDAL, "com.aspiro.wamp.player.MusicService")
        private const val HOME = "HOME_V2::home_page_v2_id"
        private const val MIXES_PAGE = "HOME_V2::home/pages/DAILY_MIXES/view-all"
        private const val GENRES_PAGE = "DYNAMIC::pages/genre_page"
        private const val PLAYLISTS = "MY_COLLECTION_PLAYLIST"
        private const val PLAYLIST_PAGE = 25 // Must match "Next 25" on the watch.
        private const val ACTION_SHUFFLE = "com.aspiro.tidal.player.action.TOGGLE_SHUFFLE"
        private const val ICON_SHUFFLE_ON = "ic_shuffle_active"
        private const val ACTION_FAVORITE = "com.aspiro.tidal.player.action.TOGGLE_FAVORITE"
        private const val ICON_FAVORITE_ON = "ic_favorite_filled"
        private const val CONNECT_TIMEOUT = 10_000L
        private const val PLAY_TIMEOUT = 10_000L
    }

    /** Names of the mixes on Tidal's "Custom mixes" page (My Mix 1..8 and others). */
    suspend fun mixNames(): List<String> = session { b, _ -> mixes(b).map { title(it) } }

    /** Names of the genres on Tidal's Genres page. */
    suspend fun genreNames(): List<String> = session { b, _ -> children(b, GENRES_PAGE).map { title(it) } }

    suspend fun playMix(name: String, autoShuffle: Boolean): String = session { b, c ->
        val mix = mixes(b).firstOrNull { title(it).equals(name, ignoreCase = true) }
            ?: throw TidalException("Mix not found: $name")
        play(c, mix.mediaId!!, autoShuffle)
        title(mix)
    }

    /** Plays a random playlist from the Tidal genre page with this name. */
    suspend fun playGenre(name: String, autoShuffle: Boolean): String = session { b, c ->
        val genre = children(b, GENRES_PAGE).firstOrNull { title(it).equals(name, ignoreCase = true) }
            ?: throw TidalException("Genre not found: $name")
        val pick = children(b, genre.mediaId!!).filter { isPlaylist(it) }.randomOrNull()
            ?: throw TidalException("No playlists for $name")
        play(c, pick.mediaId!!, autoShuffle)
        title(pick)
    }

    /** Plays a random playlist from Tidal's search results for this term. */
    suspend fun playSearch(term: String, autoShuffle: Boolean): String = session { b, c ->
        val pick = search(b, term).filter { isPlaylist(it) }.randomOrNull()
            ?: throw TidalException("No playlists for \"$term\"")
        play(c, pick.mediaId!!, autoShuffle)
        title(pick)
    }

    /** One page of playlist names from the user's Tidal collection, and whether more follow. */
    suspend fun playlistPage(offset: Int): Pair<List<String>, Boolean> = session { b, _ ->
        val list = playlists(b)
        list.drop(offset).take(PLAYLIST_PAGE).map { title(it) } to (list.size > offset + PLAYLIST_PAGE)
    }

    /** Plays a collection playlist. The position tells playlists with the same name apart. */
    suspend fun playPlaylist(index: Int, name: String, autoShuffle: Boolean): String = session { b, c ->
        val list = playlists(b)
        val pick = list.getOrNull(index)?.takeIf { title(it) == name }
            ?: list.firstOrNull { title(it) == name }
            ?: throw TidalException("Playlist not found: $name")
        play(c, pick.mediaId!!, autoShuffle)
        title(pick)
    }

    /** Media ID (Tidal track ID) and title of the track currently loaded in Tidal. */
    suspend fun currentTrack(): Pair<String, String> = session { _, c ->
        val md = c.metadata ?: throw TidalException("Nothing playing in Tidal")
        val id = md.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
            ?: throw TidalException("Nothing playing in Tidal")
        id to (md.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: "")
    }

    /** Plays a Tidal mix by ID, for example a track radio. */
    suspend fun playMixId(mixId: String, autoShuffle: Boolean) = session { _, c ->
        play(c, "MIX::$mixId", autoShuffle)
    }

    /** Toggles shuffle; returns the new state. */
    suspend fun toggleShuffle(): Boolean = session { _, c ->
        val before = shuffleOn(c)
        c.transportControls.sendCustomAction(ACTION_SHUFFLE, Bundle())
        repeat(20) {
            delay(150)
            if (shuffleOn(c) != before) return@session !before
        }
        throw TidalException("Shuffle did not change")
    }

    /** Adds or removes the current song; returns false if it already was in that state. */
    suspend fun setFavorite(on: Boolean): Boolean = session { _, c ->
        if (c.metadata == null) throw TidalException("Nothing playing in Tidal")
        if (actionIcon(c, ACTION_FAVORITE) == null) throw TidalException("Tidal has no favorite button here")
        if (isFavorite(c) == on) return@session false
        c.transportControls.sendCustomAction(ACTION_FAVORITE, Bundle())
        repeat(20) {
            delay(150)
            if (isFavorite(c) == on) return@session true
        }
        throw TidalException("Favorite did not change")
    }

    /** Pauses if playing, otherwise plays; returns the new state. */
    suspend fun playPause(): String = session { _, c ->
        if (c.playbackState?.state == PlaybackStateCompat.STATE_PLAYING) {
            c.transportControls.pause()
            "Paused"
        } else {
            c.transportControls.play()
            // After a night Tidal may have nothing left to resume; play() then silently does nothing.
            repeat(20) {
                delay(150)
                if (c.playbackState?.state == PlaybackStateCompat.STATE_PLAYING) return@session "Playing"
            }
            throw TidalException("Nothing to resume. Pick a mix")
        }
    }

    /** Title and artist of the current track, on two lines. */
    suspend fun nowPlaying(): String = session { _, c -> songInfo(c) ?: throw TidalException("Nothing playing in Tidal") }

    /** Skips to the next or previous track; returns the new title and artist. */
    suspend fun skip(forward: Boolean): String = session { _, c ->
        val before = c.metadata?.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
        if (forward) c.transportControls.skipToNext() else c.transportControls.skipToPrevious()
        // "Previous" may just restart the current track, so a missing change is not an error.
        for (i in 1..15) {
            delay(150)
            if (c.metadata?.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID) != before) break
        }
        if (forward && c.metadata?.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID) == before &&
            c.playbackState?.state != PlaybackStateCompat.STATE_PLAYING
        ) throw TidalException("Nothing playing. Pick a mix")
        songInfo(c) ?: "Skipped"
    }

    // --- internals ---

    private suspend fun <T> session(block: suspend (MediaBrowserCompat, MediaControllerCompat) -> T): T {
        val browser = withTimeout(CONNECT_TIMEOUT) { connect() }
        try {
            return block(browser, MediaControllerCompat(ctx, browser.sessionToken))
        } finally {
            browser.disconnect()
        }
    }

    private suspend fun connect(): MediaBrowserCompat = suspendCancellableCoroutine { cont ->
        lateinit var browser: MediaBrowserCompat
        browser = MediaBrowserCompat(ctx, SERVICE, object : MediaBrowserCompat.ConnectionCallback() {
            override fun onConnected() {
                if (cont.isActive) cont.resume(browser)
            }

            override fun onConnectionFailed() {
                if (cont.isActive) cont.resumeWithException(TidalException("Tidal refused (notification access on?)"))
            }
        }, null)
        cont.invokeOnCancellation { browser.disconnect() }
        browser.connect()
    }

    private suspend fun children(b: MediaBrowserCompat, id: String): List<MediaItem> =
        withTimeout(CONNECT_TIMEOUT) {
            suspendCancellableCoroutine { cont ->
                b.subscribe(id, object : MediaBrowserCompat.SubscriptionCallback() {
                    override fun onChildrenLoaded(parentId: String, children: List<MediaItem>) {
                        b.unsubscribe(id)
                        if (cont.isActive) cont.resume(children)
                    }

                    override fun onError(parentId: String) {
                        if (cont.isActive) cont.resumeWithException(TidalException("Tidal could not load $id"))
                    }
                })
            }
        }

    private suspend fun search(b: MediaBrowserCompat, query: String): List<MediaItem> =
        withTimeout(CONNECT_TIMEOUT) {
            suspendCancellableCoroutine { cont ->
                b.search(query, null, object : MediaBrowserCompat.SearchCallback() {
                    override fun onSearchResult(query: String, extras: Bundle?, items: List<MediaItem>) {
                        if (cont.isActive) cont.resume(items)
                    }

                    override fun onError(query: String, extras: Bundle?) {
                        if (cont.isActive) cont.resumeWithException(TidalException("Tidal search failed"))
                    }
                })
            }
        }

    /** Mixes on the Custom mixes page; falls back to "My Mix" entries on Home. */
    private suspend fun mixes(b: MediaBrowserCompat): List<MediaItem> {
        var page = children(b, MIXES_PAGE).filter { it.mediaId?.startsWith("MIX::") == true }
        if (page.isEmpty()) {
            // Right after Tidal starts the page can come back empty; Home then only has My Mix 1-4.
            delay(2000)
            page = children(b, MIXES_PAGE).filter { it.mediaId?.startsWith("MIX::") == true }
        }
        if (page.isNotEmpty()) return page
        return children(b, HOME)
            .filter { it.mediaId?.startsWith("MIX::") == true && title(it).startsWith("My Mix") }
            .distinctBy { it.mediaId }
    }

    private suspend fun playlists(b: MediaBrowserCompat): List<MediaItem> {
        val list = children(b, PLAYLISTS).filter { isPlaylist(it) }
        if (list.isNotEmpty()) return list
        // Same as the mixes: right after Tidal starts the page can come back empty.
        delay(2000)
        return children(b, PLAYLISTS).filter { isPlaylist(it) }
    }

    /** Starts playback and waits until Tidal reports the new queue playing. */
    private suspend fun play(c: MediaControllerCompat, mediaId: String, autoShuffle: Boolean) {
        val before = c.metadata?.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID) to c.queueTitle?.toString()
        c.transportControls.playFromMediaId(mediaId, Bundle())
        val deadline = System.currentTimeMillis() + PLAY_TIMEOUT
        while (System.currentTimeMillis() < deadline) {
            delay(200)
            val now = c.metadata?.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID) to c.queueTitle?.toString()
            if (c.playbackState?.state == PlaybackStateCompat.STATE_PLAYING && now != before) {
                if (autoShuffle && !shuffleOn(c)) {
                    // Tidal resets shuffle on every new queue. The first track stays: skipping it was audible.
                    c.transportControls.sendCustomAction(ACTION_SHUFFLE, Bundle())
                }
                return
            }
        }
        throw TidalException("Tidal did not start playing")
    }

    /** Tidal does not report shuffle mode; its shuffle button icon does. */
    private fun shuffleOn(c: MediaControllerCompat) = actionIcon(c, ACTION_SHUFFLE) == ICON_SHUFFLE_ON

    /** Same for favorites: the button's text depends on the phone language, its icon doesn't. */
    private fun isFavorite(c: MediaControllerCompat) = actionIcon(c, ACTION_FAVORITE) == ICON_FAVORITE_ON

    private fun actionIcon(c: MediaControllerCompat, id: String): String? {
        val action = c.playbackState?.customActions?.firstOrNull { it.action == id } ?: return null
        return try {
            ctx.packageManager.getResourcesForApplication(TIDAL).getResourceEntryName(action.icon)
        } catch (e: Exception) {
            null
        }
    }

    private fun songInfo(c: MediaControllerCompat): String? {
        val md = c.metadata ?: return null
        val title = md.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: return null
        val artist = md.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
        return if (artist.isNullOrBlank()) title else "$title\n$artist"
    }

    private fun isPlaylist(it: MediaItem) = it.mediaId?.startsWith("PLAYLIST::") == true

    private fun title(it: MediaItem) = it.description.title?.toString() ?: ""
}
