// swift-tools-version:5.9
//
// Prebuilt whisper.cpp and llama.cpp for iOS, the same engine versions the Android build
// compiles from source (whisper.cpp 1.9.x, llama.cpp b11521).
//
// The official llama.cpp download has no iOS Simulator slice. To run in the Simulator,
// run scripts/ios-build-llama.sh once; it builds Frameworks/llama.xcframework with a
// Simulator slice, and this manifest uses it automatically (File > Packages > Reset
// Package Caches in Xcode after building it).
import PackageDescription
import Foundation

let packageDir = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
let localLlama = packageDir.appendingPathComponent("Frameworks/llama.xcframework")

let llama: Target = FileManager.default.fileExists(atPath: localLlama.path)
    ? .binaryTarget(name: "llama", path: "Frameworks/llama.xcframework")
    : .binaryTarget(
        name: "llama",
        url: "https://github.com/ggml-org/llama.cpp/releases/download/b11521/llama-b11521-xcframework.zip",
        checksum: "65f063211b268cd3847a54f09f7ba1efe4072bff6e194e55e3e708fc1bdca2e5"
    )

let package = Package(
    name: "NativeEngines",
    platforms: [.iOS("16.4")],
    products: [
        .library(name: "NativeEngines", targets: ["llama", "whisper"]),
    ],
    targets: [
        llama,
        .binaryTarget(
            name: "whisper",
            url: "https://github.com/ggml-org/whisper.cpp/releases/download/v1.9.2/whisper-v1.9.2-xcframework.zip",
            checksum: "af74fed13ea7f2d5ca2a39d9f58ec177713fafd7cab63aef4e27b79f3ceca80b"
        ),
    ]
)
