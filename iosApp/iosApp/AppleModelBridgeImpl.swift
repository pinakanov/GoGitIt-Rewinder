import Foundation
import Shared
#if canImport(FoundationModels)
import FoundationModels
#endif

/// Apple Foundation Models (the on-device Apple Intelligence model) for the shared Kotlin
/// summarizer. Needs iOS 26 on an Apple Intelligence device with it turned on.
/// Kotlin calls this from a background thread, so waiting on the async API here is fine.
final class AppleModelBridgeImpl: NSObject, AppleModelBridge {
    private final class Box: @unchecked Sendable {
        var text: String?
        var error: String?
    }

    private var lastErrorText: String?

    func unavailableReason() -> String? {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, *) {
            switch SystemLanguageModel.default.availability {
            case .available:
                return nil
            case .unavailable(let reason):
                let text = String(describing: reason)
                if text.contains("deviceNotEligible") { return "This iPhone does not support Apple Intelligence" }
                if text.contains("appleIntelligenceNotEnabled") { return "Turn on Apple Intelligence in Settings" }
                if text.contains("modelNotReady") { return "Apple Intelligence is still downloading" }
                return "Apple Intelligence is unavailable"
            @unknown default:
                return "Apple Intelligence is unavailable"
            }
        }
        #endif
        return "Needs iOS 26 with Apple Intelligence"
    }

    func complete(instructions: String, prompt: String, temperature: Float) -> String? {
        #if canImport(FoundationModels)
        if #available(iOS 26.0, *) {
            let box = Box()
            let done = DispatchSemaphore(value: 0)
            Task.detached {
                do {
                    let session = LanguageModelSession(instructions: instructions)
                    let response = try await session.respond(
                        to: prompt,
                        options: GenerationOptions(temperature: Double(temperature))
                    )
                    box.text = response.content
                } catch {
                    box.error = String(describing: error)
                }
                done.signal()
            }
            done.wait()
            lastErrorText = box.error
            return box.text
        }
        #endif
        lastErrorText = "Apple Foundation Models need iOS 26"
        return nil
    }

    func lastError() -> String? { lastErrorText }
}
