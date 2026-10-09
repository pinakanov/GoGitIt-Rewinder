package com.gogitit.rewinder

import com.gogitit.rewinder.audio.AudioSource
import com.gogitit.rewinder.audio.SessionHooks
import com.gogitit.rewinder.data.InMemoryRecentsRepository
import com.gogitit.rewinder.data.RecentsRepository
import com.gogitit.rewinder.models.ModelProvider
import com.gogitit.rewinder.pipeline.PlaceholderSummarizer
import com.gogitit.rewinder.pipeline.PlaceholderTranscriber
import com.gogitit.rewinder.pipeline.RegexSensitiveFilter
import com.gogitit.rewinder.pipeline.RewindPipeline
import com.gogitit.rewinder.pipeline.SensitiveFilter
import com.gogitit.rewinder.pipeline.Summarizer
import com.gogitit.rewinder.pipeline.Transcriber

/**
 * Manual dependency wiring. Each platform builds exactly one of these per process
 * and passes in its own microphone and "in progress" surface.
 */
class RewinderGraph(
    audioSource: AudioSource,
    hooks: SessionHooks,
    transcriber: Transcriber = PlaceholderTranscriber(),
    filter: SensitiveFilter = RegexSensitiveFilter(),
    summarizer: Summarizer = PlaceholderSummarizer(),
    val recents: RecentsRepository = InMemoryRecentsRepository(),
    /** On-device AI model store; null where the platform has none yet. */
    val models: ModelProvider? = null,
) {
    val navigator = AppNavigator()

    val controller = RewinderController(
        audioSource = audioSource,
        hooks = hooks,
        pipeline = RewindPipeline(transcriber, filter, summarizer),
        recents = recents,
    )
}
