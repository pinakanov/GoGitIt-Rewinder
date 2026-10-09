package com.gogitit.rewinder.audio

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

/**
 * The microphone, optionally mixed with sound playing on the phone ("device audio": a video,
 * an online class in the browser, a recorded lecture).
 *
 * Mic chunks set the pace; device samples that arrived in between are added on top. When the
 * mic stalls or is cut off (for example the OS takes it for a call), device audio keeps flowing
 * on its own. Everything stays in memory, like the mic path.
 *
 * Calls themselves cannot be captured: Android and iOS do not let other apps record phone or
 * VoIP call audio, and both mute other apps' microphones during a call.
 */
class MixedAudioSource(
    private val mic: AudioSource,
    private val device: AudioSource?,
    private val useDevice: () -> Boolean,
) : AudioSource {

    @kotlin.concurrent.Volatile private var mixing = false
    @kotlin.concurrent.Volatile private var micProblem: String? = null
    @kotlin.concurrent.Volatile private var deviceProblem: String? = null
    @kotlin.concurrent.Volatile private var deviceHeard = false

    /** True when the device can capture its own audio at all. */
    val supportsDeviceAudio: Boolean get() = device != null

    override fun frames(sampleRate: Int): Flow<ShortArray> {
        val device = device
        if (device == null || !useDevice()) {
            mixing = false
            return mic.frames(sampleRate)
        }
        return channelFlow {
            mixing = true
            micProblem = null
            deviceProblem = null
            deviceHeard = false
            val pending = SampleQueue(capacity = sampleRate * 2) // at most 2 s waiting to be mixed
            val lock = Mutex()
            var lastMic = TimeSource.Monotonic.markNow()
            var micAlive = true
            var deviceFailed = false
            val chunk = sampleRate / 10

            launch {
                try {
                    device.frames(sampleRate).collect { samples ->
                        if (!deviceHeard && samples.any { it.toInt() != 0 }) deviceHeard = true
                        lock.withLock { pending.push(samples) }
                        samples.fill(0)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    deviceFailed = true
                    deviceProblem = t.message ?: "Device audio stopped."
                    if (!micAlive) close(t)
                }
            }

            // When the mic is silent-by-absence (stalled, interrupted), keep device audio moving.
            launch {
                while (true) {
                    delay(100)
                    if (lastMic.elapsedNow().inWholeMilliseconds > 300) {
                        val out = lock.withLock { pending.pop(chunk * 2) }
                        if (out != null) send(out)
                    }
                }
            }

            try {
                mic.frames(sampleRate).collect { samples ->
                    lastMic = TimeSource.Monotonic.markNow()
                    val extra = lock.withLock { pending.pop(samples.size) }
                    if (extra != null) {
                        mixInto(samples, extra)
                        extra.fill(0)
                    }
                    send(samples)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                micAlive = false
                micProblem = t.message
                if (deviceFailed) throw t
                awaitCancellation() // device audio carries on alone
            }
        }
    }

    override fun status(): InputStatus? {
        val micStatus = mic.status()
        if (!mixing) return micStatus
        val deviceStatus = device?.status()
        val label = buildString {
            append(micStatus?.label ?: "Mic")
            append(" + ")
            append(deviceStatus?.label ?: if (deviceHeard) "device audio" else "device audio (waiting)")
        }
        val warning = micProblem ?: micStatus?.warning ?: deviceProblem ?: deviceStatus?.warning
        return InputStatus(label, warning)
    }

    private fun mixInto(target: ShortArray, extra: ShortArray) {
        val n = minOf(target.size, extra.size)
        for (i in 0 until n) {
            target[i] = (target[i] + extra[i]).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }
}

/** Fixed-size FIFO of samples; when full, the oldest samples are dropped. Not thread-safe. */
internal class SampleQueue(private val capacity: Int) {
    private val data = ShortArray(capacity)
    private var head = 0
    private var size = 0

    fun push(samples: ShortArray) {
        for (s in samples) {
            if (size == capacity) {
                head = (head + 1) % capacity
                size--
            }
            data[(head + size) % capacity] = s
            size++
        }
    }

    /** Up to [max] samples, or null when empty. */
    fun pop(max: Int): ShortArray? {
        if (size == 0) return null
        val n = minOf(max, size)
        val out = ShortArray(n)
        for (i in 0 until n) {
            out[i] = data[(head + i) % capacity]
            data[(head + i) % capacity] = 0
        }
        head = (head + n) % capacity
        size -= n
        return out
    }
}
