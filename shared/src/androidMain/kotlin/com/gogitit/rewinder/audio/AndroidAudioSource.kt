package com.gogitit.rewinder.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Reads the active input (built-in, wired or Bluetooth mic) with AudioRecord.
 * Samples go straight into the shared ring buffer in memory; nothing is written to disk.
 *
 * The caller must hold RECORD_AUDIO (MainActivity asks before starting).
 */
class AndroidAudioSource : AudioSource {

    @SuppressLint("MissingPermission")
    override fun frames(sampleRate: Int): Flow<ShortArray> = callbackFlow {
        val chunk = sampleRate / 10 // 100 ms
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, chunk * 2 * 4),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            close(IllegalStateException("The microphone is not available right now."))
            return@callbackFlow
        }

        val running = AtomicBoolean(true)
        record.startRecording()
        val reader = thread(name = "rewinder-audio", isDaemon = true) {
            val buf = ShortArray(chunk)
            while (running.get()) {
                val n = record.read(buf, 0, chunk)
                when {
                    n > 0 -> trySend(buf.copyOf(n))
                    n < 0 -> {
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
}
