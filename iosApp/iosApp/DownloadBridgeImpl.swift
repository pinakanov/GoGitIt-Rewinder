import Foundation
import Shared

/// Streams a model file from Hugging Face into a .part file for the shared Kotlin ModelStore,
/// resuming with an HTTP Range request. All work happens on one serial queue.
final class DownloadBridgeImpl: NSObject, DownloadBridge, URLSessionDataDelegate {
    private let queue: OperationQueue = {
        let q = OperationQueue()
        q.maxConcurrentOperationCount = 1
        return q
    }()
    private lazy var session = URLSession(configuration: .default, delegate: self, delegateQueue: queue)
    private var task: URLSessionDataTask?
    private var file: FileHandle?
    private var listener: DownloadListener?
    private var partPath = ""
    private var written: Int64 = 0
    private var lastReport: Int64 = 0
    private var alreadyComplete = false
    private var failure: String?

    func start(url: String, partPath: String, existingBytes: Int64, listener: DownloadListener) {
        queue.addOperation { [self] in
            self.listener = listener
            self.partPath = partPath
            written = existingBytes
            lastReport = existingBytes
            alreadyComplete = false
            failure = nil
            guard let target = URL(string: url) else {
                listener.onFinished(error: "Bad download URL")
                return
            }
            var request = URLRequest(url: target)
            request.setValue("Rewinder-iOS/0.2", forHTTPHeaderField: "User-Agent")
            if existingBytes > 0 {
                request.setValue("bytes=\(existingBytes)-", forHTTPHeaderField: "Range")
            }
            task = session.dataTask(with: request)
            task?.resume()
        }
    }

    func cancel() {
        queue.addOperation { [self] in task?.cancel() }
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive response: URLResponse,
                    completionHandler: @escaping (URLSession.ResponseDisposition) -> Void) {
        let code = (response as? HTTPURLResponse)?.statusCode ?? 0
        if code == 416 && written > 0 { // the partial file is already complete
            alreadyComplete = true
            completionHandler(.cancel)
            return
        }
        guard code == 200 || code == 206 else {
            failure = "HTTP \(code)"
            completionHandler(.cancel)
            return
        }
        let fm = FileManager.default
        if code == 200 {
            written = 0 // server ignored Range: start over
            fm.createFile(atPath: partPath, contents: nil)
        } else if !fm.fileExists(atPath: partPath) {
            fm.createFile(atPath: partPath, contents: nil)
        }
        file = FileHandle(forWritingAtPath: partPath)
        guard let file = file else {
            failure = "Cannot write the model file"
            completionHandler(.cancel)
            return
        }
        _ = file.seekToEndOfFile()
        completionHandler(.allow)
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        do {
            try file?.write(contentsOf: data)
        } catch {
            failure = "Storage error: \(error.localizedDescription)"
            dataTask.cancel()
            return
        }
        written += Int64(data.count)
        if written - lastReport >= 2_000_000 {
            lastReport = written
            listener?.onProgress(writtenBytes: written)
        }
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        try? file?.close()
        file = nil
        let finished = listener
        listener = nil
        if alreadyComplete {
            finished?.onFinished(error: nil)
        } else if let failure = failure {
            finished?.onFinished(error: failure)
        } else if let error = error {
            finished?.onFinished(error: error.localizedDescription)
        } else {
            finished?.onFinished(error: nil)
        }
    }
}
