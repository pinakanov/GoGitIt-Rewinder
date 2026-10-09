#!/usr/bin/env bash
# Copies the models from ./models into Rewinder on the booted iOS Simulator.
# (Download them first with ./scripts/models.sh download.)
#
# On a real iPhone, use Finder instead: connect the iPhone, open it in Finder > Files >
# Rewinder, and drag the four files into a folder named "models".
set -euo pipefail
cd "$(dirname "$0")/.."

BUNDLE_ID="com.gogitit.rewinder"
DATA="$(xcrun simctl get_app_container booted "$BUNDLE_ID" data 2>/dev/null)" || {
  echo "Rewinder is not installed on a booted Simulator. Run it from Xcode once, then retry." >&2
  exit 1
}
DEST="$DATA/Documents/models"
mkdir -p "$DEST"
for f in ggml-large-v3-turbo-q5_0.bin ggml-silero-v6.2.0.bin Qwen3.5-2B-Q4_0.gguf Qwen3.5-0.8B-Q4_0.gguf; do
  if [[ -f "models/$f" ]]; then
    echo "→ $f"
    cp "models/$f" "$DEST/"
  fi
done
echo "Done. Rewinder notices the models within a few seconds."
