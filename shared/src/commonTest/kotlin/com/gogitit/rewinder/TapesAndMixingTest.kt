package com.gogitit.rewinder

import com.gogitit.rewinder.audio.SampleQueue
import com.gogitit.rewinder.data.Recent
import com.gogitit.rewinder.data.RecentsCodec
import com.gogitit.rewinder.pipeline.Summaries
import com.gogitit.rewinder.pipeline.SummarizerPrompt
import com.gogitit.rewinder.pipeline.SummaryRequest
import com.gogitit.rewinder.settings.AppSettings
import com.gogitit.rewinder.settings.MemoryStore
import com.gogitit.rewinder.settings.SummaryLanguages
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TapesAndMixingTest {
    @Test
    fun tapesRoundTrip() {
        val tapes = listOf(
            Recent(2, "Lunch plans", "Napag-usapan ang tanghalian.", listOf("Jollibee", "12:30"), 1, 1_000L, 181_000L),
            Recent(1, "Empty points", "Nothing much.", emptyList(), 0, 5L, 6L),
        )
        assertEquals(tapes, RecentsCodec.decode(RecentsCodec.encode(tapes)))
        assertEquals(emptyList(), RecentsCodec.decode(""))
    }

    @Test
    fun separatorCharactersCannotBreakTheFormat() {
        val tape = Recent(1, "a\u001Fb", "c\u001Ed", listOf("e\u001Df"), 0, 1L, 2L)
        val back = RecentsCodec.decode(RecentsCodec.encode(listOf(tape))).single()
        assertEquals("ab", back.title)
        assertEquals("cd", back.gist)
        assertEquals(listOf("ef"), back.points)
    }

    @Test
    fun sampleQueueDropsOldestWhenFull() {
        val q = SampleQueue(capacity = 4)
        q.push(shortArrayOf(1, 2, 3))
        q.push(shortArrayOf(4, 5, 6))
        assertContentEquals(shortArrayOf(3, 4), q.pop(2))
        assertContentEquals(shortArrayOf(5, 6), q.pop(10))
        assertNull(q.pop(1))
    }

    @Test
    fun filipinoRecapsAreRequestedInBothPrompts() {
        val request = SummaryRequest("Usapan tungkol sa exam.", false, 0, 180_000L)
        assertTrue(SummarizerPrompt.system(SummaryLanguages.FILIPINO).contains("Filipino (Tagalog)"))
        assertTrue(SummarizerPrompt.user(request, outputLanguage = SummaryLanguages.FILIPINO).endsWith("Isulat ang buod sa Filipino."))
        assertTrue(SummarizerPrompt.system(SummaryLanguages.ENGLISH).contains("simple English"))
        assertEquals("Walang malinaw na narinig", Summaries.nothingHeard(0, SummaryLanguages.FILIPINO).title)
    }

    @Test
    fun qwenBecomesDefaultOnceForOldAppleChoice() {
        val store = MemoryStore().apply { putString("summary_model", "apple") }
        assertEquals("qwen2b", AppSettings(store, "qwen2b").summaryModel.value)
        AppSettings(store, "qwen2b").setSummaryModel("apple")
        assertEquals("apple", AppSettings(store, "qwen2b").summaryModel.value)
    }
}
