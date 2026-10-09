import AudioToolbox
import CoreMedia
import Foundation
import Network
import ReplayKit

/// "Rewinder Audio" Screen Broadcast extension.
///
/// Takes only the app-audio track (sound playing on the phone), converts it to 16 kHz mono
/// Int16 and streams it to the Rewinder app over 127.0.0.1. Video frames and the mic track are
/// dropped on arrival. Nothing is written to disk. When Rewinder stops (or is not listening),
/// the broadcast ends itself.
final class SampleHandler: RPBroadcastSampleHandler {
    /// Must match DeviceAudioBridgeImpl.swift.
    private static let port: UInt16 = 47_219
    private static let handshake = Data("RWND0001".utf8)

    private let queue = DispatchQueue(label: "com.gogitit.rewinder.broadcast")
    private let lock = NSLock()
    private var connection: NWConnection?
    private var ready = false
    private var ended = false
    private var everConnected = false
    private var resampler = MonoResampler(targetRate: 16_000)

    override func broadcastStarted(withSetupInfo setupInfo: [String: NSObject]?) {
        guard let port = NWEndpoint.Port(rawValue: Self.port) else { return }
        let conn = NWConnection(host: .ipv4(.loopback), port: port, using: .tcp)
        conn.stateUpdateHandler = { [weak self] state in
            guard let self = self else { return }
            switch state {
            case .ready:
                self.lock.lock()
                self.ready = true
                self.everConnected = true
                self.lock.unlock()
                conn.send(content: Self.handshake, completion: .contentProcessed { _ in })
            case .waiting, .failed:
                // Connection refused: Rewinder is not listening for phone audio.
                self.end(self.everConnected
                    ? "Rewinder stopped recording."
                    : "Open Rewinder, choose Microphone + phone audio in Settings, then press REC.")
            case .cancelled:
                self.end("Rewinder stopped recording.")
            default:
                break
            }
        }
        lock.lock()
        connection = conn
        lock.unlock()
        conn.start(queue: queue)
    }

    override func processSampleBuffer(_ sampleBuffer: CMSampleBuffer, with sampleBufferType: RPSampleBufferType) {
        guard sampleBufferType == .audioApp else { return } // video and mic are ignored
        lock.lock()
        let conn = ready && !ended ? connection : nil
        lock.unlock()
        guard let conn = conn else { return }
        var samples = resampler.convert(sampleBuffer)
        guard !samples.isEmpty else { return }
        let data = samples.withUnsafeBufferPointer { Data(buffer: $0) }
        for i in samples.indices { samples[i] = 0 }
        conn.send(content: data, completion: .contentProcessed { [weak self] error in
            if error != nil { self?.end("Rewinder stopped recording.") }
        })
    }

    override func broadcastPaused() {}
    override func broadcastResumed() {}

    override func broadcastFinished() {
        lock.lock()
        ended = true
        let conn = connection
        connection = nil
        lock.unlock()
        conn?.cancel()
    }

    private func end(_ message: String) {
        lock.lock()
        if ended {
            lock.unlock()
            return
        }
        ended = true
        let conn = connection
        connection = nil
        lock.unlock()
        conn?.cancel()
        finishBroadcastWithError(NSError(domain: "Rewinder", code: 1, userInfo: [NSLocalizedDescriptionKey: message]))
    }
}

/// Converts ReplayKit app audio (any rate, Int16 or Float32, interleaved or not, either byte
/// order) to 16 kHz mono Int16, carrying the resampling phase across buffers.
struct MonoResampler {
    let targetRate: Double
    private var position: Double = 0
    private var last: Float = 0

    init(targetRate: Double) {
        self.targetRate = targetRate
    }

    mutating func convert(_ sampleBuffer: CMSampleBuffer) -> [Int16] {
        guard let format = CMSampleBufferGetFormatDescription(sampleBuffer),
              let asbdPointer = CMAudioFormatDescriptionGetStreamBasicDescription(format) else { return [] }
        let asbd = asbdPointer.pointee
        guard asbd.mFormatID == kAudioFormatLinearPCM, asbd.mSampleRate > 0 else { return [] }
        let channels = max(1, Int(asbd.mChannelsPerFrame))
        let frames = CMSampleBufferGetNumSamples(sampleBuffer)
        guard frames > 0 else { return [] }

        let isFloat = asbd.mFormatFlags & kAudioFormatFlagIsFloat != 0
        let isBigEndian = asbd.mFormatFlags & kAudioFormatFlagIsBigEndian != 0
        let nonInterleaved = asbd.mFormatFlags & kAudioFormatFlagIsNonInterleaved != 0
        let bytesPerSample = Int(asbd.mBitsPerChannel) / 8
        guard (isFloat && bytesPerSample == 4) || (!isFloat && bytesPerSample == 2) else { return [] }

        let bufferList = AudioBufferList.allocate(maximumBuffers: nonInterleaved ? channels : 1)
        defer { free(bufferList.unsafeMutablePointer) }
        var blockBuffer: CMBlockBuffer?
        let status = CMSampleBufferGetAudioBufferListWithRetainedBlockBuffer(
            sampleBuffer,
            bufferListSizeNeededOut: nil,
            bufferListOut: bufferList.unsafeMutablePointer,
            bufferListSize: AudioBufferList.sizeInBytes(maximumBuffers: nonInterleaved ? channels : 1),
            blockBufferAllocator: nil,
            blockBufferMemoryAllocator: nil,
            flags: kCMSampleBufferFlag_AudioBufferList_Assure16ByteAlignment,
            blockBufferOut: &blockBuffer
        )
        guard status == noErr else { return [] }

        func sample(_ base: UnsafeMutableRawPointer, _ index: Int) -> Float {
            if isFloat {
                var bits = base.load(fromByteOffset: index * 4, as: UInt32.self)
                if isBigEndian { bits = bits.byteSwapped }
                return Float(bitPattern: bits)
            } else {
                var bits = base.load(fromByteOffset: index * 2, as: UInt16.self)
                if isBigEndian { bits = bits.byteSwapped }
                return Float(Int16(bitPattern: bits)) / 32768
            }
        }

        // Mix down to mono, with the previous buffer's last sample in front for interpolation.
        var mono = [Float](repeating: 0, count: frames + 1)
        mono[0] = last
        let scale = 1 / Float(channels)
        if nonInterleaved {
            for (c, buffer) in bufferList.enumerated() where c < channels {
                guard let base = buffer.mData else { continue }
                for f in 0..<frames { mono[f + 1] += sample(base, f) * scale }
            }
        } else if let base = bufferList[0].mData {
            for f in 0..<frames {
                var sum: Float = 0
                for c in 0..<channels { sum += sample(base, f * channels + c) }
                mono[f + 1] = sum * scale
            }
        }

        let ratio = asbd.mSampleRate / targetRate
        // Light smoothing before dropping samples, so high frequencies do not alias into speech.
        if ratio >= 2 {
            var previous = mono[0]
            for i in 1..<(mono.count - 1) {
                let current = mono[i]
                mono[i] = 0.25 * previous + 0.5 * current + 0.25 * mono[i + 1]
                previous = current
            }
        }

        var out = [Int16]()
        out.reserveCapacity(Int(Double(frames) / ratio) + 2)
        let lastIndex = Double(mono.count - 1)
        while position < lastIndex {
            let i = Int(position)
            let t = Float(position - Double(i))
            let v = mono[i] * (1 - t) + mono[i + 1] * t
            out.append(Int16(max(-1, min(1, v)) * 32767))
            position += ratio
        }
        position -= lastIndex
        last = mono[mono.count - 1]
        for i in mono.indices { mono[i] = 0 }
        return out
    }
}
