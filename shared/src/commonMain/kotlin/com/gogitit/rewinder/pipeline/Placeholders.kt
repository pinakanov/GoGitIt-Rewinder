package com.gogitit.rewinder.pipeline

import com.gogitit.rewinder.core.formatDuration

/**
 * Stand-ins until the on-device models are wired in (see docs/ARCHITECTURE.md).
 * They let the full flow run end to end on both platforms today.
 */
class PlaceholderTranscriber : Transcriber {
    override suspend fun transcribe(pcm: ShortArray, sampleRate: Int): Transcript =
        Transcript(text = "", looksLikeConversation = false)
}

class PlaceholderSummarizer : Summarizer {
    override suspend fun summarize(request: SummaryRequest): Summary = Summary(
        title = "Untitled rewind",
        gist = "Captured ${formatDuration(request.durationMillis)} of audio. The on-device " +
            "transcription and summary models are not connected yet, so this is a placeholder.",
        points = listOf(
            "The audio was held in memory only and has already been wiped.",
            "Once a model is connected, this card shows a short recap of what was said.",
        ),
        redactedCount = request.redactedCount,
    )
}
