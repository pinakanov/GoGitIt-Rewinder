package com.gogitit.rewinder

import com.gogitit.rewinder.core.AudioRingBuffer
import com.gogitit.rewinder.core.formatDuration
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class AudioRingBufferTest {

    private fun seq(from: Int, count: Int) = ShortArray(count) { (from + it).toShort() }

    @Test
    fun keepsOnlyTheNewestWindow() {
        val buf = AudioRingBuffer(sampleRate = 10, windowSeconds = 1) // capacity 10
        buf.write(seq(0, 7))
        buf.write(seq(7, 8)) // 15 samples total, oldest 5 overwritten
        assertEquals(10, buf.samplesSinceCut)
        assertContentEquals(seq(5, 10), buf.snapshotSinceCut())
    }

    @Test
    fun cutPreventsRepeatingAudio() {
        val buf = AudioRingBuffer(sampleRate = 10, windowSeconds = 1)
        buf.write(seq(0, 6))
        buf.advanceCut()
        assertEquals(0, buf.samplesSinceCut)
        buf.write(seq(6, 3))
        assertContentEquals(seq(6, 3), buf.snapshotSinceCut())
    }

    @Test
    fun oversizedChunkKeepsItsTail() {
        val buf = AudioRingBuffer(sampleRate = 10, windowSeconds = 1)
        buf.write(seq(0, 25))
        assertContentEquals(seq(15, 10), buf.snapshotSinceCut())
    }

    @Test
    fun wipeClearsEverything() {
        val buf = AudioRingBuffer(sampleRate = 10, windowSeconds = 1)
        buf.write(seq(1, 10))
        buf.wipe()
        assertEquals(0, buf.samplesSinceCut)
        assertEquals(0L, buf.millisSinceCut())
    }

    @Test
    fun millisReflectSampleRate() {
        val buf = AudioRingBuffer(sampleRate = 16_000, windowSeconds = 300)
        buf.write(ShortArray(16_000 * 181))
        assertEquals(181_000L, buf.millisSinceCut())
        buf.write(ShortArray(16_000 * 200))
        assertEquals(300_000L, buf.millisSinceCut()) // capped at the window
    }

    @Test
    fun formatsDurations() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("2:41", formatDuration(161_000))
        assertEquals("3:00", formatDuration(179_200)) // rounds up for countdowns
    }
}
