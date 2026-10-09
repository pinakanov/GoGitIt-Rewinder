package com.gogitit.rewinder.ios

import platform.Foundation.NSData

/*
 * The only things iOS implements in Swift (the BridgeImpl Swift files in iosApp/iosApp). Everything else,
 * including the UI, session logic, models and prompts, is shared Kotlin.
 * Swift sees these as protocols with the same names.
 */

/** AVAudioEngine microphone, delivering 16 kHz mono 16-bit PCM. */
interface MicBridge {
    fun requestPermission(callback: PermissionCallback)

    /** Starts capture. Returns an error message, or null on success. */
    fun start(sink: PcmSink): String?

    fun stop()

    /** For example "iPhone Microphone · 48 kHz in". Null when not recording. */
    fun inputLabel(): String?

    /** A problem to show under the waveform, or null. */
    fun inputWarning(): String?
}

/**
 * Sound playing on the phone, streamed in from the "Rewinder Audio" screen broadcast extension
 * (iosApp/RewinderBroadcast) over a loopback-only socket. 16 kHz mono Int16, memory only.
 * Implemented in DeviceAudioBridgeImpl.swift.
 */
interface DeviceAudioBridge {
    /** Listens for the extension and shows the system Screen Broadcast sheet. Error or null. */
    fun start(sink: PcmSink): String?

    /** Stops listening; the extension then ends the broadcast. */
    fun stop()

    /** For example "phone audio". Null when not running. */
    fun label(): String?

    /** For example "Start the Rewinder Audio broadcast", or null. */
    fun warning(): String?
}

interface PermissionCallback {
    fun onResult(granted: Boolean)
}

interface PcmSink {
    /** 16 kHz mono little-endian Int16 samples. */
    fun onPcm(pcm16: NSData)
}

/** whisper.xcframework. Returns the transcript, or null on failure. */
interface SpeechBridge {
    fun transcribe(
        modelPath: String,
        pcm16: NSData,
        language: String,
        prompt: String,
        vadModelPath: String?,
        threads: Int,
    ): String?
}

/** llama.xcframework. Returns the completion, or null on failure. */
interface TextBridge {
    fun complete(
        modelPath: String,
        prompt: String,
        nCtx: Int,
        threads: Int,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        presencePenalty: Float,
    ): String?
}

/** URLSession download into a .part file, resuming with an HTTP Range request. */
interface DownloadBridge {
    fun start(url: String, partPath: String, existingBytes: Long, listener: DownloadListener)
    fun cancel()
}

interface DownloadListener {
    fun onProgress(writtenBytes: Long)

    /** Null error means the file is complete. */
    fun onFinished(error: String?)
}

/** Apple Foundation Models (Apple Intelligence, iOS 26+). Implemented in AppleModelBridgeImpl.swift. */
interface AppleModelBridge {
    /** Null when usable, otherwise a short reason to show in the model picker. */
    fun unavailableReason(): String?

    /** Returns the response, or null on failure (see [lastError]). */
    fun complete(instructions: String, prompt: String, temperature: Float): String?

    fun lastError(): String?
}
