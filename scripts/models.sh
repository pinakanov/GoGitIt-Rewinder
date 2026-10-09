#!/usr/bin/env bash
# Puts Rewinder's on-device AI models on your phone without the in-app download.
#
#   1. Downloads the models once into ./models (resumable, skipped if already there).
#   2. Copies them over USB into the app's private storage on the connected phone/emulator.
#
# Usage (from the repo root, with the debug app installed and a device connected):
#   ./scripts/models.sh            # picks Qwen3.5 2B or 0.8B from the device's RAM
#   ./scripts/models.sh 2b         # force Qwen3.5 2B
#   ./scripts/models.sh 0.8b       # force Qwen3.5 0.8B
#   ./scripts/models.sh download   # only download into ./models, no phone needed
#
# Keep these URLs and file names in sync with shared/src/commonMain/.../models/ModelCatalog.kt.
set -euo pipefail
cd "$(dirname "$0")/.."

PKG="com.gogitit.rewinder"
DIR="models"
HF="https://huggingface.co"

WHISPER_FILE="ggml-large-v3-turbo-q5_0.bin"
WHISPER_URL="$HF/ggerganov/whisper.cpp/resolve/main/$WHISPER_FILE"
VAD_FILE="ggml-silero-v6.2.0.bin"
VAD_URL="$HF/ggml-org/whisper-vad/resolve/main/$VAD_FILE"
QWEN2B_FILE="Qwen3.5-2B-Q4_0.gguf"
QWEN2B_URL="$HF/unsloth/Qwen3.5-2B-GGUF/resolve/main/$QWEN2B_FILE"
QWEN08B_FILE="Qwen3.5-0.8B-Q4_0.gguf"
QWEN08B_URL="$HF/unsloth/Qwen3.5-0.8B-GGUF/resolve/main/$QWEN08B_FILE"

MODE="${1:-auto}"

ADB="${ADB:-}"
if [[ -z "$ADB" ]]; then
  if command -v adb >/dev/null 2>&1; then
    ADB="adb"
  else
    ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
  fi
fi

download() { # name url
  local name="$1" url="$2"
  if [[ -f "$DIR/$name" ]]; then
    echo "✓ $name already downloaded"
    return
  fi
  echo "↓ $name"
  # -C - resumes a partial .part file; --retry survives flaky Wi-Fi.
  curl -L --fail --retry 5 --retry-delay 3 -C - -o "$DIR/$name.part" "$url"
  mv "$DIR/$name.part" "$DIR/$name"
}

pick_qwen() {
  case "$MODE" in
    2b) echo "2b" ;;
    0.8b|08b) echo "0.8b" ;;
    *)
      # Same rule as ModelCatalog.summarizerFor(): about 4 GB of RAM or more gets 2B.
      local kb
      kb=$("$ADB" shell cat /proc/meminfo | awk '/MemTotal/ {print $2}' | tr -d '\r')
      if (( kb * 1024 >= 3400000000 )); then echo "2b"; else echo "0.8b"; fi
      ;;
  esac
}

mkdir -p "$DIR"

if [[ "$MODE" == "download" ]]; then
  download "$WHISPER_FILE" "$WHISPER_URL"
  download "$VAD_FILE" "$VAD_URL"
  download "$QWEN2B_FILE" "$QWEN2B_URL"
  download "$QWEN08B_FILE" "$QWEN08B_URL"
  echo "Done. Models are in ./$DIR"
  exit 0
fi

if [[ ! -x "$ADB" ]] && ! command -v "$ADB" >/dev/null 2>&1; then
  echo "adb not found. Install Android SDK Platform-Tools or set ADB=/path/to/adb." >&2
  exit 1
fi
if [[ "$("$ADB" get-state 2>/dev/null || true)" != "device" ]]; then
  echo "No device connected. Start the emulator or plug in your phone (USB debugging on)." >&2
  echo "If several are connected, pick one with: ANDROID_SERIAL=<id> $0" >&2
  exit 1
fi
if ! "$ADB" shell pm path "$PKG" >/dev/null 2>&1; then
  echo "Rewinder is not installed on the device. Run the app from Android Studio once, then retry." >&2
  exit 1
fi

QWEN="$(pick_qwen)"
if [[ "$QWEN" == "2b" ]]; then QWEN_FILE="$QWEN2B_FILE"; QWEN_URL="$QWEN2B_URL"; else QWEN_FILE="$QWEN08B_FILE"; QWEN_URL="$QWEN08B_URL"; fi
echo "Summary model for this device: $QWEN_FILE"

download "$WHISPER_FILE" "$WHISPER_URL"
download "$VAD_FILE" "$VAD_URL"
download "$QWEN_FILE" "$QWEN_URL"

TMP="/data/local/tmp/rewinder-models"
"$ADB" shell "rm -rf $TMP && mkdir -p $TMP"
for f in "$WHISPER_FILE" "$VAD_FILE" "$QWEN_FILE"; do
  echo "→ pushing $f"
  "$ADB" push "$DIR/$f" "$TMP/$f"
done
"$ADB" shell "chmod 755 $TMP && chmod 644 $TMP/*"

# run-as copies into the app's private no-backup folder (works on debug builds).
"$ADB" shell am force-stop "$PKG"
"$ADB" shell "run-as $PKG sh -c 'mkdir -p no_backup/models && cp $TMP/* no_backup/models/ && ls -l no_backup/models'"
"$ADB" shell "rm -rf $TMP"

"$ADB" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
echo "Done. Rewinder restarted with its models installed."
