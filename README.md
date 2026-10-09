# GoGitIt-Rewinder
Privacy-first, air-gapped local AI turning rambling voice notes into actionable task for ADHD minds.

Rewinder keeps a rolling 5 minute memory of what you hear, only while you switch it on,
and turns it into a short on-device summary when you tap **Rewind**.

## Project

Kotlin Multiplatform + Compose Multiplatform: shared logic and UI, thin Android and iOS layers.

- Android: open this folder in Android Studio and run `androidApp` (needs NDK + CMake from SDK Manager).
- Install the on-device models (Whisper large-v3-turbo for Filipino/Taglish speech, Unsloth
  Qwen3.5 2B or 0.8B for summaries). Fastest: with the app installed and the phone connected,
  run `./scripts/models.sh` from the repo root. It downloads them once into `models/`
  (git-ignored) and copies them onto the phone over USB. Or tap **Download** in the app.
  After that everything runs offline.
- iOS: open `iosApp/iosApp.xcodeproj` in Xcode.

## Docs

- [Product spec](docs/SPEC.md)
- [Architecture and iOS porting notes](docs/ARCHITECTURE.md)
