package com.gogitit.rewinder.core

import kotlin.math.max

/**
 * Fixed size, in-memory ring buffer of PCM16 samples. Never touches disk.
 *
 * Positions are absolute sample counts since the session started. The "cut" marks
 * where the previous Rewind ended, so consecutive rewinds never repeat audio.
 *
 * Not thread safe on purpose: RewinderController confines every call to a
 * single-threaded dispatcher.
 */
class AudioRingBuffer(
    val sampleRate: Int,
    windowSeconds: Int,
) {
    val capacity: Int = sampleRate * windowSeconds
    private val data = ShortArray(capacity)
    private var written: Long = 0L
    private var cut: Long = 0L

    /** First absolute position still available, honoring both the window and the cut. */
    private val start: Long get() = max(cut, max(0L, written - capacity))

    val samplesSinceCut: Int get() = (written - start).toInt()

    fun millisSinceCut(): Long = samplesSinceCut * 1000L / sampleRate

    fun write(chunk: ShortArray, length: Int = chunk.size) {
        var offset = 0
        var remaining = length
        // A chunk larger than the window only keeps its newest part.
        if (remaining > capacity) {
            offset = remaining - capacity
            written += offset
            remaining = capacity
        }
        while (remaining > 0) {
            val pos = (written % capacity).toInt()
            val n = minOf(remaining, capacity - pos)
            chunk.copyInto(data, destinationOffset = pos, startIndex = offset, endIndex = offset + n)
            offset += n
            remaining -= n
            written += n
        }
    }

    /** Copies everything since the cut (at most one window) in chronological order. */
    fun snapshotSinceCut(): ShortArray {
        val from = start
        val count = (written - from).toInt()
        val out = ShortArray(count)
        var copied = 0
        var abs = from
        while (copied < count) {
            val pos = (abs % capacity).toInt()
            val n = minOf(count - copied, capacity - pos)
            data.copyInto(out, destinationOffset = copied, startIndex = pos, endIndex = pos + n)
            copied += n
            abs += n
        }
        return out
    }

    /** Called after a Rewind: the next one only covers audio recorded after now. */
    fun advanceCut() {
        cut = written
    }

    /** Zero-fills memory. Called on Stop. */
    fun wipe() {
        data.fill(0)
        written = 0L
        cut = 0L
    }
}
