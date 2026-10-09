package com.gogitit.rewinder

import com.gogitit.rewinder.core.AudioGain
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudioGainTest {

    private fun tone(amplitude: Int, n: Int = 16_000) =
        ShortArray(n) { (amplitude * sin(2 * PI * 220 * it / 16_000.0)).toInt().toShort() }

    private fun rms(pcm: ShortArray) = sqrt(pcm.sumOf { it.toDouble() * it } / pcm.size) / Short.MAX_VALUE

    @Test
    fun quietSpeechIsBoostedTowardMinus20dB() {
        val pcm = tone(300) // about -40 dBFS
        val gain = AudioGain.normalizeInPlace(pcm)
        assertTrue(gain > 5.0, "gain $gain")
        assertTrue(rms(pcm) in 0.06..0.12, "rms ${rms(pcm)}")
    }

    @Test
    fun neverClips() {
        val pcm = tone(300)
        pcm[100] = 20_000 // one loud click limits the gain
        AudioGain.normalizeInPlace(pcm)
        assertTrue(pcm.all { abs(it.toInt()) <= 31_000 })
    }

    @Test
    fun loudAudioIsLeftAlone() {
        val pcm = tone(8_000)
        val copy = pcm.copyOf()
        assertEquals(1.0, AudioGain.normalizeInPlace(pcm))
        assertTrue(pcm.contentEquals(copy))
    }

    @Test
    fun detectsDigitalSilence() {
        assertTrue(AudioGain.isDigitalSilence(ShortArray(1600)))
        assertFalse(AudioGain.isDigitalSilence(ShortArray(1600) { if (it == 7) 1 else 0 }))
        assertEquals(1.0, AudioGain.normalizeInPlace(ShortArray(1600)))
    }
}
