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
├── androidApp/                     Activity, foreground service, model downloads, AI wiring
├── whisper/                        Android: whisper.cpp v1.9.5 via CMake + JNI (speech to text)
├── llama/                          Android: llama.cpp b11521 via CMake + JNI (summaries)
└── iosApp/                         Xcode project (SwiftUI shell around Compose)
```

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
| `AudioSource` | `AndroidAudioSource` (AudioRecord, VOICE_RECOGNITION) | AVAudioEngine input tap, convert to 16 kHz mono Int16 |
| `SessionHooks` | `ForegroundServiceHooks` -> `RewinderService` notification | Swift class implementing `SessionHooks`, starts/ends a Live Activity |
| Permissions | `MainActivity.requestStart()` | `AVAudioApplication.requestRecordPermission` before `start()` |
| Back navigation | `OnBackPressedCallback` -> `AppNavigator.back()` | Swipe-back not needed (custom back buttons) |
| `Transcriber` / `Summarizer` | `WhisperTranscriber` / `QwenSummarizer` | placeholders |
| `ModelProvider` | `HuggingFaceModelManager` | none yet |

## Running

**Android**: open the repo root in Android Studio (latest stable), let Gradle sync,
run the `androidApp` configuration. Needs JDK 17+ (Android Studio's bundled JBR is fine),
plus **NDK (Side by side)** and **CMake** from SDK Manager > SDK Tools. The first sync
downloads whisper.cpp and llama.cpp sources from GitHub and compiles them (several minutes).

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

## On-device AI (Android, done)

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

### iOS later

Both engines ship Apple builds (llama.cpp and whisper.cpp XCFrameworks). Implement the same
`Transcriber`, `Summarizer` and `ModelProvider` in Swift (or Kotlin/Native cinterop) and pass
them into `RewinderGraph`; the prompt, parser and cleaner in `shared` are reused as is.

## Known gaps

- Recents are in memory and vanish when the app process ends (spec open question 4).
- The Android lock screen surface is a notification, not a custom widget; a live
  waveform there needs custom RemoteViews or Android 16 Live Updates.
- Since Android 14 users can swipe the ongoing notification away; recording continues.
