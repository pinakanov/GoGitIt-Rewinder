# Rewinder architecture

Kotlin Multiplatform + Compose Multiplatform. One codebase for logic **and** UI;
Android and iOS each add only a thin platform layer.

```
Rewinder/
├── shared/                         Everything that is the same on both platforms
│   └── src/
│       ├── commonMain/kotlin/com/gogitit/rewinder/
│       │   ├── App.kt              Root composable (RewinderApp)
│       │   ├── RewinderController  Session state machine (Start, Stop, Rewind)
│       │   ├── RewinderGraph       Manual DI: each platform builds one
│       │   ├── AppNavigator        Home / Summary / Privacy + drawer state
│       │   ├── core/               Rolling 5 min ring buffer, product rules
│       │   ├── pipeline/           Transcriber -> SensitiveFilter -> Summarizer
│       │   ├── audio/              AudioSource + SessionHooks interfaces
│       │   ├── data/               Recents (in memory for now)
│       │   └── ui/                 Locally-style screens and components
│       ├── commonTest/             Ring buffer + sensitive filter tests
│       ├── androidMain/            AndroidAudioSource (AudioRecord)
│       └── iosMain/                MainViewController (Compose entry for Swift)
├── androidApp/                     Activity, foreground service, manifest, icons
└── iosApp/                         Xcode project (SwiftUI shell around Compose)
```

## How a rewind flows

1. **Start**: `RewinderController.start()` calls `SessionHooks.onSessionStarted()`
   (Android: microphone foreground service + ongoing notification) and collects
   `AudioSource.frames()` into `AudioRingBuffer` (16 kHz mono PCM16, 5 min, memory only).
2. While running, `RewinderState.bufferedMillis` counts audio since the last cut.
   **Rewind** is disabled until it reaches 3:00 (`RewindPolicy`).
3. **Rewind**: snapshot the buffer since the cut, advance the cut, then
   `RewindPipeline`: transcribe, zero-fill PCM, filter sensitive details, summarize.
   The result is saved as a `Recent` and the UI opens it.
4. **Stop**: capture is cancelled, the buffer is zero-filled, the service stops.

Every buffer access runs on one single-threaded dispatcher, so the ring buffer has no locks.

## Platform seams (the only things to write per platform)

| Seam | Android (done) | iOS (todo) |
| --- | --- | --- |
| `AudioSource` | `AndroidAudioSource` (AudioRecord, VOICE_RECOGNITION) | AVAudioEngine input tap, convert to 16 kHz mono Int16 |
| `SessionHooks` | `ForegroundServiceHooks` -> `RewinderService` notification | Swift class implementing `SessionHooks`, starts/ends a Live Activity |
| Permissions | `MainActivity.requestStart()` | `AVAudioApplication.requestRecordPermission` before `start()` |
| Back navigation | `OnBackPressedCallback` -> `AppNavigator.back()` | Swipe-back not needed (custom back buttons) |
| `Transcriber` / `Summarizer` | placeholders | placeholders |

## Running

**Android**: open the repo root in Android Studio (latest stable), let Gradle sync,
run the `androidApp` configuration. Needs JDK 17+ (Android Studio's bundled JBR is fine).

**iOS** (on a Mac with Xcode): open `iosApp/iosApp.xcodeproj`, set your team in
`iosApp/Configuration/Config.xcconfig` (`TEAM_ID=`), pick a simulator, Run.
The Xcode build phase calls `./gradlew :shared:embedAndSignAppleFrameworkForXcode`,
so no extra setup is needed. Until the AVAudioEngine source exists, iOS uses
`SimulatedAudioSource` so the full flow (3 min unlock, Rewind, Recents) works.

## Porting checklist for iOS

1. `IosAudioSource` in `shared/src/iosMain` (Kotlin/Native can call AVFoundation directly),
   or write it in Swift and pass it in by implementing the `AudioSource` interface.
2. Swift `LiveActivitySessionHooks: SessionHooks` using ActivityKit, a Widget Extension
   target for the lock screen UI (clock, "Rewinder in progress", waveform, Stop/Rewind
   via App Intents).
3. Change `MainViewController()` to accept those two objects from Swift.
4. Replace the template app icon in `Assets.xcassets`.

## Wiring real models (both platforms)

- `Transcriber`: whisper.cpp (tiny/base, quantized) via JNI on Android and cinterop on
  iOS, or the OS on-device recognizers where offline mode is guaranteed.
- `Summarizer`: a small quantized LLM (llama.cpp, MediaPipe LLM Inference, or Apple
  Foundation Models on supported iPhones). Use `SummarizerPrompt.SYSTEM`.
- Add implementations under `androidMain` / `iosMain` (or expect/actual) and pass them
  into `RewinderGraph`. Nothing else changes.

## Known gaps

- Recents are in memory and vanish when the app process ends (spec open question 4).
- The Android lock screen surface is a notification, not a custom widget; a live
  waveform there needs custom RemoteViews or Android 16 Live Updates.
- Since Android 14 users can swipe the ongoing notification away; recording continues.
