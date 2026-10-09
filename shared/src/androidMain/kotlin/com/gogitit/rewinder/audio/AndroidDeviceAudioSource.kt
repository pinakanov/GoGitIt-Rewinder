package com.gogitit.rewinder.audio

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * The user's "Start recording or casting?" answer for one session. Android only allows a
 * screen-capture token to be used once, so MainActivity asks again on every REC.
 */
object DeviceAudioConsent {
    @Volatile private var resultCode: Int = 0
    @Volatile private var data: Intent? = null

    /** Set by the foreground service once it runs with the mediaProjection type. */
    @Volatile var foregroundReady: Boolean = false

    /**
     * True from the user's "Start now" until the session ends. The service reads this to start
     * with the mediaProjection type; it stays true after [take] so the order of the two does
     * not matter.
     */
    @Volatile var grantedThisSession: Boolean = false
        private set

    fun grant(resultCode: Int, data: Intent) {
        this.resultCode = resultCode
        this.data = data
        grantedThisSession = true
    }

    /** Hands the grant out once. */
    @Synchronized
    fun take(): Pair<Int, Intent>? {
        val d = data ?: return null
        data = null
        return resultCode to d
    }

    fun clear() {
        data = null
        grantedThisSession = false
        foregroundReady = false
    }
}

/**
 * Sound playing on this phone, through AudioPlaybackCapture (Android 10+): videos, online
 * classes in the browser, recorded lectures, games. Only in memory, like the mic.
 *
 * What Android does not allow: phone and VoIP call audio (USAGE_VOICE_COMMUNICATION) and apps
 * that opt out of capture are silent here. That is a platform privacy rule, not a setting.
 */
/** Only constructed on Android 10+ (see RewinderApplication). */
@TargetApi(Build.VERSION_CODES.Q)
class AndroidDeviceAudioSource(context: Context) : AudioSource {
    private val appContext = context.applicationContext
    private val projections = appContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    @Volatile private var label: String? = null
    @Volatile private var warning: String? = null

    @SuppressLint("MissingPermission")
    override fun frames(sampleRate: Int): Flow<ShortArray> = callbackFlow {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            close(IllegalStateException("Phone audio needs Android 10 or newer. Recording the microphone only."))
            return@callbackFlow
        }
        val grant = DeviceAudioConsent.take()
        if (grant == null) {
            close(IllegalStateException("Phone audio was not allowed (screen-capture prompt). Recording the microphone only."))
            return@callbackFlow
        }
        // Android 14+: the projection may only be created while our foreground service runs
        // with the mediaProjection type, so wait for it (it starts right after REC).
        var waited = 0
        while (!DeviceAudioConsent.foregroundReady && waited < 5000) {
            delay(50)
            waited += 50
        }
        val projection = runCatching { projections.getMediaProjection(grant.first, grant.second) }
            .onFailure { Log.e(TAG, "getMediaProjection failed", it) }
            .getOrNull()
        if (projection == null) {
            close(IllegalStateException("Android did not start phone audio capture. Recording the microphone only."))
            return@callbackFlow
        }
        val running = AtomicBoolean(true)
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                // Stopped from the status bar chip or Quick Settings.
                warning = "Phone audio was stopped from the status bar. Recording the microphone only."
                running.set(false)
            }
        }, Handler(Looper.getMainLooper()))

        val opened = open(projection, sampleRate)
        if (opened == null) {
            projection.stop()
            close(IllegalStateException("This phone did not allow capturing its own audio. Recording the microphone only."))
            return@callbackFlow
        }
        val (record, rate) = opened
        label = "phone audio"
        warning = null
        Log.i(TAG, "capturing playback at $rate Hz")

        val producer = this
        val reader = thread(name = "rewinder-device-audio", isDaemon = true) {
            val factor = rate / sampleRate
            val raw = ShortArray(sampleRate / 10 * factor)
            try {
                while (running.get()) {
                    val n = record.read(raw, 0, raw.size)
                    if (n < 0) break
                    if (n == 0) continue
                    producer.trySend(downsample(raw, n, factor))
                }
            } finally {
                raw.fill(0)
            }
        }

        awaitClose {
            running.set(false)
            runCatching { record.stop() }
            reader.join(1000)
            record.release()
            runCatching { projection.stop() }
            label = null
            DeviceAudioConsent.clear()
        }
    }

    override fun status(): InputStatus? = label?.let { InputStatus(it, warning) }
        ?: warning?.let { InputStatus("phone audio off", it) }

    @SuppressLint("MissingPermission")
    private fun open(projection: MediaProjection, sampleRate: Int): Pair<AudioRecord, Int>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        for (rate in listOf(sampleRate, 48_000)) {
            val record = runCatching {
                AudioRecord.Builder()
                    .setAudioPlaybackCaptureConfig(config)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(rate / 10 * 2 * 4)
                    .build()
            }.getOrNull() ?: continue
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                continue
            }
            runCatching { record.startRecording() }
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) return record to rate
            record.release()
        }
        return null
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

    private companion object {
        const val TAG = "RewinderDeviceAudio"
    }
}
