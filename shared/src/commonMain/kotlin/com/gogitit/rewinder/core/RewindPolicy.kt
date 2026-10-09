package com.gogitit.rewinder.core

/**
 * Product rules from docs/SPEC.md, in one place.
 */
object RewindPolicy {
    /** 16 kHz mono PCM16 is what on-device speech models (Whisper class) expect. */
    const val SAMPLE_RATE: Int = 16_000

    /** Rolling memory. Anything older is overwritten and gone. */
    const val WINDOW_SECONDS: Int = 5 * 60

    /** Rewind stays locked until this much audio is buffered since the last cut. */
    const val MIN_REWIND_SECONDS: Int = 3 * 60

    const val MIN_REWIND_MILLIS: Long = MIN_REWIND_SECONDS * 1000L
    const val WINDOW_MILLIS: Long = WINDOW_SECONDS * 1000L
}

/** 161_000 -> "2:41". Used for countdowns and buffer labels. */
fun formatDuration(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0L) + 999L) / 1000L
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
