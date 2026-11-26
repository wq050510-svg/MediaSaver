# MediaSaver — Project Architecture & Engineering Guidelines

## Overview
**MediaSaver** is a personal-use, pure on-device Android application (no backend server) that parses share links from **Douyin (抖音)**, **Bilibili (B站)**, **X (Twitter)**, and **Instagram**, extracts metadata/previews, and downloads videos, audio, single images, carousels, and image-text notes directly to the device gallery.

## Technical Stack & Hard Constraints
1. **UI & Architecture**: Kotlin + Jetpack Compose (Material 3, Edge-to-Edge, System Light/Dark Theme) + Room + Jetpack DataStore Preferences + WorkManager + Coil.
2. **Python Dual-Engine (Chaquopy)**:
   - Chaquopy Gradle plugin with Python 3.12 (`arm64-v8a` only).
   - Embedded pip packages: `yt-dlp`, `gallery-dl`, `requests`.
3. **FFmpeg Integration (Android 10+ W^X Compliant)**:
   - NEVER execute binaries located in `filesDir` or `cacheDir`.
   - Place `libffmpeg.so` under `app/src/main/jniLibs/arm64-v8a/libffmpeg.so`.
   - Enable `packaging { jniLibs { useLegacyPackaging = true } }` in `app/build.gradle.kts`.
   - At runtime, verify `${context.applicationInfo.nativeLibraryDir}/libffmpeg.so`:
     - If present: pass its path to `yt-dlp` via `ffmpeg_location` for Bilibili DASH video+audio muxing (`bestvideo+bestaudio/best`).
     - If absent: show "未检测到 FFmpeg" in Settings and gracefully degrade Bilibili downloads to single-stream `best` format without crashing.
4. **Threading & Security**:
   - All Python calls, network requests, and file I/O MUST execute on `Dispatchers.IO` or inside `WorkManager` workers; never block the main thread.
   - NEVER hardcode cookies, proxy addresses, tokens, or secrets.

## Package Structure (Clean Architecture)
```text
app/src/main/java/com/personal/mediasaver/
├── data/
│   ├── local/        # Room database (DownloadHistoryEntity, DAO), DataStore (Proxy & App Settings)
│   └── engine/       # Chaquopy Kotlin-to-Python bridge (MediaEngineBridge), MediaStoreExporter
├── domain/
│   ├── model/        # Platform, MediaType, MediaItemType, ParsedMedia, MediaItem, DownloadTask, EngineHealth
│   └── sanitizer/    # UrlSanitizer (dirty share text URL extraction, platform detection, tracking param removal)
├── worker/           # DownloadWorker (WorkManager foreground notification download & gallery export)
└── ui/
    ├── theme/        # Material 3 Edge-to-Edge dynamic/dark/light theme
    ├── share/        # Transparent ShareReceiverActivity + ModalBottomSheet parse preview & selection
    ├── main/         # MainActivity & HomeScreen (clipboard auto-detect/paste bar + filterable history list)
    └── settings/     # Split-tunnel proxy config, engine versions & FFmpeg status

app/src/main/python/
├── router.py          # Unified Kotlin entry point: health_check(), parse(), download()
├── ytdlp_adapter.py   # yt-dlp wrapper (parse, download with progress callback, FFmpeg muxing)
├── gallery_adapter.py # gallery-dl wrapper (carousels, mixed image/video posts)
└── douyin_note.py     # Lightweight Douyin /note/ image-text parser (HD images + background audio with cookies)
```

## Python -> Kotlin JSON Data Contract
All Python endpoints return a JSON string with this schema:
```json
{
  "status": "ok | error",
  "platform": "DOUYIN | BILIBILI | X | INSTAGRAM | UNKNOWN",
  "title": "string",
  "author": "string",
  "media_type": "VIDEO | GALLERY | MIXED",
  "items": [
    {
      "url": "string",
      "type": "VIDEO | IMAGE | AUDIO",
      "thumbnail": "string",
      "quality": "string"
    }
  ],
  "error_message": "string"
}
```
For `download(...)`, an additional `files` array (`[{"path": "...", "type": "VIDEO|IMAGE|AUDIO", "mime_type": "..."}]`) is included on success so Kotlin's `MediaStoreExporter` can save each file into `Movies/MediaSaver`, `Pictures/MediaSaver`, or `Music/MediaSaver` and clean up `cacheDir/downloads/{taskId}/`.

## Verification Commands
- Unit Tests: `./gradlew testDebugUnitTest`
- Debug APK Build: `./gradlew assembleDebug`
