package com.gogitit.rewinder

import com.gogitit.rewinder.ai.AiControls
import com.gogitit.rewinder.ai.OnDeviceSummarizer
import com.gogitit.rewinder.ai.OnDeviceTranscriber
import com.gogitit.rewinder.ai.SpeechEngine
import com.gogitit.rewinder.ai.SystemModelEngine
import com.gogitit.rewinder.ai.TextEngine
import com.gogitit.rewinder.ai.inferenceThreads
import com.gogitit.rewinder.audio.AudioSource
import com.gogitit.rewinder.audio.MixedAudioSource
import com.gogitit.rewinder.audio.SessionHooks
import com.gogitit.rewinder.data.InMemoryRecentsRepository
import com.gogitit.rewinder.data.RecentsRepository
import com.gogitit.rewinder.data.StoredRecentsRepository
import com.gogitit.rewinder.models.ModelCatalog
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
import com.gogitit.rewinder.settings.AppSettings
import com.gogitit.rewinder.settings.InputSources
import com.gogitit.rewinder.settings.KeyValueStore

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
    /** Settings and model picker; null in previews. */
    val ai: AiControls? = null,
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
 * The one wiring both platforms use: same models, same settings, same pipeline, same UI.
 * A platform supplies only its microphone, "in progress" surface, AI engines, file/HTTP access
 * and a key-value store (plus Apple's built-in model on iOS).
 */
fun onDeviceGraph(
    audioSource: AudioSource,
    hooks: SessionHooks,
    speech: SpeechEngine,
    text: TextEngine,
    storage: ModelStorage,
    downloader: ModelDownloader,
    settingsStore: KeyValueStore,
    totalRamBytes: Long,
    cpuCores: Int,
    systemModel: SystemModelEngine? = null,
    /** Sound playing on the phone (Android playback capture, iOS screen broadcast), if supported. */
    deviceAudio: AudioSource? = null,
): RewinderGraph {
    // Qwen is the default on both platforms (sized to the phone's RAM). Apple's model stays
    // available in Settings on iOS 26.
    val defaultSummary = ModelCatalog.summarizerFor(totalRamBytes).id
    val settings = AppSettings(settingsStore, defaultSummary)
    val models = ModelStore(storage, downloader) { settings.summaryModel.value }
    val threads = inferenceThreads(cpuCores)
    val input = MixedAudioSource(audioSource, deviceAudio) {
        settings.inputSource.value == InputSources.MIC_AND_DEVICE
    }
    return RewinderGraph(
        audioSource = input,
        hooks = hooks,
        transcriber = OnDeviceTranscriber(speech, models, threads) { settings.language.value },
        summarizer = OnDeviceSummarizer(
            engine = text,
            files = models,
            threads = threads,
            system = systemModel,
            selected = { settings.summaryModel.value },
            outputLanguage = { settings.summaryLanguage.value },
        ),
        recents = StoredRecentsRepository(settingsStore),
        models = models,
        ai = AiControls(settings, models, systemModel, totalRamBytes, supportsDeviceAudio = deviceAudio != null),
    )
}
