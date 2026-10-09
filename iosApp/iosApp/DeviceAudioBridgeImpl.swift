import Foundation
import Network
import ReplayKit
import UIKit
import Shared

/// Phone audio for the shared Kotlin IosDeviceAudioSource ("Microphone + phone audio" in Settings).
///
/// iOS only lets an app hear other apps' sound through a Screen Broadcast. The "Rewinder Audio"
/// broadcast extension (iosApp/RewinderBroadcast) takes the app-audio track, converts it to
/// 16 kHz mono Int16 and streams it here over a socket bound to 127.0.0.1 only. Nothing is
/// written to disk and no App Group is needed. Video frames are ignored by the extension.
///
/// Phone and FaceTime calls are not included: iOS keeps call audio out of broadcasts.
final class DeviceAudioBridgeImpl: NSObject, DeviceAudioBridge {
    /// Must match SampleHandler.swift.
    static let port: UInt16 = 47_219
    static let handshake = Data("RWND0001".utf8)

    private let queue = DispatchQueue(label: "com.gogitit.rewinder.device-audio")
    private var listener: NWListener?
    private var connection: NWConnection?
    private var sink: PcmSink?
    private var pending = Data()
    private var handshakeDone = false
    private var startedAt = Date.distantPast
    private var lastAudioAt = Date.distantPast
    private var running = false
    private var picker: RPSystemBroadcastPickerView?

    func start(sink: PcmSink) -> String? {
        stop()
        let parameters = NWParameters.tcp
        parameters.allowLocalEndpointReuse = true
        guard let port = NWEndpoint.Port(rawValue: Self.port) else { return "Phone audio could not start." }
        // Loopback only: nothing outside this phone can connect.
        parameters.requiredLocalEndpoint = .hostPort(host: .ipv4(.loopback), port: port)
        let listener: NWListener
        do {
            listener = try NWListener(using: parameters)
        } catch {
            return "Phone audio could not start: \(error.localizedDescription)"
        }
        queue.sync {
            self.sink = sink
            self.running = true
            self.startedAt = Date()
            self.lastAudioAt = .distantPast
        }
        listener.newConnectionHandler = { [weak self] connection in
            self?.accept(connection)
        }
        listener.start(queue: queue)
        self.listener = listener

        DispatchQueue.main.async { [weak self] in self?.showBroadcastPicker() }
        return nil
    }

    func stop() {
        listener?.cancel()
        listener = nil
        queue.sync {
            // Closing the socket tells the extension to end the broadcast.
            connection?.cancel()
            connection = nil
            sink = nil
            running = false
            pending.resetBytes(in: 0..<pending.count)
            pending.removeAll()
            handshakeDone = false
        }
        DispatchQueue.main.async { [weak self] in
            self?.picker?.removeFromSuperview()
            self?.picker = nil
        }
    }

    func label() -> String? {
        queue.sync {
            guard running else { return nil }
            if connection == nil { return "phone audio (waiting)" }
            return Date().timeIntervalSince(lastAudioAt) < 2 ? "phone audio" : "phone audio (quiet)"
        }
    }

    func warning() -> String? {
        queue.sync {
            guard running, connection == nil, Date().timeIntervalSince(startedAt) > 15 else { return nil }
            return "Phone audio is off. To include it, start the \"Rewinder Audio\" broadcast: " +
                "Control Center > hold Screen Recording > Rewinder Audio > Start Broadcast."
        }
    }

    // MARK: - Socket

    private func accept(_ newConnection: NWConnection) {
        // One broadcast at a time; a new one replaces the old.
        connection?.cancel()
        connection = newConnection
        handshakeDone = false
        pending.removeAll()
        newConnection.stateUpdateHandler = { [weak self, weak newConnection] state in
            guard let self = self, let conn = newConnection else { return }
            switch state {
            case .failed, .cancelled:
                if self.connection === conn { self.connection = nil }
            default:
                break
            }
        }
        newConnection.start(queue: queue)
        receive(on: newConnection)
    }

    private func receive(on conn: NWConnection) {
        conn.receive(minimumIncompleteLength: 1, maximumLength: 64 * 1024) { [weak self] data, _, isComplete, error in
            guard let self = self else { return }
            if let data = data, !data.isEmpty {
                self.consume(data, from: conn)
            }
            if isComplete || error != nil {
                conn.cancel()
                return
            }
            self.receive(on: conn)
        }
    }

    private func consume(_ data: Data, from conn: NWConnection) {
        pending.append(data)
        if !handshakeDone {
            guard pending.count >= Self.handshake.count else { return }
            guard pending.prefix(Self.handshake.count) == Self.handshake else {
                conn.cancel() // not our extension
                return
            }
            pending.removeFirst(Self.handshake.count)
            handshakeDone = true
        }
        let usable = pending.count - pending.count % 2
        guard usable > 0, let sink = sink else { return }
        let chunk = pending.prefix(usable)
        pending.removeFirst(usable)
        lastAudioAt = Date()
        sink.onPcm(pcm16: Data(chunk))
    }

    // MARK: - System broadcast sheet

    /// Opens the system "Screen Broadcast" sheet with Rewinder Audio preselected. Apps cannot
    /// start a broadcast themselves; the user taps Start Broadcast.
    private func showBroadcastPicker() {
        let picker = RPSystemBroadcastPickerView(frame: CGRect(x: -100, y: -100, width: 44, height: 44))
        picker.preferredExtension = (Bundle.main.bundleIdentifier ?? "com.gogitit.rewinder") + ".broadcast"
        picker.showsMicrophoneButton = false
        picker.alpha = 0.01
        let window = UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.keyWindow }
            .first
        window?.addSubview(picker)
        self.picker = picker
        for case let button as UIButton in picker.subviews {
            button.sendActions(for: .touchUpInside)
        }
    }
}
