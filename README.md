<div align="center">

<img src="app/src/main/res/drawable-nodpi/funky_icon.png" alt="FunkyPlayer2000" width="96">

# FunkyPlayer2000 for Android

**The early-2000s music player, now in your pocket.** Windows XP "Luna" look, LED spectrum analyzer, background playback.

</div>

> **Status: stage 2 of 4.** Plays the music on your phone **and from YouTube Music**. Synced lyrics, library folder, playlists and history are coming in the next stages (see the [roadmap](#roadmap)). The desktop version lives in its own repository.

## Features

- Windows XP look and feel: blue title bar, bevelled buttons, zebra-striped lists.
- Plays the music on your device (mp3, flac, ogg, opus, wav, m4a, aac and anything else Android can decode).
- **Background playback** with a media notification, lock-screen controls and headset buttons.
- **Real-time LED spectrum analyzer** (tap it to switch to an oscilloscope). It reads the audio being played, so no microphone permission is needed.
- Library tab: your music grouped by artist and album, with search. Tap a track to play its album from there, `+` to queue it.
- Queue with shuffle, repeat (off / all / one), seek bar and cover art.
- "Add files…" to play any audio file from anywhere via the system file picker.
- **YouTube Music (new in stage 2):** search **songs, albums and playlists**, or paste a link to a song, album or playlist. Tap to play, `+` to queue, "More results" for more. No account or login needed.
  - Local files and YouTube tracks can share the same queue.
  - YouTube audio links expire, so the player asks for a fresh one at the moment each track starts.
  - Covers and artist/album names come from YouTube Music (also shown in the notification).

## Requirements

- **Android 11 (API 30) or newer.**
- To build: Android Studio (Ladybug or newer) **or** just a GitHub account (the included workflow builds the APK in the cloud).

## Get the APK

### Option A: let GitHub build it (no installs needed)

1. Create a repository and upload this whole folder.
2. Open the **Actions** tab and run **Build APK** (it also runs on every push to `main`).
3. When it finishes, open the run and download the **FunkyPlayer2000-debug-apk** artifact (a zip with `app-debug.apk` inside).

### Option B: Android Studio

1. **File → Open** and choose this folder. Wait for the Gradle sync (it downloads Gradle 8.9 by itself).
2. Plug in your phone (USB debugging on) or start an emulator, and press **Run ▶**.
3. For an APK file: **Build → Build Bundle(s) / APK(s) → Build APK(s)**. It ends up in `app/build/outputs/apk/debug/`.

If Android Studio complains about the Gradle wrapper, open **Settings → Build, Execution, Deployment → Gradle** and select Gradle 8.9, or run `gradle wrapper --gradle-version 8.9` once in this folder.

### Option C: command line

```bash
gradle assembleDebug        # needs JDK 17, the Android SDK (platform 35) and Gradle 8.9
```

## Install on your phone

1. Copy `app-debug.apk` to the phone and open it.
2. Android asks you to allow installing apps from this source (your browser or file manager). Allow it for this install.
3. Open **FunkyPlayer2000** and grant the permissions:
   - access to music and audio (needed to list the songs on the phone);
   - notifications (Android 13+, only for the player notification).

The debug build is signed with Android's debug key, which is fine for personal use. Play Protect may show a warning because the app does not come from the Play Store.

## Building notes (stage 2)

- The YouTube library comes from **JitPack**, which builds it on demand. The very first time you build, JitPack can take a few minutes (or time out). If the build fails while downloading `NewPipeExtractor`, just run it again.
- Android 11 and 12 need *core library desugaring* for that library; it is already enabled in `app/build.gradle.kts`.
- If a YouTube track says "No playable audio stream" or never starts, YouTube probably changed something: update the NewPipeExtractor version (see its [releases](https://github.com/TeamNewPipe/NewPipeExtractor/releases)) and rebuild.

## Android 11 and newer

Everything in this project is built for `minSdk 30`:

- music is read through **MediaStore** (Android 11 enforces scoped storage): `READ_EXTERNAL_STORAGE` on Android 11 and 12, `READ_MEDIA_AUDIO` on 13+;
- files you pick with "Add files…" use the system file picker and keep their access permission;
- playback runs in a foreground **media playback** service (declared type on every version);
- the notification permission is only requested on Android 13+.

Phones from some makers (Xiaomi, Huawei, Samsung with aggressive battery saving, etc.) may stop background apps. If playback stops when the screen is off, exclude FunkyPlayer2000 from battery optimization.

## Roadmap

1. **Stage 1:** XP look, local playback, background service, visualizer. ✔
2. **Stage 2 (this one):** YouTube Music search, albums and playlists, and playback.
3. **Stage 3:** synced lyrics (LiriQo + LRCLIB) with word-by-word karaoke highlight.
4. **Stage 4:** remember the queue, playlists, listening history, library folder and saving YouTube Music tracks.

> **License note:** since stage 2 the app uses [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor), which is **GPLv3**, so this Android app is released under **GPLv3** too (see [LICENSE](LICENSE)). The desktop project is a separate codebase and keeps its own license.

## Project layout

```
app/src/main/java/com/funkyplayer2000/
  MainActivity.kt        entry point (edge-to-edge window)
  PlaybackService.kt     ExoPlayer in a media session service + the audio tap + YouTube URL resolver
  YtMusic.kt             YouTube Music search/albums/playlists and stream URLs (NewPipeExtractor)
  PlayerViewModel.kt     connects the screen to the player and holds the queue state
  AudioTap.kt            last played samples + FFT for the visualizer
  MusicLibrary.kt        reads the device music (MediaStore) and file tags
  CoverLoader.kt         cover art (thumbnail or embedded picture)
  ui/Xp.kt               Windows XP widgets (buttons, tabs, seek bar, title bar)
  ui/Visualizer.kt       LED spectrum analyzer / oscilloscope
  ui/PlayerScreen.kt     the whole screen: now playing, transport, queue, library
  ui/YtPane.kt           the YouTube tab (search, results, add/play)
.github/workflows/build-apk.yml   builds the APK on GitHub
```

## Disclaimer

A personal, non-commercial project, **not affiliated with** Microsoft, YouTube, Google or any service mentioned here. The Windows XP look is an homage.

The YouTube Music features use an **unofficial** library (NewPipeExtractor), which can stop working whenever YouTube changes something (updating the library version in `app/build.gradle.kts` usually fixes it). Playing or downloading audio from YouTube may violate YouTube's Terms of Service; use it at your own responsibility and respect the rights of artists. Because of this, the app is not meant for the Google Play Store.

## License

[GNU GPLv3](LICENSE)
