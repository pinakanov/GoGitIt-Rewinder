package com.gogitit.rewinder.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Brings quiet recordings up to a level speech models handle well (about -20 dBFS),
 * without clipping. Phone mics far from the speaker often record at -45 dBFS or lower,
 * which Whisper and its voice detector treat as silence.
 */
object AudioGain {
    private const val TARGET_RMS = 0.1      // about -20 dBFS
    private const val MAX_GAIN = 30.0       // never boost more than ~30 dB
    private const val PEAK_LIMIT = 31_000.0 // stay just under full scale

    /** Returns the gain that was applied (1.0 = unchanged). */
    fun normalizeInPlace(pcm: ShortArray): Double {
        if (pcm.isEmpty()) return 1.0
        var sum = 0.0
        var peak = 0
        for (s in pcm) {
            val v = s.toInt()
            sum += v.toDouble() * v
            peak = max(peak, abs(v))
        }
        if (peak == 0) return 1.0
        val rms = sqrt(sum / pcm.size) / Short.MAX_VALUE
        val gain = min(min(TARGET_RMS / rms, MAX_GAIN), PEAK_LIMIT / peak)
        if (gain <= 1.2) return 1.0
        for (i in pcm.indices) {
            pcm[i] = (pcm[i] * gain).toInt().coerceIn(-32768, 32767).toShort()
        }
        return gain
    }

    /** True when a chunk is exact digital silence: what Android delivers when capture is blocked. */
    fun isDigitalSilence(chunk: ShortArray): Boolean {
        for (s in chunk) if (s.toInt() != 0) return false
        return true
    }
}
