package com.gogitit.rewinder.audio

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

/**
 * Platform microphone. Emits mono PCM16 chunks (about 100 ms each) while collected,
 * and releases the mic as soon as collection is cancelled.
 *
 * Android: AndroidAudioSource (AudioRecord). iOS: implement with AVAudioEngine.
 */
interface AudioSource {
    fun frames(sampleRate: Int): Flow<ShortArray>
}

/**
 * Low-level noise generator. Used for previews and as the iOS stand-in until the
 * AVAudioEngine source exists, so the UI and timing rules can be tested anywhere.
 */
class SimulatedAudioSource : AudioSource {
    override fun frames(sampleRate: Int): Flow<ShortArray> = flow {
        val chunk = sampleRate / 10
        var t = 0
        while (true) {
            delay(100)
            val loud = (t / 12) % 3 != 2 // a few seconds of "speech", then a pause
            val amp = if (loud) 2500 + Random.nextInt(2500) else 150
            emit(ShortArray(chunk) { (Random.nextInt(-amp, amp)).toShort() })
            t++
        }
    }
}
