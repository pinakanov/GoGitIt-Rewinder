package com.gogitit.rewinder

import com.gogitit.rewinder.ai.OnDeviceSummarizer
import com.gogitit.rewinder.ai.OnDeviceTranscriber
import com.gogitit.rewinder.ai.SpeechEngine
import com.gogitit.rewinder.ai.TextEngine
import com.gogitit.rewinder.ai.inferenceThreads
import com.gogitit.rewinder.audio.AudioSource
import com.gogitit.rewinder.audio.SessionHooks
import com.gogitit.rewinder.data.InMemoryRecentsRepository
import com.gogitit.rewinder.data.RecentsRepository
import com.gogitit.rewinder.models.ModelDownloader
import com.gogitit.rewinder.models.ModelProvider
import com.gogitit.rewinder.models.ModelStorage
import com.gogitit.rewinder.models.ModelStore
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

/**
 * The one wiring both platforms use: same models, same pipeline, same UI.
 * A platform supplies only its microphone, "in progress" surface, AI engines and file/HTTP access.
 */
fun onDeviceGraph(
    audioSource: AudioSource,
    hooks: SessionHooks,
    speech: SpeechEngine,
    text: TextEngine,
    storage: ModelStorage,
    downloader: ModelDownloader,
    totalRamBytes: Long,
    cpuCores: Int,
): RewinderGraph {
    val models = ModelStore(storage, downloader, totalRamBytes)
    val threads = inferenceThreads(cpuCores)
    return RewinderGraph(
        audioSource = audioSource,
        hooks = hooks,
        transcriber = OnDeviceTranscriber(speech, models, threads),
        summarizer = OnDeviceSummarizer(text, models, threads),
        models = models,
    )
}
