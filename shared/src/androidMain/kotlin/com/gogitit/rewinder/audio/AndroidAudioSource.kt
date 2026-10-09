package com.gogitit.rewinder.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
 * Reads the active input (built-in, wired or Bluetooth mic) with AudioRecord.
 * Samples go straight into the shared ring buffer in memory; nothing is written to disk.
 *
 * Source order: MIC first (it has the phone's automatic gain, so distant voices are audible),
 * then VOICE_RECOGNITION and DEFAULT as fallbacks for devices where MIC will not open.
 *
 * Logcat tag "RewinderAudio" reports the chosen source and input level every 5 seconds.
 * The caller must hold RECORD_AUDIO (MainActivity asks before starting).
 */
class AndroidAudioSource : AudioSource {

    @SuppressLint("MissingPermission")
    override fun frames(sampleRate: Int): Flow<ShortArray> = callbackFlow {
        val chunk = sampleRate / 10 // 100 ms
        val record = openRecorder(sampleRate, chunk)
        if (record == null) {
            close(IllegalStateException("The microphone is not available right now. Another app may be using it."))
            return@callbackFlow
        }

        val running = AtomicBoolean(true)
        val reader = thread(name = "rewinder-audio", isDaemon = true) {
            val buf = ShortArray(chunk)
            var peak = 0
            var sumSquares = 0.0
            var counted = 0L
            while (running.get()) {
                val n = record.read(buf, 0, chunk)
                when {
                    n > 0 -> {
                        for (i in 0 until n) {
                            val s = buf[i].toInt()
                            peak = max(peak, abs(s))
                            sumSquares += s.toDouble() * s
                        }
                        counted += n
                        if (counted >= sampleRate * 5L) {
                            val rms = sqrt(sumSquares / counted) / Short.MAX_VALUE
                            Log.d(TAG, "input level: rms %.1f dBFS, peak %d".format(20 * log10(rms.coerceAtLeast(1e-9)), peak))
                            peak = 0; sumSquares = 0.0; counted = 0L
                        }
                        trySend(buf.copyOf(n))
                    }
                    n < 0 -> {
                        Log.e(TAG, "read failed: $n")
                        close(IllegalStateException("Microphone read failed ($n)."))
                        break
                    }
                }
            }
            buf.fill(0)
        }

        awaitClose {
            running.set(false)
            runCatching { record.stop() }
            reader.join(500)
            record.release()
        }
    }

    @SuppressLint("MissingPermission")
    private fun openRecorder(sampleRate: Int, chunk: Int): AudioRecord? {
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferBytes = maxOf(minBuffer, chunk * 2 * 4)
        for ((source, name) in SOURCES) {
            val record = runCatching {
                AudioRecord(source, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferBytes)
            }.getOrNull() ?: continue
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "source $name did not initialize")
                record.release()
                continue
            }
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.w(TAG, "source $name did not start recording")
                record.release()
                continue
            }
            Log.i(TAG, "recording from $name at $sampleRate Hz")
            return record
        }
        return null
    }

    private companion object {
        const val TAG = "RewinderAudio"
        val SOURCES = listOf(
            MediaRecorder.AudioSource.MIC to "MIC",
            MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION",
            MediaRecorder.AudioSource.DEFAULT to "DEFAULT",
        )
    }
}
