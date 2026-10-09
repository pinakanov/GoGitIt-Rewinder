import AVFoundation
import Shared

/// AVAudioEngine microphone for the shared Kotlin IosAudioSource.
/// Converts whatever the hardware delivers (usually 48 kHz float) into 16 kHz mono Int16,
/// the format the ring buffer and Whisper use. Audio only lives in memory.
final class MicBridgeImpl: NSObject, MicBridge {
    private let engine = AVAudioEngine()
    private let outputFormat = AVAudioFormat(commonFormat: .pcmFormatInt16, sampleRate: 16_000, channels: 1, interleaved: true)!
    private var converter: AVAudioConverter?
    private var inputRate: Double = 0
    private var running = false
    private var interrupted = false
    private var sink: PcmSink?
    private var observing = false

    func requestPermission(callback: PermissionCallback) {
        if #available(iOS 17.0, *) {
            AVAudioApplication.requestRecordPermission { granted in callback.onResult(granted: granted) }
        } else {
            AVAudioSession.sharedInstance().requestRecordPermission { granted in callback.onResult(granted: granted) }
        }
    }

    func start(sink: PcmSink) -> String? {
        self.sink = sink
        observeSessionEvents()
        let error = startEngine(sink: sink)
        if error == nil {
            running = true
            interrupted = false
        }
        return error
    }

    /// Calls and other apps can take the audio session. iOS then stops our engine; these
    /// notifications restart it as soon as the microphone is free again.
    private func observeSessionEvents() {
        guard !observing else { return }
        observing = true
        let center = NotificationCenter.default
        center.addObserver(self, selector: #selector(handleInterruption(_:)),
                           name: AVAudioSession.interruptionNotification, object: nil)
        center.addObserver(self, selector: #selector(handleMediaReset(_:)),
                           name: AVAudioSession.mediaServicesWereResetNotification, object: nil)
        center.addObserver(self, selector: #selector(handleConfigurationChange(_:)),
                           name: .AVAudioEngineConfigurationChange, object: engine)
    }

    @objc private func handleInterruption(_ note: Notification) {
        guard running,
              let raw = note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
              let type = AVAudioSession.InterruptionType(rawValue: raw) else { return }
        switch type {
        case .began:
            interrupted = true
        case .ended:
            restart(attempt: 0)
        @unknown default:
            break
        }
    }

    @objc private func handleMediaReset(_ note: Notification) {
        guard running else { return }
        restart(attempt: 0)
    }

    @objc private func handleConfigurationChange(_ note: Notification) {
        // Route or format changed (headphones, Bluetooth, a call ending). Rebuild the tap.
        guard running else { return }
        restart(attempt: 0)
    }

    private func restart(attempt: Int) {
        guard running, let sink = sink else { return }
        DispatchQueue.main.async { [weak self] in
            guard let self = self, self.running else { return }
            self.engine.inputNode.removeTap(onBus: 0)
            self.engine.stop()
            if self.startEngine(sink: sink) == nil {
                self.interrupted = false
            } else if attempt < 10 {
                // The call may still be holding the mic; try again shortly.
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { self.restart(attempt: attempt + 1) }
            }
        }
    }

    private func startEngine(sink: PcmSink) -> String? {
        let session = AVAudioSession.sharedInstance()
        do {
            // playAndRecord + mixWithOthers: music or podcasts can keep playing while Rewinder listens.
            try session.setCategory(.playAndRecord, mode: .default, options: [.mixWithOthers, .allowBluetooth, .defaultToSpeaker])
            try session.setActive(true)
        } catch {
            return "Could not start audio: \(error.localizedDescription)"
        }

        let input = engine.inputNode
        let inputFormat = input.outputFormat(forBus: 0)
        guard inputFormat.sampleRate > 0, inputFormat.channelCount > 0,
              let converter = AVAudioConverter(from: inputFormat, to: outputFormat) else {
            return "No microphone input is available."
        }
        self.converter = converter
        inputRate = inputFormat.sampleRate

        input.removeTap(onBus: 0)
        input.installTap(onBus: 0, bufferSize: 4800, format: inputFormat) { [weak self] buffer, _ in
            self?.convertAndSend(buffer, sink: sink)
        }
        engine.prepare()
        do {
            try engine.start()
        } catch {
            input.removeTap(onBus: 0)
            return "Could not start the microphone: \(error.localizedDescription)"
        }
        return nil
    }

    private func convertAndSend(_ buffer: AVAudioPCMBuffer, sink: PcmSink) {
        guard let converter = converter else { return }
        let ratio = outputFormat.sampleRate / buffer.format.sampleRate
        let capacity = AVAudioFrameCount(Double(buffer.frameLength) * ratio) + 64
        guard let out = AVAudioPCMBuffer(pcmFormat: outputFormat, frameCapacity: capacity) else { return }

        var fed = false
        var error: NSError?
        let status = converter.convert(to: out, error: &error) { _, inputStatus in
            if fed {
                inputStatus.pointee = .noDataNow
                return nil
            }
            fed = true
            inputStatus.pointee = .haveData
            return buffer
        }
        guard status != .error, error == nil, out.frameLength > 0, let samples = out.int16ChannelData else { return }
        let data = Data(bytes: samples[0], count: Int(out.frameLength) * MemoryLayout<Int16>.size)
        sink.onPcm(pcm16: data)
    }

    func stop() {
        guard running else { return }
        running = false
        interrupted = false
        sink = nil
        engine.inputNode.removeTap(onBus: 0)
        engine.stop()
        converter = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    func inputLabel() -> String? {
        guard running else { return nil }
        let name = AVAudioSession.sharedInstance().currentRoute.inputs.first?.portName ?? "Microphone"
        return "\(name) · \(Int(inputRate / 1000)) kHz in"
    }

    func inputWarning() -> String? {
        let session = AVAudioSession.sharedInstance()
        let denied: Bool
        if #available(iOS 17.0, *) {
            denied = AVAudioApplication.shared.recordPermission == .denied
        } else {
            denied = session.recordPermission == .denied
        }
        if denied { return "Microphone access is off for Rewinder in Settings." }
        if interrupted {
            return "A call or another app has the microphone. iOS gives it back when it ends; " +
                "phone audio, if on, keeps recording."
        }
        if !session.isInputAvailable { return "No microphone input is available." }
        return nil
    }
}
