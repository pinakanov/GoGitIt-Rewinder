@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.gogitit.rewinder.ios

import com.gogitit.rewinder.ai.SpeechEngine
import com.gogitit.rewinder.ai.TextEngine
import com.gogitit.rewinder.audio.AudioSource
import com.gogitit.rewinder.audio.InputStatus
import com.gogitit.rewinder.models.ModelDownloader
import com.gogitit.rewinder.models.ModelStorage
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal fun NSData.toShortArray(): ShortArray {
    val count = (length.toLong() / 2L).toInt()
    val out = ShortArray(count)
    if (count == 0) return out
    out.usePinned { memcpy(it.addressOf(0), bytes, (count * 2).toULong()) }
    return out
}

internal fun ShortArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { NSData.create(bytes = it.addressOf(0), length = (size * 2).toULong()) }
}

/** Shared AudioSource on top of the Swift AVAudioEngine microphone. */
class IosAudioSource(private val mic: MicBridge) : AudioSource {
    override fun frames(sampleRate: Int): Flow<ShortArray> = callbackFlow {
        val error = mic.start(object : PcmSink {
            override fun onPcm(pcm16: NSData) {
                trySend(pcm16.toShortArray())
            }
        })
        if (error != null) {
            close(IllegalStateException(error))
            return@callbackFlow
        }
        awaitClose { mic.stop() }
    }

    override fun status(): InputStatus? = mic.inputLabel()?.let { InputStatus(it, mic.inputWarning()) }
}

class IosSpeechEngine(private val bridge: SpeechBridge) : SpeechEngine {
    override fun transcribe(
        modelPath: String,
        pcm: ShortArray,
        language: String,
        prompt: String,
        vadModelPath: String?,
        threads: Int,
    ): String = bridge.transcribe(modelPath, pcm.toNSData(), language, prompt, vadModelPath, threads)
        ?: error("Transcription failed.")
}

class IosTextEngine(private val bridge: TextBridge) : TextEngine {
    override fun complete(
        modelPath: String,
        prompt: String,
        nCtx: Int,
        threads: Int,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        presencePenalty: Float,
    ): String = bridge.complete(modelPath, prompt, nCtx, threads, maxTokens, temperature, topP, topK, presencePenalty)
        ?: error("The summary model could not process this rewind.")
}

/**
 * Models live in Documents/models, excluded from iCloud backup. File sharing is on in
 * Info.plist, so during development you can drag the files in with Finder.
 */
class IosModelStorage : ModelStorage {
    override val directory: String = run {
        val docs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String
        val dir = "$docs/models"
        NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
        NSURL.fileURLWithPath(dir).setResourceValue(NSNumber.numberWithBool(true), forKey = NSURLIsExcludedFromBackupKey, error = null)
        dir
    }

    override fun sizeOf(path: String): Long {
        val fm = NSFileManager.defaultManager
        if (!fm.fileExistsAtPath(path)) return -1L
        val attrs = fm.attributesOfItemAtPath(path, error = null) ?: return -1L
        return (attrs[NSFileSize] as? NSNumber)?.longLongValue ?: -1L
    }

    override fun move(from: String, to: String): Boolean {
        val fm = NSFileManager.defaultManager
        if (fm.fileExistsAtPath(to)) fm.removeItemAtPath(to, error = null)
        return fm.moveItemAtPath(from, toPath = to, error = null)
    }
}

class IosModelDownloader(private val bridge: DownloadBridge) : ModelDownloader {
    override suspend fun download(
        url: String,
        partPath: String,
        existingBytes: Long,
        onProgress: (writtenBytes: Long) -> Unit,
    ) {
        val report = onProgress
        suspendCancellableCoroutine<Unit> { cont ->
            bridge.start(url, partPath, existingBytes, object : DownloadListener {
                override fun onProgress(writtenBytes: Long) {
                    report(writtenBytes)
                }

                override fun onFinished(error: String?) {
                    if (!cont.isActive) return
                    if (error == null) cont.resume(Unit) else cont.resumeWithException(IllegalStateException(error))
                }
            })
            cont.invokeOnCancellation { bridge.cancel() }
        }
    }
}
