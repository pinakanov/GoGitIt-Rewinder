# Rewinder

Privacy-first, air-gapped local AI that helps ADHD minds catch up on what they just missed.

Rewinder keeps a rolling 5 minute memory of what you hear, only while you switch it on.
Tap **Rewind** and it turns that window into a short, plain summary, entirely on your phone.
No audio, transcript or summary ever leaves the device.

## How it works

1. Tap **Start**. Rewinder captures from the active mic and holds only the last 5 minutes in memory.
2. Tap **Rewind** (available once 3 minutes are buffered). The buffer is transcribed by Whisper, filtered for sensitive details, then summarized by a local Qwen model.
3. The audio and transcript are wiped as soon as the summary is saved. Only a title, time range and summary are kept.

Principles: summary is the only output, opt-in and visible, rolling memory, paraphrase never quote, sensitive details filtered first.

## Tech stack

| Layer | Choice |
| --- | --- |
| App | Kotlin Multiplatform + Compose Multiplatform (shared logic and UI) |
| Speech to text | whisper.cpp, Whisper large-v3-turbo (q5_0) with Silero VAD, tuned for Filipino/Taglish |
| Summaries | llama.cpp with Unsloth Qwen3.5 2B or 0.8B (Q4_0), chosen by device RAM |
| Android | minSdk 26, arm64-v8a, foreground mic service, NDK + CMake + JNI |
| iOS | SwiftUI shell, AVAudioEngine, prebuilt whisper/llama xcframeworks |

## Repository layout

```
shared/       Kotlin Multiplatform code: UI, rewind logic, pipeline, model management
androidApp/   Android shell (Activity, foreground service, engines)
iosApp/       iOS shell (Xcode project, Swift bridges, NativeEngines package)
whisper/      Android whisper.cpp module (CMake + JNI)
llama/        Android llama.cpp module (CMake + JNI)
scripts/      Model download and iOS native build helpers
docs/         Product spec and architecture notes
```

Most changes belong in `shared/src/commonMain`: edit once and both apps change.

## Requirements

- JDK 17 or newer
- Android Studio with the Android SDK, **NDK** and **CMake** (SDK Manager)
- For iOS: macOS with Xcode and an Apple ID (free) or Apple Developer team (paid)

## Build for Android

Open the folder in Android Studio and run `androidApp`, or use Gradle:

```bash
# Debug APK (debug-signed, ready to sideload)
./gradlew :androidApp:assembleDebug
# -> androidApp/build/outputs/apk/debug/androidApp-debug.apk

# Release APK (unsigned until you add a signing config or sign it manually)
./gradlew :androidApp:assembleRelease
# -> androidApp/build/outputs/apk/release/androidApp-release-unsigned.apk
```

Sign a release APK manually:

```bash
keytool -genkey -v -keystore ~/rewinder.jks -alias rewinder -keyalg RSA -keysize 2048 -validity 10000
zipalign -v 4 androidApp-release-unsigned.apk aligned.apk
apksigner sign --ks ~/rewinder.jks --out Rewinder-release.apk aligned.apk
```

Install on a connected phone: `adb install -r Rewinder-debug.apk`

## Build for iOS

1. Set `TEAM_ID` in `iosApp/Configuration/Config.xcconfig`.
2. Open `iosApp/iosApp.xcodeproj` in Xcode and run on an iPhone.
3. Simulator only: run `./scripts/ios-build-llama.sh` once.

To produce an IPA, use **Product > Archive > Distribute App** in Xcode, or:

```bash
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Release -destination 'generic/platform=iOS' \
  -archivePath build/Rewinder.xcarchive archive

xcodebuild -exportArchive -archivePath build/Rewinder.xcarchive \
  -exportPath dist -exportOptionsPlist ExportOptions.plist
```

Use `development` or `ad-hoc` as the export method for device installs, or `app-store-connect` for TestFlight.

## On-device models

Everything runs offline once the models are installed.

- **Android, fastest:** with the app installed and the phone connected over USB, run `./scripts/models.sh` from the repo root. It downloads the models once into `models/` (git-ignored) and copies them to the phone. Options: `2b`, `0.8b`, or `download` to only fetch.
- **iOS:** run `./scripts/ios-models.sh`, or drop the files in via Finder (iPhone > Files > Rewinder > `models`).
- **Either platform:** tap **Download** inside the app.

Keep model URLs in `scripts/models.sh` in sync with `shared/.../models/ModelCatalog.kt`.

## Docs

- [Product spec](docs/SPEC.md)
- [Architecture and iOS porting notes](docs/ARCHITECTURE.md)

## Status

Android is working end to end. iOS bridges are in progress (lock screen Live Activity is still to do).
