# Ferrocine for Android TV

A native Android TV and Google TV client for [Ferrocine](https://github.com/sebastka/ferrocine),
the self-hosted media server. Built for the remote: Compose for TV, Media3 (ExoPlayer).

- **Quick Connect sign-in.** The TV shows a code and a QR code; approve it from the web UI on your
  phone or computer (`/pair`). The TV then holds a token of its own, listed under your devices in the
  web UI, where it can be signed out. No password is ever typed on the TV.
- **Plays what the TV can.** The app reports the TV's decoders, its HDMI sink's passthrough formats
  (AC-3, E-AC-3, DTS, TrueHD) and its display's HDR formats; the server direct-plays when it can,
  remuxes or transcodes when it must, and tone-maps HDR formats the display lacks.
- **Player:** resume, audio and subtitle choice (text subtitles as WebVTT, bitmap ones drawn by the
  player or burned in by the server), seeking with preview frames and chapter names, progress saved
  on the server, next episode.
- **Browsing:** home (continue watching, next up, latest), libraries, search by title or cast and
  crew, series with their seasons, extras.

Requires a Ferrocine core with Quick Connect (devices, `/api/v1/pair`) and the matching web UI.

## Install

Each [release](https://github.com/sebastka/ferrocine-androidtv/releases) carries a signed APK. On
the TV, install it with the Downloader app from its URL, or from a computer:

```sh
adb connect <tv-address>:5555        # after enabling network debugging on the TV
adb install ferrocine-androidtv-YYYY-MM-DD.apk
```

Updates install over the previous version (same signing key). Then open Ferrocine, enter your
server's address (as you open it in a browser) and approve the code shown.

Plain `http://` works on a home network, but the device token then travels unencrypted. Servers
with a certificate from a CA installed on the TV are trusted.

## Development

Builds need Docker only: `scripts/gradle` runs Gradle in an image with JDK 21 and the Android SDK
(`docker/Dockerfile`), caching Gradle and the debug signing key in Docker volumes.

```sh
scripts/gradle assembleDebug testDebugUnitTest lintDebug
```

`scripts/emulator` runs a headless Google TV emulator (Android 14, as on the Google TV Streamer) in
Docker, which needs `/dev/kvm`. It shares the host's network: the host is `10.0.2.2` in it.

```sh
scripts/emulator start                         # boot (a few minutes the first time)
scripts/emulator install                       # install the debug APK and launch it
scripts/emulator adb shell input keyevent DPAD_RIGHT
scripts/emulator shot screen.png
scripts/emulator stop
```

The emulator lacks the Dolby decoders of real TVs: AC-3 audio is converted by the server there.

### Layout

| Path | What |
|---|---|
| `api/` | The core's JSON API: models and an OkHttp client |
| `FerrocineApp.kt` | The account (server, device token) and the shared HTTP client, which adds the token for the server's requests (images, streams) |
| `ui/` | Setup (server, Quick Connect), browsing, details |
| `player/` | Device capabilities, the player screen, and its pure logic (`PlayerLogic.kt`, unit-tested) |

### Releases

Run the Release workflow by hand on `master`: it tags the commit `YYYY-MM-DD` (`.1`, `.2`… later
the same day), builds the APK signed with the release key (repository secrets `ANDROID_KEYSTORE`,
base64, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`), attests it, and
publishes it in a GitHub release. The version code is the date (`2026102401`), so releases always
upgrade.

## License

[MIT](LICENSE)
