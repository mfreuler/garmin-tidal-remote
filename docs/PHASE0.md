# Phase 0 result: how to make Tidal play something from a background app

Tested 2026-10-28 on a Samsung S26 Ultra (SM-S948B), Android 17, Tidal 2.215.0.
The throwaway test app used for these experiments is not part of the published repository.
Every command was sent to a background app, the way the companion app will get a message from the watch.

## Summary

**One method works reliably: the MediaBrowser connection (method 2), the same interface Android Auto uses.**
It works with the screen locked, with Tidal closed, playing or paused, and no window opens on the phone.

| # | Method | Result |
|---|--------|--------|
| 1 | MediaController on Tidal's active session (`playFromSearch` / `playFromUri`) | Not needed. It only works while Tidal is already running and needs notification-listener access. Method 2 covers the same commands without those limits. |
| 2 | **MediaBrowser connection to Tidal's `MusicService`** | **Works.** Tidal accepts third-party apps, so we get the same menu as Android Auto. `playFromMediaId` and `playFromSearch` both start playback. |
| 3 | Intent `MEDIA_PLAY_FROM_SEARCH` | **Fails.** From the background Android blocks it ("Abort background activity starts"). Even when allowed (via adb), Tidal only opens its player and ignores the search. |
| 4 | Deep link `https://tidal.com/browse/mix/<id>` | **Fails.** Blocked from the background, same as method 3. |

## Method 2 details

Tidal's Android Auto menu (`ROOT_AUTO`) offers:

- **Home → Custom mixes**: My Daily Discovery, **My Mix 1 to 8**, My New Arrivals, each with an ID such as `MIX::<id>`.
  The companion app can therefore read the current My Mix list from Tidal itself. You don't need to copy share links.
- **Explore → genre pages**: Hip-Hop, Pop&Rock, R&B/Soul, Indie&Alternative, Dance&Electronic, Country&Folk, Classical, Global, Jazz, Blues, Metal.
  Each page holds about 10 Tidal-curated playlists (for example Dance&Electronic → "Rave 24/7", "Fresh House", ...).
- Your own playlists, under Collection and Home.

Ways to start music:

| Command | Example | Result |
|---------|---------|--------|
| `playFromMediaId("MIX::…")` | My Mix 1 to 5 | Plays that mix |
| `playFromMediaId("PLAYLIST::…")` | Rave 24/7, Fresh House | Plays that playlist |
| `playFromSearch("hip hop")` | | "Hip-Hop Classics" playlist, good |
| `playFromSearch("jazz")` | | Miles Davis, good |
| `playFromSearch("techno")` | | An artist literally named "Techno", **poor** |

Free-text search matches names, not genres, so its quality depends on the word used.
For "Random genre", a more reliable option is to pick a random playlist from Tidal's genre page, or from a configured list of playlists per genre.

## Delay (phone side only, command received until new track playing)

| Situation | Screen | Delay |
|-----------|--------|-------|
| Tidal playing something else | locked | ~1.0 s |
| Tidal paused | locked | ~1.0 s |
| Tidal not running (force-stopped) | locked | ~2.8 s |

The phone stayed locked throughout and nothing appeared on screen.
The Bluetooth hop from the watch comes on top of this (measured in Phase 4).

## Risks and open points

- **Unofficial interface.** Tidal built this for Android Auto, not for us. A Tidal update could restrict which apps may connect, or rename IDs.
  Mitigation: look up mixes by name ("My Mix 3") at runtime instead of storing IDs, and report a clear error to the watch if the connection is refused.
- In the tests, adb triggered the app. In Phase 2 the trigger is a Connect IQ message. Connecting to a media service is not a restricted background action, so we expect the same result, but this must be confirmed.
- Samsung battery optimisation may freeze the companion app. Handled in Phase 2.
- Tasker was not tested. The custom app path works without third-party plugins, so it is the simpler and more reliable choice.

## Tidal genres (Explore → Genres, page `DYNAMIC::pages/genre_page`)

| Genre | Page ID |
|-------|---------|
| Blues | `genre_blues` |
| Classical | `genre_classical` |
| Country | `genre_country` |
| Dance & Electronic | `genre_dance_electronic` |
| Folk / Americana | `genre_americana` |
| Global | `genre_world` |
| Gospel / Christian | `genre_gospel` |
| Hip-Hop | `genre_hip_hop` |
| Jazz | `genre_jazz` |
| K-Pop | `genre_kpop` |
| Kids | `genre_kids` |
| Latin | `genre_latin` |
| Legacy (older music) | `genre_retro` |
| Metal | `genre_metal` |
| Pop | `genre_pop` |
| R&B / Soul | `genre_rnb` |
| Reggae / Dancehall | `genre_reggae` |
| Rock / Indie | `genre_indierock` |

Each page holds about 90 playlists plus albums, for example Dance & Electronic: "Rave 24/7", "Electronic Workout", "Trance Classics", but also "Zen Garden" and "Study Session".
A random pick from a whole page can therefore land on calm music. Subgenres such as techno or house have no page of their own.

## Search results as a list (for "random playlist for a search term")

Tidal reports `SEARCH_SUPPORTED=true`. With the AndroidX media library (`MediaBrowserCompat.search`) we get the result list:
for "techno", 26 items (artists, albums, tracks, videos, and **5 playlists**: Techno Essentials, TECH PACK, Dub Techno, Black Techno Excellence, Hard Techno).
Picking a random `PLAYLIST::` result and playing it with `playFromMediaId` worked with the screen locked ("techno" → Dub Techno, 1.3 s; "deep house" → Fresh House, 1.0 s).
The pool per search term is small (about 5 playlists), but every one of them matches the term.

Playlists on the chosen genre pages: Blues 19, Jazz 97, Dance & Electronic 91, R&B / Soul 99, Reggae / Dancehall 61, Rock / Indie 49.

## Shuffle

- The standard `setShuffleMode` call is ignored. Tidal uses its own custom action `com.aspiro.tidal.player.action.TOGGLE_SHUFFLE` (a toggle).
- The current state can be read from the action's icon name in Tidal's resources: `ic_shuffle` (off) or `ic_shuffle_active` (on).
- **Every `playFromMediaId` resets shuffle to off** and starts at track 1.
- Toggling shuffle on reshuffles the queue but keeps the current track first.
- Decision: **"Shuffle on/off" becomes its own watch function.** It sends TOGGLE_SHUFFLE, then reports the new state (from the icon name) back to the watch.
- Optional companion setting **"Auto-shuffle new playlists"** (default off): play → if icon is `ic_shuffle`, send TOGGLE_SHUFFLE → skip to next.
  It is off by default because three chained commands are more likely to fail when Tidal is slow, for example on a cold start.

## Track radio ("radio based on the current song")

- Tidal offers the custom action `PLAY_TRACK_MIX` ("Go to track radio"), but it **crashes Tidal** (`IllegalArgumentException: Required value was null` in `MusicService.onCustomAction`) when playback was started through the media browser. Not usable.
- `TRACK_MIX::<id>` and similar guessed media IDs do not work.
- `playFromMediaId("ARTIST::<id>")` plays the artist's top tracks, not a radio.
- **Works:** the official TIDAL API with **client credentials** (no user login):
  1. Get a token: `POST https://auth.tidal.com/v1/oauth2/token` with `grant_type=client_credentials` and Basic auth (client ID and secret). The token is valid for 4 h.
  2. Read the current track ID from the session metadata (`android.media.metadata.MEDIA_ID`, for example `561360698`).
  3. `GET https://openapi.tidal.com/v2/tracks/{id}/relationships/radio?countryCode=CH` returns `{"id":"0018b81d…","type":"playlists"}`, a mix (`playlistType: MIX`).
  4. `playFromMediaId("MIX::<id>")` plays the track radio.
- Tested with the screen locked: WYWD → radio with salute, BLOND:ISH, ANOTR, …; Moon Dreams (Miles Davis) → Miles Davis radio. The API call took about 0.3 s.
- The radio starts by playing the current song again from the beginning. Optionally skip to next.
- Needs a free app registration at developer.tidal.com. The client ID and secret belong in the companion app's settings, not in the code, so each user can enter their own. Locally they are stored in `tidal-api.properties` (git-ignored). The phone needs internet access (it already needs it for streaming).

## Recommendation

Build the companion app on **method 2 (MediaBrowser → `playFromMediaId`)**:

- **My Mix 1 to 8:** look up the mix ID under Home → Custom mixes by name, then play it.
- **Genre:** pick a genre from your list on the watch (Blues, Jazz, Dance & Electronic, R&B / Soul, Reggae / Dancehall, Rock / Indie), then play a random playlist from its Tidal genre page.
- **Search term:** pick a term from your list on the watch, run a Tidal search, then play a random playlist from the results.

No notification-listener permission is needed for this.
