import Foundation
import Shared
import whisper

/// whisper.cpp (whisper.xcframework) for the shared Kotlin OnDeviceTranscriber.
/// Loads the model, transcribes one rewind, frees everything. Uses Metal on a real device.
final class SpeechBridgeImpl: NSObject, SpeechBridge {
    func transcribe(modelPath: String, pcm16: Data, language: String, prompt: String,
                    vadModelPath: String?, threads: Int32) -> String? {
        var cparams = whisper_context_default_params()
        cparams.flash_attn = true // faster encoder on Metal and CPU, less memory; same transcript
        #if targetEnvironment(simulator)
        cparams.use_gpu = false
        #endif
        guard let ctx = whisper_init_from_file_with_params(modelPath, cparams) else { return nil }
        defer { whisper_free(ctx) }

        let count = pcm16.count / MemoryLayout<Int16>.size
        var samples = [Float](repeating: 0, count: count)
        pcm16.withUnsafeBytes { raw in
            for i in 0..<count {
                samples[i] = Float(raw.loadUnaligned(fromByteOffset: i * 2, as: Int16.self)) / 32768
            }
        }
        defer { for i in samples.indices { samples[i] = 0 } }

        var params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY)
        params.n_threads = threads
        params.translate = false          // transcribe, never translate
        params.detect_language = false
        params.no_context = true          // fewer repetition loops on long audio
        params.no_timestamps = true
        params.single_segment = false
        params.print_special = false
        params.print_progress = false
        params.print_realtime = false
        params.print_timestamps = false
        params.suppress_blank = true
        params.suppress_nst = true

        let vadPath = vadModelPath ?? ""
        return language.withCString { lang -> String? in
            prompt.withCString { initialPrompt -> String? in
                vadPath.withCString { vad -> String? in
                    params.language = lang
                    params.initial_prompt = prompt.isEmpty ? nil : initialPrompt
                    if !vadPath.isEmpty {
                        params.vad = true
                        params.vad_model_path = vad
                        params.vad_params.threshold = 0.35
                    }
                    let rc = samples.withUnsafeBufferPointer { buf in
                        whisper_full(ctx, params, buf.baseAddress, Int32(buf.count))
                    }
                    guard rc == 0 else { return nil }
                    var text = ""
                    let segments = whisper_full_n_segments(ctx)
                    for i in 0..<segments {
                        if let segment = whisper_full_get_segment_text(ctx, i) {
                            text += String(cString: segment)
                        }
                    }
                    return text
                }
            }
        }
    }
}
