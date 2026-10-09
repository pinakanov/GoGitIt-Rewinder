package com.gogitit.rewinder

import com.gogitit.rewinder.pipeline.QwenChatFormat
import com.gogitit.rewinder.pipeline.SummarizerPrompt
import com.gogitit.rewinder.pipeline.SummaryParser
import com.gogitit.rewinder.pipeline.SummaryRequest
import com.gogitit.rewinder.pipeline.TranscriptCleaner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextFormatsTest {

    @Test
    fun parsesWellFormedAnswer() {
        val raw = """
            TITLE: Lunch plans for Friday
            GIST: The group decided where to eat after class on Friday.
            - They picked the ramen place near the gate.
            - Meet at 12:30 in the lobby.
            - You were asked to reserve a table.
        """.trimIndent()
        val s = SummaryParser.parse(raw, redactedCount = 1)
        assertEquals("Lunch plans for Friday", s.title)
        assertEquals("The group decided where to eat after class on Friday.", s.gist)
        assertEquals(3, s.points.size)
        assertEquals("You were asked to reserve a table.", s.points[2])
        assertEquals(1, s.redactedCount)
    }

    @Test
    fun toleratesMarkdownThinkingAndTaglish() {
        val raw = "<think>\nhmm\n</think>\n**TITLE:** \"Usapan tungkol sa project\"\n**GIST:** Pinag-usapan ang deadline ng project.\n* Sa Lunes na ang pasahan.\n1. Ikaw ang gagawa ng slides."
        val s = SummaryParser.parse(raw, 0)
        assertEquals("Usapan tungkol sa project", s.title)
        assertEquals("Pinag-usapan ang deadline ng project.", s.gist)
        assertEquals(listOf("Sa Lunes na ang pasahan.", "Ikaw ang gagawa ng slides."), s.points)
    }

    @Test
    fun fallsBackWhenFormatIsIgnored() {
        val s = SummaryParser.parse("They talked about the exam schedule and agreed to review together.", 0)
        assertEquals("Rewind", s.title)
        assertEquals("They talked about the exam schedule and agreed to review together.", s.gist)
        assertTrue(s.points.isEmpty())
    }

    @Test
    fun longTitlesAreShortened() {
        val s = SummaryParser.parse("TITLE: one two three four five six seven eight.\nGIST: x", 0)
        assertEquals("one two three four five six", s.title)
    }

    @Test
    fun cleanerDropsTagsAndLoops() {
        val raw = " [BLANK_AUDIO] Okay so ayun. (music) Okay so ayun. Okay so ayun. Tara na, kain tayo. [Music]"
        assertEquals("Okay so ayun. Tara na, kain tayo.", TranscriptCleaner.clean(raw))
        assertEquals("", TranscriptCleaner.clean("[BLANK_AUDIO] (Music)  "))
    }

    @Test
    fun chatFormatDisablesThinking() {
        val p = QwenChatFormat.build("SYS", "USER")
        assertTrue(p.startsWith("<|im_start|>system\nSYS<|im_end|>\n<|im_start|>user\nUSER<|im_end|>\n"))
        assertTrue(p.endsWith("<|im_start|>assistant\n<think>\n\n</think>\n\n"))
    }

    @Test
    fun longTranscriptsKeepTheMostRecentPart() {
        val text = "old ".repeat(3000) + "NEWEST"
        val prompt = SummarizerPrompt.user(SummaryRequest(text, false, 0, 300_000))
        assertTrue(prompt.contains("NEWEST"))
        assertTrue(prompt.contains("5:00"))
        assertFalse(prompt.length > SummarizerPrompt.MAX_TRANSCRIPT_CHARS + 200)
    }
}
