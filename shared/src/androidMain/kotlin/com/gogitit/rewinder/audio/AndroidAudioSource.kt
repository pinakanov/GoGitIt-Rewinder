package com.gogitit.rewinder.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Reads the active input (built-in, wired, USB or Bluetooth mic) with AudioRecord.
 * Samples go straight into the shared ring buffer in memory; nothing is written to disk.
 *
 * Some phones open a mic source fine but deliver pure silence for it. So each candidate
 * (source x sample rate) is probed for one second, and the first one that hears anything
 * is kept. 48 kHz inputs are averaged down to 16 kHz.
 *
 * [status] reports the routed device and whether Android is muting this app, shown on the
 * home screen. Logcat tag "RewinderAudio" has the details.
 * The caller must hold RECORD_AUDIO (MainActivity asks before starting).
 */
class AndroidAudioSource(context: Context) : AudioSource {

    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Volatile private var active: AudioRecord? = null
    @Volatile private var activeLabel: String = ""

    @SuppressLint("MissingPermission")
    override fun frames(sampleRate: Int): Flow<ShortArray> = callbackFlow {
        val producer = this
        unmuteIfStuck()
        val running = AtomicBoolean(true)

        val reader = thread(name = "rewinder-audio", isDaemon = true) {
            var record: AudioRecord? = null
            var reopenAfterSilentSeconds = 3L
            try {
                // Outer loop: (re)open the mic. Inputs can go dead mid-session (emulator host mic
                // dropping, Bluetooth disconnect, routing change), so a dead input is reopened.
                while (running.get()) {
                    val opened = openWorkingInput(sampleRate, running) { producer.trySend(it) }
                    if (opened == null) {
                        if (running.get()) {
                            producer.close(IllegalStateException("The microphone is not available right now. Another app may be using it."))
                        }
                        return@thread
                    }
                    record = opened.record
                    active = opened.record
                    activeLabel = "${opened.name} ${opened.rate / 1000} kHz"
                    Log.i(TAG, "recording from ${opened.name} at ${opened.rate} Hz, routed to ${deviceName(opened.record.routedDevice)}")

                    val factor = opened.rate / sampleRate
                    val raw = ShortArray((sampleRate / 10) * factor) // 100 ms
                    var peak = 0
                    var sumSquares = 0.0
                    var counted = 0L
                    var zeroRun = 0L
                    var reopen = false
                    var sawCall = false
                    var reads = 0L
                    while (running.get()) {
                        val n = opened.record.read(raw, 0, raw.size)
                        if (n < 0) {
                            Log.e(TAG, "read failed: $n, reopening")
                            reopen = true
                            break
                        }
                        if (n == 0) continue
                        var allZero = true
                        for (i in 0 until n) {
                            val s = raw[i].toInt()
                            if (s != 0) allZero = false
                            peak = max(peak, abs(s))
                            sumSquares += s.toDouble() * s
                        }
                        counted += n
                        zeroRun = if (allZero) zeroRun + n else 0L
                        if (counted >= opened.rate * 5L) {
                            val rms = sqrt(sumSquares / counted) / Short.MAX_VALUE
                            Log.d(TAG, "input level: rms %.1f dBFS, peak %d, %s".format(
                                20 * log10(rms.coerceAtLeast(1e-9)), peak, status()?.let { "${it.label} ${it.warning ?: ""}" } ?: ""))
                            peak = 0; sumSquares = 0.0; counted = 0L
                        }
                        producer.trySend(downsample(raw, n, factor))
                        // During a call Android hands the mic to the call app and feeds us silence.
                        // Reopening then is pointless; reopen the moment the call ends instead.
                        if (++reads % 5L == 0L) {
                            if (inCall()) {
                                sawCall = true
                            } else if (sawCall) {
                                sawCall = false
                                if (zeroRun > 0) {
                                    Log.i(TAG, "call ended, reopening the mic")
                                    reopenAfterSilentSeconds = 1L
                                    reopen = true
                                    break
                                }
                            }
                        }
                        if (sawCall) continue
                        if (zeroRun >= opened.rate * reopenAfterSilentSeconds) {
                            Log.w(TAG, "input went dead (exact zeros for ${reopenAfterSilentSeconds}s), reopening")
                            reopen = true
                            break
                        }
                        if (!allZero) reopenAfterSilentSeconds = 3L
                    }
                    raw.fill(0)
                    active = null
                    runCatching { opened.record.stop() }
                    opened.record.release()
                    record = null
                    if (!reopen) break
                    // Back off if reopening does not help (Android muting the app), up to 30 s.
                    reopenAfterSilentSeconds = (reopenAfterSilentSeconds * 2).coerceIn(3L, 30L)
                }
            } finally {
                active = null
                record?.let {
                    runCatching { it.stop() }
                    it.release()
                }
            }
        }

        awaitClose {
            running.set(false)
            active?.let { runCatching { it.stop() } } // unblocks read()
            reader.join(1500)
        }
    }

    override fun status(): InputStatus? {
        val record = active ?: return null
        val device = deviceName(record.routedDevice)
        val silenced = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            record.activeRecordingConfiguration?.isClientSilenced == true
        val others = runCatching { audioManager.activeRecordingConfigurations.size - 1 }.getOrDefault(0)
        val warning = when {
            inCall() -> "A call is using the microphone. Android mutes other apps during calls; " +
                "Rewinder hears again when it ends (phone audio, if on, keeps recording)."
            silenced -> "Android is muting this app's mic (privacy toggle or another app has priority)."
            audioManager.isMicrophoneMute -> "The phone's microphone is muted system-wide."
            others > 0 -> "Another app is also recording."
            else -> null
        }
        return InputStatus(label = "$device · $activeLabel", warning = warning)
    }

    private class Opened(val record: AudioRecord, val name: String, val rate: Int)

    /**
     * Tries each source and rate; keeps the first that hears anything in one second.
     * If every input is silent (Android muting the app), falls back to the first that opened.
     */
    @SuppressLint("MissingPermission")
    private fun openWorkingInput(sampleRate: Int, running: AtomicBoolean, emit: (ShortArray) -> Unit): Opened? {
        var fallback: Pair<Int, Int>? = null
        for ((source, name) in SOURCES) {
            for (rate in listOf(sampleRate, 48_000).distinct()) {
                if (!running.get()) return null
                val record = open(source, rate) ?: continue
                if (fallback == null) fallback = source to rate
                val silenced = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    record.activeRecordingConfiguration?.isClientSilenced == true
                // Probe one second. The probe audio is kept, so nothing is lost while testing.
                val factor = rate / sampleRate
                val probe = ShortArray(rate / 10)
                var peak = 0
                repeat(10) {
                    val n = record.read(probe, 0, probe.size)
                    if (n > 0) {
                        for (i in 0 until n) peak = max(peak, abs(probe[i].toInt()))
                        emit(downsample(probe, n, factor))
                    }
                }
                Log.i(TAG, "probe $name @ $rate Hz: peak $peak, silenced=$silenced, device=${deviceName(record.routedDevice)}")
                if (peak > SILENCE_PEAK || silenced) {
                    // Hearing something, or Android is muting every source anyway: stop probing.
                    return Opened(record, name, rate)
                }
                runCatching { record.stop() }
                record.release()
            }
        }
        val (source, rate) = fallback ?: return null
        val record = open(source, rate) ?: return null
        val name = SOURCES.first { entry -> entry.first == source }.second
        Log.w(TAG, "every input was silent; staying on $name @ $rate Hz")
        return Opened(record, name, rate)
    }

    @SuppressLint("MissingPermission")
    private fun open(source: Int, rate: Int): AudioRecord? {
        val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return null
        val record = runCatching {
            AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, rate / 10 * 2 * 4))
        }.getOrNull() ?: return null
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return null
        }
        runCatching { record.startRecording() }
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            record.release()
            return null
        }
        return record
    }

    private fun inCall(): Boolean = runCatching {
        val mode = audioManager.mode
        mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && mode == AudioManager.MODE_CALL_SCREENING)
    }.getOrDefault(false)

    /** Some phones leave the global mic mute on after a VoIP call; every app then hears silence. */
    private fun unmuteIfStuck() {
        if (audioManager.isMicrophoneMute) {
            Log.w(TAG, "microphone was muted system-wide; unmuting")
            runCatching { audioManager.isMicrophoneMute = false }
        }
    }

    private fun downsample(src: ShortArray, n: Int, factor: Int): ShortArray {
        if (factor <= 1) return src.copyOf(n)
        val out = ShortArray(n / factor)
        for (i in out.indices) {
            var sum = 0
            for (k in 0 until factor) sum += src[i * factor + k]
            out[i] = (sum / factor).toShort()
        }
        return out
    }

    private fun deviceName(device: AudioDeviceInfo?): String = when (device?.type) {
        null -> "Default input"
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in mic"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset mic"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth headset mic"
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB mic"
        AudioDeviceInfo.TYPE_TELEPHONY -> "Phone call audio"
        AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> "Remote submix"
        26 /* TYPE_BLE_HEADSET */ -> "Bluetooth LE mic"
        else -> "Input type ${device?.type}"
    }

    private companion object {
        const val TAG = "RewinderAudio"
        const val SILENCE_PEAK = 4
        val SOURCES = listOf(
            MediaRecorder.AudioSource.MIC to "MIC",
            MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION",
            MediaRecorder.AudioSource.CAMCORDER to "CAMCORDER",
            MediaRecorder.AudioSource.UNPROCESSED to "UNPROCESSED",
            MediaRecorder.AudioSource.DEFAULT to "DEFAULT",
        )
    }
}
