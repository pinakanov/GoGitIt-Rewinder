# Rewinder architecture

Kotlin Multiplatform + Compose Multiplatform. One codebase for logic **and** UI;
Android and iOS each add only a thin platform layer.

```
Rewinder/
├── shared/                          ONE codebase for both apps (Kotlin Multiplatform)
│   └── src/
│       ├── commonMain/              ← edit here and Android + iOS both change
│       │   ├── App.kt, ui/          All screens (Compose Multiplatform)
│       │   ├── RewinderController   Session: Start, Stop, Rewind, queue, mic checks
│       │   ├── RewinderGraph        onDeviceGraph(): the one wiring both apps use
│       │   ├── core/                5 min ring buffer, gain, product rules
│       │   ├── pipeline/            Sensitive filter, prompt, parser, cleaner
│       │   ├── ai/                  OnDeviceTranscriber / OnDeviceSummarizer + engine interfaces
│       │   ├── models/              ModelCatalog (URLs), ModelStore (download, readiness, remove)
│       │   ├── settings/            AppSettings: summary model + transcription language (persisted)
│       │   └── audio/, data/        Interfaces, recents
│       ├── commonTest/              Tests for all of the above
│       ├── androidMain/             AndroidAudioSource (AudioRecord)
│       └── iosMain/                 Bridges.kt (what Swift implements), adapters, MainViewController
├── androidApp/                      Android shell: Activity, foreground service, engines, file/HTTP
├── whisper/, llama/                 Android: whisper.cpp / llama.cpp compiled via CMake + JNI
└── iosApp/                          iOS shell (Xcode project)
    ├── iosApp/*BridgeImpl.swift     Mic (AVAudioEngine), Whisper, Llama, downloader
    └── NativeEngines/Package.swift  Prebuilt whisper/llama .xcframeworks (Swift Package)
```

## Where code goes (one change, both apps)

| You want to change | Edit | Platforms affected |
| --- | --- | --- |
| Any screen, colors, text, layout | `shared/src/commonMain/.../ui/` | Android + iOS |
| Rewind rules, queue, buffer, mic checks | `RewinderController.kt`, `core/` | Android + iOS |
| Prompt, summary format, sensitive filter | `pipeline/` | Android + iOS |
| Model choice, URLs, download logic | `models/ModelCatalog.kt`, `models/ModelStore.kt` | Android + iOS (also update `scripts/models.sh`) |
| Model selector / Settings options | `ai/AiControls.kt`, `ui/SettingsScreen.kt` (rows in `ui/ModelPicker.kt`) | Android + iOS |
| Look (cassette recorder) | `ui/Theme.kt` (`Deck` palette), `ui/DeckParts.kt` (body, LCD, cassette, VU, keys) | Android + iOS |
| How audio is captured | `shared/src/androidMain` / `iosApp/iosApp/MicBridgeImpl.swift` | one platform |
| Lock screen surface | `androidApp/RewinderService.kt` / (iOS Live Activity, todo) | one platform |
| Engine glue | `whisper/`, `llama/` (Android) / `*BridgeImpl.swift` (iOS) | one platform |

Rule of thumb: if it is not about talking to the OS (mic, notifications, files, native
libraries), it belongs in `commonMain`. Platform code only implements small interfaces
(`AudioSource`, `SessionHooks`, `SpeechEngine`, `TextEngine`, `ModelStorage`,
`ModelDownloader`), so there is nothing to keep in sync by hand.


## How a rewind flows

1. **Start**: `RewinderController.start()` calls `SessionHooks.onSessionStarted()`
   (Android: microphone foreground service + ongoing notification) and collects
   `AudioSource.frames()` into `AudioRingBuffer` (16 kHz mono PCM16, 5 min, memory only).
2. While running, `RewinderState.bufferedMillis` counts audio in memory (capped at 5 min).
   **Rewind** is disabled until it reaches 3:00 (`RewindPolicy`).
3. **Rewind**: snapshot the whole buffer at that instant (windows overlap; nothing is cut),
   then `RewindPipeline`: auto-gain, transcribe, zero-fill PCM, filter sensitive details,
   summarize. Rewind can be tapped again while earlier ones are processing; snapshots queue
   and run one at a time behind a mutex, so only one model is ever loaded. Each result is
   saved as a `Recent` and the UI opens it.
4. **Stop**: capture is cancelled, the buffer is zero-filled, the service stops.

Every buffer access runs on one single-threaded dispatcher, so the ring buffer has no locks.

## Platform seams (the only things to write per platform)

| Seam | Android (done) | iOS (todo) |
| --- | --- | --- |
| `AudioSource` | `AndroidAudioSource` (AudioRecord, probes sources, reopens after calls) | `MicBridgeImpl` (AVAudioEngine to 16 kHz Int16, restarts after interruptions) |
| Phone audio | `AndroidDeviceAudioSource` (AudioPlaybackCapture via MediaProjection, mixed with the mic by `MixedAudioSource`) | `DeviceAudioBridgeImpl` + `RewinderBroadcast` ReplayKit extension, 16 kHz mono Int16 over loopback TCP 127.0.0.1:47219 |
| Haptics | `Haptics.android.kt` (VibrationEffect) | `Haptics.ios.kt` (UIImpactFeedbackGenerator) |
| `SessionHooks` | `ForegroundServiceHooks` -> `RewinderService` notification | todo: Live Activity (Widget Extension) |
| Permissions | `MainActivity.requestStart()` | `AVAudioApplication.requestRecordPermission` before `start()` |
| Back navigation | `OnBackPressedCallback` -> `AppNavigator.back()` | Swipe-back not needed (custom back buttons) |
| `SpeechEngine` / `TextEngine` | JNI (`whisper/`, `llama/`) | `SpeechBridgeImpl` / `TextBridgeImpl` (xcframeworks) |
| `ModelStorage` / `ModelDownloader` | `AndroidModelIo.kt` | `IosModelStorage` + `DownloadBridgeImpl` |

## Running

**Android**: open the repo root in Android Studio (latest stable), let Gradle sync,
run the `androidApp` configuration. Needs JDK 17+ (Android Studio's bundled JBR is fine),
plus **NDK (Side by side)** and **CMake** from SDK Manager > SDK Tools. The first sync
downloads whisper.cpp and llama.cpp sources from GitHub and compiles them (several minutes).

**iOS** (Mac with Xcode 15 or newer):
1. Open `iosApp/iosApp.xcodeproj`. Xcode resolves the `NativeEngines` package and downloads
   the whisper/llama frameworks (first time only, about 80 MB).
2. Set your team: `TEAM_ID=` in `iosApp/Configuration/Config.xcconfig`, or Signing & Capabilities.
3. Pick your iPhone (or "My Mac (Designed for iPad)" on Apple Silicon) and Run. The build
   phase runs Gradle to compile the shared Kotlin framework; it uses Android Studio's JDK
   automatically if `JAVA_HOME` is not set.
4. Models: tap Download in the app, or copy them in. iPhone: Finder > your iPhone > Files >
   Rewinder > create `models` and drop the 4 files. Simulator: `./scripts/ios-models.sh`.

iOS Simulator: the official llama.cpp download has no Simulator build. Run
`./scripts/ios-build-llama.sh` once (needs `brew install cmake`), then File > Packages >
Reset Package Caches. Real devices and "Designed for iPad" work without this.

## On-device AI (Android and iOS)

| Job | Model | File | Size |
| --- | --- | --- | --- |
| Speech to text | Whisper large-v3-turbo, q5_0 (whisper.cpp) | `ggml-large-v3-turbo-q5_0.bin` | 574 MB |
| Skip silence | Silero VAD v6.2.0 | `ggml-silero-v6.2.0.bin` | 0.9 MB |
| Summary, 4 GB+ RAM | Unsloth Qwen3.5 2B, Q4_0 | `Qwen3.5-2B-Q4_0.gguf` | 1.21 GB |
| Summary, under 4 GB | Unsloth Qwen3.5 0.8B, Q4_0 | `Qwen3.5-0.8B-Q4_0.gguf` | 507 MB |

- Downloaded once from Hugging Face into no-backup app storage (`HuggingFaceModelManager`),
  resumable. This is the only network use; audio and text never leave the phone.
- For development, `scripts/models.sh` downloads the files once into `models/` on your
  computer (git-ignored) and copies them into the same app folder with `adb` + `run-as`
  (debug builds), so reinstalling the app never re-downloads 1.8 GB. Models are kept out of
  the APK on purpose: a 1.8 GB APK would be re-sent on every Run and exceeds Play's limits.
- Whisper runs with language `tl` plus a Taglish initial prompt. On auto-detect Whisper
  tends to translate Tagalog into English; forcing `tl` keeps Tagalog, and English words
  inside Taglish still come out in English. Pure-English rooms may get some Tagalog
  spellings; a language setting is the next step.
- Models load per rewind and are freed right after, one at a time, so peak memory is
  about one model (roughly 1 to 1.4 GB), which is what makes 4 GB phones workable.
- Q4_0 is used because llama.cpp repacks it for ARM dot-product instructions, the fastest
  4-bit path on phone CPUs. Qwen3.5 runs in non-thinking mode (`QwenChatFormat`).
- Native builds target `arm64-v8a` with `armv8.2-a+dotprod` (Cortex-A55/A75 and newer).
  Add `x86_64` to `abiFilters` in `whisper/`, `llama/` and `androidApp/` for Intel emulators.
- Each engine is its own `.so` with its own private ggml (`--exclude-libs,ALL`), so the two
  copies never clash.

Expect a rewind of 3 to 5 minutes of speech to take roughly 30 seconds to a few minutes on a
mid-range phone (Whisper turbo is the slow part). Next speed step: transcribe in the
background every ~30 s while listening, keeping a rolling transcript instead of rolling audio.

Accuracy upgrade path for Filipino: BuzzASR/filipino (Whisper large-v3 fine-tune, MIT,
reported 9.68% WER on FLEURS) can be converted to ggml with whisper.cpp's
`convert-h5-to-ggml.py` and quantized, but at large-v3 size it is slower and heavier than turbo.

### iOS

Same models, same prompt, parser, filter and download logic (all in `commonMain`). The engines
are the official prebuilt `llama.xcframework` (b11521) and `whisper.xcframework` (v1.9.2, the
newest whisper.cpp release with Apple builds), via `iosApp/NativeEngines/Package.swift`. On a
real iPhone both run on the GPU (Metal). Models live in `Documents/models`, excluded from
iCloud backup.

Summary model choices on iOS: Apple Foundation (Apple Intelligence on-device model, iOS 26+,
via `AppleModelBridgeImpl.swift`), Qwen3.5 2B, Qwen3.5 0.8B. Qwen is the default on both platforms (a saved Apple choice is migrated to Qwen once; Apple stays selectable). Apple's model has a 4096 token window and is strongest in English; if it refuses or fails
(for example on Tagalog), an installed Qwen model summarizes instead. Android offers the two
Qwen sizes. The choice is saved (SharedPreferences / NSUserDefaults). Recap language (auto, fil, en) is a setting; Filipino adds a prompt rule and routes to Qwen even if Apple is selected.

Look: the RW-5 cassette-corder in a cobalt finish (colors sampled from a blue EPOK player), drawn
entirely in Compose so it is identical on both platforms. `ui/DeckMaterials.kt` holds the
materials (cobalt shell with brushed lines and a top-left bloom, one cached grain tile drawn over
every screen, recesses, bezels, silver plates, J-card paper, screws, jewel lamps, chrome knobs,
the physical press); `ui/DeckParts.kt` holds the modules (dot-matrix LCD, tape bay, VU meter,
lamp panel, grille, piano keys, key rail). Modules are drawn in the design canvas's units and
scaled to the screen width. `ui/TapesScreen.kt` is the full-screen tape shelf (it replaced the
drawer); `Screen.Tapes` is a normal back-stack entry. Fonts (OFL, licenses in
`composeResources/files/licenses`) are Compose resources in `shared/src/commonMain/composeResources/font`:
Archivo Black, IBM Plex Mono (500, 600), Doto (static black instance) and Caveat (static 600).
`Accessibility.android.kt` / `Accessibility.ios.kt` read Reduce Motion and contrast settings.
Status bar icons are light on both platforms (cobalt shell): `SystemBarStyle.dark` on Android,
`UIStatusBarStyleLightContent` with view-controller-based appearance off on iOS.

Performance: only the reels, the needle, the sheen and blinking lamps move, each as a
graphicsLayer transform or a draw-phase read in its own layer, so nothing recomposes while they
move; static art is cached per size with drawWithCache; the LCD recomposes once a second. Quick
taps use an Animatable press that always bottoms out before springing back.

Still todo on iOS: Live Activity for the lock screen (needs a Widget Extension target).

## Known gaps

- Recents are stored on the device (`StoredRecentsRepository`, key `tapes_v1`, max 500).
- Phone and VoIP call audio cannot be captured on either OS.
- The Android lock screen surface is a notification, not a custom widget; a live
  waveform there needs custom RemoteViews or Android 16 Live Updates.
- Since Android 14 users can swipe the ongoing notification away; recording continues.
