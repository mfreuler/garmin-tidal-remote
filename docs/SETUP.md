# Development environment setup (Windows 11)

Everything below lives in your user folder. Nothing needs admin rights unless noted.
`%LOCALAPPDATA%` is `C:\Users\<you>\AppData\Local`.

## 1. Git
Git for Windows (`C:\Program Files\Git`). Set a repo-local identity, for example with a privacy address:
```
git config user.name "<your name>"
git config user.email "<your address>"
```

## 2. Android tools (portable)

| Tool | Location | Source |
|------|----------|--------|
| adb (platform-tools) | `%LOCALAPPDATA%\Android\Sdk\platform-tools` | https://dl.google.com/android/repository/platform-tools-latest-windows.zip |
| JDK 21 (Eclipse Temurin) | `%LOCALAPPDATA%\Programs\jdk21` | https://adoptium.net (Windows x64 zip) |
| Android command-line tools | `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest` | https://developer.android.com/studio#command-tools |
| SDK platform 36 and build-tools 36.0.0 | `%LOCALAPPDATA%\Android\Sdk` | installed with sdkmanager (below) |
| Gradle 9.8.2 | `%LOCALAPPDATA%\Programs\gradle-9.8.2` | https://gradle.org/releases (only needed to create the wrapper; builds use `gradlew`) |

The repo's install script was a one-off. To redo it by hand:
1. Unzip platform-tools into `%LOCALAPPDATA%\Android\Sdk`.
2. Unzip the JDK into `%LOCALAPPDATA%\Programs\jdk21`.
3. Unzip the command-line tools so that `%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat` exists.
4. Install the SDK packages. Package names contain `;`, which the Windows command shell splits on, so pass them through a file:
   ```powershell
   $env:JAVA_HOME = "$env:LOCALAPPDATA\Programs\jdk21"
   $sdk = "$env:LOCALAPPDATA\Android\Sdk"
   "platforms;android-36`nbuild-tools;36.0.0" | Out-File -Encoding ascii pkgs.txt
   & "$sdk\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root=$sdk --licenses
   & "$sdk\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root=$sdk --package_file=pkgs.txt
   ```

## 3. Phone (Samsung S26 Ultra) for development
1. Settings → About phone → Software information → tap **Build number** 7 times.
2. Settings → Developer options → **USB debugging** on.
3. Connect via USB, allow the PC ("Always allow").
4. Check with `adb devices`.

## 4. Building the companion app (`android/`)
```bash
cd android
export JAVA_HOME="$LOCALAPPDATA/Programs/jdk21"   # Git Bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
`android/local.properties` (not in git) points to the SDK:
```
sdk.dir=C\:/Users/<you>/AppData/Local/Android/Sdk
```

Test a command without the watch (debug build only):
```
adb shell am broadcast -n io.github.jumpypangolin.tidalremote/.DebugReceiver --esa cmd "mix,My Mix 3"
adb logcat -s TidalRemote
```

## 5. Tidal API credentials (for track radio)
1. Log in at https://developer.tidal.com → Dashboard → **Create app**.
2. Copy the Client ID and Client Secret.
3. Enter them in the companion app's settings. For scripts on this PC, store them in `tidal-api.properties` (git-ignored):
   ```
   clientId=...
   clientSecret=...
   ```

## 6. Garmin Connect IQ
| Item | Location |
|------|----------|
| Developer key (RSA 4096, DER) | `%USERPROFILE%\.garmin\developer_key.der` (outside the repo, back it up: apps signed with it can only be updated with the same key) |
| Connect IQ SDK Manager | `%LOCALAPPDATA%\Programs\connectiq-sdk-manager\sdkmanager.exe` (from https://developer.garmin.com/connect-iq/sdk/) |

The developer key was generated with OpenSSL (included in Git for Windows):
```bash
openssl genrsa -out developer_key.pem 4096
openssl pkcs8 -topk8 -inform PEM -outform DER -in developer_key.pem -out developer_key.der -nocrypt
```

Installed (done):
- Connect IQ SDK 9.3.0 via the SDK Manager (after logging in with the Garmin account), with the device files for **fenix7pro** (fēnix 7 Pro, 260×260).
  SDK folder: `%APPDATA%\Garmin\ConnectIQ\Sdks\connectiq-sdk-win-9.3.0-...`
- VS Code (user install via winget) with the **Monkey C** extension from Garmin.

## 7. Building the watch app (`watch/`)
```bash
cd watch
S="$APPDATA/Garmin/ConnectIQ/Sdks/connectiq-sdk-win-9.3.0-2026-10-20-95888f262"
"$LOCALAPPDATA/Programs/jdk21/bin/java" -Xms1g -Dfile.encoding=UTF-8 -jar "$S/bin/monkeybrains.jar" \
  -o bin/TidalRemote.prg -f monkey.jungle -y "$USERPROFILE/.garmin/developer_key.der" -d fenix7pro -w
```
In VS Code the same works with **Monkey C: Build for Device**.

To build one file per supported model (all models in `manifest.xml`) into `watch/dist/`:
```bash
cd watch
JAVA="$LOCALAPPDATA/Programs/jdk21/bin/java" ./build-all.sh          # all models
JAVA="$LOCALAPPDATA/Programs/jdk21/bin/java" ./build-all.sh fr965    # one model
```
The script finds the SDK selected in the SDK Manager and the key in `~/.garmin/developer_key.der`; set `CIQ_SDK` or `DEV_KEY` to override. It runs in Git Bash on Windows, and on macOS or Linux.

### Adding a watch model
1. In the SDK Manager, **Devices** tab, download the model.
2. Find its device ID: the folder name under `%APPDATA%\Garmin\ConnectIQ\Devices` (macOS: `~/Library/Application Support/Garmin/ConnectIQ/Devices`), for example `venux1`. The `displayName` in its `compiler.json` says which watches it covers.
3. Add a line `<iq:product id="venux1"/>` to the product list in `watch/manifest.xml`.
4. Build with `./build-all.sh venux1` and try it in the simulator (section 8).

The model needs Connect IQ 3.2 or newer and glance support for the glance line; the screens scale with the display size. If its screen is 360 pixels or wider, also add its screen family to `watch/monkey.jungle` (the `round-WxH` value in `compiler.json` under `deviceFamily`) so it gets the sharper launcher icon. Models with monochrome or non-round screens (Instinct Solar, Edge) are untested and may need layout changes in `ResultView.mc`.

The companion app on the phone needs no change for a new model.

## 8. Testing with the simulator
The simulator can talk to the real companion app on the phone over USB:
1. Start `%APPDATA%\Garmin\ConnectIQ\Sdks\<sdk>\bin\simulator.exe`.
2. Run the app in it (PowerShell, with `$S` set to the SDK folder): `& "$S\bin\monkeydo.bat" bin\TidalRemote.prg fenix7pro`
3. Forward the simulator port to the phone: `adb forward tcp:7381 tcp:7381`
4. In the simulator: menu **Connection → adb Connection → Start** (redo after reconnecting the phone).
5. Switch the companion app (debug build) to simulator mode:
   `adb shell am broadcast -n io.github.jumpypangolin.tidalremote/.DebugReceiver --ez tethered true`
   and back with `--ez tethered false`.

## 9. Installing on the watch (sideloading)
1. Connect the watch by USB; it appears in Windows Explorer as a portable device.
2. Copy the `.prg` for your model (`watch/bin/TidalRemote.prg`, or `watch/dist/TidalRemote-<model>.prg`) into `GARMIN\Apps`.
3. Unplug the watch; it installs or updates the app. The `.prg` then disappears from the folder, and the app's stored data lives in `GARMIN\Apps\DATA`.
