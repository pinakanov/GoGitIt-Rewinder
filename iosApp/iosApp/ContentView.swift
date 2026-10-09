import UIKit
import SwiftUI
import Shared

/// The Swift halves of the platform bridges. Everything else (UI, session logic,
/// models, prompts) lives in the shared Kotlin module.
enum NativeBridges {
    static let mic = MicBridgeImpl()
    static let speech = SpeechBridgeImpl()
    static let text = TextBridgeImpl()
    static let downloader = DownloadBridgeImpl()
}

/// Hosts the shared Compose UI (shared/src/iosMain/.../MainViewController.kt).
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(
            mic: NativeBridges.mic,
            speech: NativeBridges.speech,
            text: NativeBridges.text,
            downloader: NativeBridges.downloader
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
            .preferredColorScheme(.dark)
    }
}
