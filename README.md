# Garmin-Tidal Remote

Start Tidal playlists and control playback on your Android phone from a Garmin watch. Useful at the gym or during a workout, when taking the phone out is impractical.

Tidal has no Garmin app, and its public API cannot control playback. So the watch sends commands over the Garmin Connect connection to a small companion app on the phone, and that app tells the Tidal app what to play.

```
Garmin watch (Connect IQ app)
   │  Bluetooth, via the Garmin Connect app
   ▼
Companion app on the phone (Tidal Remote)
   │  Tidal's media interface (the one Android Auto uses)
   ▼
Tidal app → plays the music
```

## What the watch app can do

| Menu item | What happens |
|-----------|--------------|
| **My Mix** | Pick one of your Tidal mixes (My Mix 1–8, My Daily Discovery, …). The list comes from Tidal. |
| **Genre** | Pick a genre. A random playlist from Tidal's page for that genre starts. |
| **Search** | Pick a search term. A random playlist from Tidal's search results starts. |
| **Track radio** | Starts Tidal's radio for the song that is playing. Only shown once the Tidal developer login is set up (see below). |
| **Playback** | Play / Pause, Next track, Previous track, Shuffle on/off. |
| **Song info** | Shows title and artist of the current song. |

After each command, the watch shows the result ("OK" with the playlist or song name, or "Failed" with the reason) and vibrates once for OK, twice for a failure.

The app also appears in the watch's **glance list**, so you can open it during an activity.

## Requirements

- An Android phone with the **Tidal** app (logged in) and the **Garmin Connect** app (paired with the watch).
- A supported Garmin watch (list below): fēnix 7 and newer, epix Gen 2 and Pro, Enduro 3, Forerunner 255 and newer, Venu 3 and 4, vívoactive 5 and 6, Instinct 3 AMOLED.
- For Track radio only: a free developer account at [developer.tidal.com](https://developer.tidal.com).

## Supported watches

The watch app is built for the models below. It was used day-to-day on a **fēnix 7 Pro**; the others were checked in Garmin's simulator (one model per screen size), not on a real watch. Feedback from other models is welcome.

<details>
<summary>Which file for which watch</summary>

| Watch | File |
|-------|------|
| fēnix 7 / quatix 7 | `TidalRemote-fenix7.prg` |
| fēnix 7S | `TidalRemote-fenix7s.prg` |
| fēnix 7X / tactix 7 / quatix 7X Solar / Enduro 2 | `TidalRemote-fenix7x.prg` |
| fēnix 7 Pro | `TidalRemote-fenix7pro.prg` |
| fēnix 7 Pro - Solar Edition (no Wi-Fi) | `TidalRemote-fenix7pronowifi.prg` |
| fēnix 7S Pro | `TidalRemote-fenix7spro.prg` |
| fēnix 7X Pro | `TidalRemote-fenix7xpro.prg` |
| fēnix 7X Pro - Solar Edition (no Wi-Fi) | `TidalRemote-fenix7xpronowifi.prg` |
| fēnix E | `TidalRemote-fenixe.prg` |
| fēnix 8 43mm | `TidalRemote-fenix843mm.prg` |
| fēnix 8 47mm / 51mm / tactix 8 47mm / 51mm / quatix 8 47mm / 51mm | `TidalRemote-fenix847mm.prg` |
| fēnix 8 Pro 47mm / 51mm / MicroLED / quatix 8 Pro 47mm / 51mm | `TidalRemote-fenix8pro47mm.prg` |
| fēnix 8 Solar 47mm | `TidalRemote-fenix8solar47mm.prg` |
| fēnix 8 Solar 51mm / tactix 8 Solar 51mm | `TidalRemote-fenix8solar51mm.prg` |
| fēnix 9 43mm | `TidalRemote-fenix943mm.prg` |
| fēnix 9 47mm / 51mm | `TidalRemote-fenix947mm.prg` |
| fēnix 9 Pro 43mm | `TidalRemote-fenix9pro43mm.prg` |
| fēnix 9 Pro 47mm | `TidalRemote-fenix9pro47mm.prg` |
| fēnix 9 Pro 51mm | `TidalRemote-fenix9pro51mm.prg` |
| fēnix 9 Pro Solar 47mm | `TidalRemote-fenix9prosolar47mm.prg` |
| fēnix 9 Pro Solar 51mm | `TidalRemote-fenix9prosolar51mm.prg` |
| epix (Gen 2) / quatix 7 Sapphire | `TidalRemote-epix2.prg` |
| epix Pro (Gen 2) 42mm | `TidalRemote-epix2pro42mm.prg` |
| epix Pro (Gen 2) 47mm / quatix 7 Pro | `TidalRemote-epix2pro47mm.prg` |
| epix Pro (Gen 2) 51mm / D2 Mach 1 Pro / tactix 7 – AMOLED Edition | `TidalRemote-epix2pro51mm.prg` |
| Enduro 3 | `TidalRemote-enduro3.prg` |
| Forerunner 255 | `TidalRemote-fr255.prg` |
| Forerunner 255 Music | `TidalRemote-fr255m.prg` |
| Forerunner 255s | `TidalRemote-fr255s.prg` |
| Forerunner 255s Music | `TidalRemote-fr255sm.prg` |
| Forerunner 265 | `TidalRemote-fr265.prg` |
| Forerunner 265s | `TidalRemote-fr265s.prg` |
| Forerunner 570 42mm | `TidalRemote-fr57042mm.prg` |
| Forerunner 570 47mm | `TidalRemote-fr57047mm.prg` |
| Forerunner 955 / Solar | `TidalRemote-fr955.prg` |
| Forerunner 965 | `TidalRemote-fr965.prg` |
| Forerunner 970 | `TidalRemote-fr970.prg` |
| Venu 3 | `TidalRemote-venu3.prg` |
| Venu 3S | `TidalRemote-venu3s.prg` |
| Venu 4 41mm | `TidalRemote-venu441mm.prg` |
| Venu 4 45mm / D2 Air X15 | `TidalRemote-venu445mm.prg` |
| vívoactive 5 | `TidalRemote-vivoactive5.prg` |
| vívoactive 6 | `TidalRemote-vivoactive6.prg` |
| Instinct 3 AMOLED 45mm | `TidalRemote-instinct3amoled45mm.prg` |
| Instinct 3 AMOLED 50mm | `TidalRemote-instinct3amoled50mm.prg` |

</details>

Not on the list? See [Adding a watch model](docs/SETUP.md#adding-a-watch-model).

## Installation

Download the files from the [latest release](../../releases/latest).

### 1. Companion app on the phone
1. Download `TidalRemote.apk` on the phone (or copy it there).
2. Open it and allow installing apps from that source when asked. Android may warn that the app is unknown, since it doesn't come from the Play Store.
3. Open **Tidal Remote** and allow notifications.

If Google Play Protect blocks the install ("App blocked to protect your device"): Play Protect stops apps from the browser that ask for sensitive access, and this app needs notification access (see step 3). Either turn off **Play Store → profile picture → Play Protect → ⚙ → Scan apps with Play Protect** while you install it and turn it back on afterwards, or install it with a file manager app that offers its own safety check, or use `adb install TidalRemote.apk`.

### 2. Watch app
1. Find your watch in the list above and download its `.prg` file.
2. Connect the watch to the computer with its USB cable. On a Mac, use [Android File Transfer](https://www.android.com/filetransfer/) or [OpenMTP](https://openmtp.ganeshrvel.com/) to see the watch's files.
3. Copy the `.prg` file into the folder `GARMIN\Apps` on the watch.
4. Unplug the watch. It installs the app; the first start shows a short Connect IQ screen.
5. On the watch, open **Tidal Remote** from the activities and apps list, or from the glance list.

To update later, copy the new `.prg` the same way; your settings stay.

### 3. Phone setup (once)
In the companion app, section **1. Phone setup**:
1. **Allow access to Tidal (notification access)**: switch on Tidal Remote in the list. Tidal only accepts commands from apps with this access.
   - If Android says **"Restricted setting"**: open Settings → Apps → Tidal Remote, tap **⋮** (top right) → **Allow restricted settings**, then switch it on again. Android asks this for apps not installed from the Play Store.
   - Then force-stop Tidal once (Settings → Apps → Tidal → **Force stop**). Tidal remembers an earlier refusal until it restarts.
2. **Allow running in background (battery)**: confirm. Without it, Samsung and other phones may stop the app in the background.

Both lines below the buttons should show ✓. As long as the app can't reach Tidal, a **Getting started** box at the top lists these steps, with buttons to the right settings pages; it disappears once the genres load.

### 4. Track radio (optional)
1. Sign in at [developer.tidal.com](https://developer.tidal.com) with your Tidal account and create an app.
2. Copy its **Client ID** and **Client secret** into section **3. Track radio** of the companion app.
3. Check the **country**: the two-letter code of the country where you signed up for Tidal (CH, DE, GB, BR, US, …). It is pre-filled from the SIM card.
4. Tap **Save**.

## Uninstalling

- **Watch:** on the watch, open **Settings → Activities & Apps → Tidal Remote → Delete**. Don't try it in Garmin Connect on the phone: for sideloaded apps it shows "Unable to Update Settings", and the watch may restart.
- **Phone:** uninstall **Tidal Remote** like any other app. Its settings, including the Tidal developer login, are deleted with it.

## Changing the lists

All lists are edited in the companion app; the watch app never needs a rebuild for this.

- **My Mix**: comes from Tidal automatically.
- **Genres**: tick the genres you want. The list is loaded from Tidal each time you open the app. Ticked genres are shown first, in the order they appear on the watch; a newly ticked genre is added at the end.
- **Search terms**: one per line, any words.

Tap **Save**. The watch picks up the change the next time you close and reopen the watch app.

## Measured delays

Time from the phone receiving a command until Tidal plays the new music, measured on a Samsung S26 Ultra:

| Command | Delay |
|---------|-------|
| My Mix | 1.1–1.8 s (about 6 s if Tidal was not running) |
| Genre | about 2.3 s |
| Search | about 1 s |
| Track radio | about 1.6 s |
| Shuffle, Play / Pause, Next, Previous | under 0.3 s |

The Bluetooth hop from the watch to the phone comes on top; in everyday use it was not noticeable.

## Known limitations

- **Permanent notification.** The companion app runs as a background service, and Android requires a notification for that. Swiping it away can break the connection, so the app posts it again. To hide it, turn off the notification category **"Watch connection"** in the phone's settings (Settings → Apps → Tidal Remote → Notifications); the app keeps working. A mode where Garmin Connect wakes the app for each command was tried and dropped: Garmin Connect 5.30.1 never delivered the messages.
- **Unofficial interface.** The app uses the interface Tidal offers Android Auto. A Tidal update could change it; mix and genre names are matched by text.
- **Notification access is required**, because Tidal only accepts callers that have it.
- **No hot key.** Garmin does not allow Connect IQ apps on the watch's hot keys. Use the glance list instead.
- **Previous track** first jumps to the start of the current song if it has played for a few seconds; press it again for the song before.
- **Auto-shuffle** (Options) switches shuffle on after a new playlist starts. The first song is still the playlist's first; only the songs after it are shuffled.
- **Menu changes** reach the watch only after the watch app is closed and reopened.
- **Track radio** needs the Tidal developer login. Without it, the item is hidden on the watch.

## Troubleshooting

The **Recent commands** section at the bottom of the companion app lists what the watch sent and how it went.

| Watch shows | Likely cause |
|-------------|--------------|
| Phone not reachable | Watch not connected to the phone (Bluetooth, Garmin Connect). |
| No reply from phone | The companion app is not running: open it once, check the battery setting, don't swipe the notification away. |
| Tidal refused (notification access on?) | Notification access is off, or Tidal cached an earlier refusal: switch the access on (allow restricted settings first if Android asks) and force-stop Tidal once. |
| Genre not found / Mix not found | The name no longer matches Tidal: re-tick the genre in the companion app. |
| Nothing playing in Tidal | Track radio and Song info need a song loaded in Tidal. |

## Project layout

| Folder | Content |
|--------|---------|
| `android/` | Companion app (Kotlin, Connect IQ Mobile SDK) |
| `watch/` | Watch app (Monkey C) |
| `docs/SETUP.md` | Development environment, building, testing, sideloading, adding a watch model |
| `docs/PHASE0.md` | Feasibility study: how the phone can control Tidal |

No credentials or keys are stored in the repository: the Tidal login is entered in the companion app, and the Garmin developer key lives outside the repo.

## Building it yourself

Everything needed to build both apps is in this repository; [docs/SETUP.md](docs/SETUP.md) walks through it on Windows (macOS and Linux work the same way with their own paths). In short:

- Phone app: JDK 21 and the Android SDK, then `./gradlew assembleDebug` in `android/`.
- Watch app: the Connect IQ SDK and your own developer key, then `./build-all.sh` in `watch/`.

A watch app you build yourself is signed with your own developer key. That's fine for sideloading, but you can't mix files built with different keys for updates: remove the app from the watch first.

## Licence

MIT, see [LICENSE](LICENSE).

## Disclaimer

Tidal Remote is an independent, non-commercial hobby project. It is not affiliated with, endorsed or sponsored by TIDAL or Garmin. TIDAL and Garmin are trademarks of their respective owners.
