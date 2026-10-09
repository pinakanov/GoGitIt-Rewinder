#!/usr/bin/env bash
# Builds llama.xcframework WITH an iOS Simulator slice (the official download only has
# iPhone + Mac). Run once on your Mac if you want to use the iOS Simulator.
# Needs Xcode and CMake (brew install cmake). Takes several minutes.
#
#   ./scripts/ios-build-llama.sh
#
# Then in Xcode: File > Packages > Reset Package Caches, and build again.
set -euo pipefail
cd "$(dirname "$0")/.."

TAG="b11521" # keep in sync with llama/src/main/cpp/CMakeLists.txt and NativeEngines/Package.swift
OUT="iosApp/NativeEngines/Frameworks"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

command -v cmake >/dev/null || { echo "CMake is required: brew install cmake" >&2; exit 1; }

echo "Fetching llama.cpp $TAG..."
git clone --depth 1 --branch "$TAG" https://github.com/ggml-org/llama.cpp.git "$WORK/llama.cpp"

echo "Building llama.xcframework (iPhone + Simulator + Mac)..."
(cd "$WORK/llama.cpp" && ./build-xcframework.sh ios-sim ios-device macos)

mkdir -p "$OUT"
rm -rf "$OUT/llama.xcframework"
cp -R "$WORK/llama.cpp/build-apple/llama.xcframework" "$OUT/"
echo "Done: $OUT/llama.xcframework"
echo "In Xcode: File > Packages > Reset Package Caches, then build."
