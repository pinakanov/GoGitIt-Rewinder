package com.gogitit.rewinder.ai

import com.gogitit.rewinder.models.ModelCatalog
import com.gogitit.rewinder.models.ModelFiles
import com.gogitit.rewinder.models.SummaryModelIds
import com.gogitit.rewinder.pipeline.PlaceholderSummarizer
import com.gogitit.rewinder.pipeline.QwenChatFormat
import com.gogitit.rewinder.pipeline.Summaries
import com.gogitit.rewinder.pipeline.Summarizer
import com.gogitit.rewinder.pipeline.SummarizerPrompt
import com.gogitit.rewinder.pipeline.Summary
import com.gogitit.rewinder.pipeline.SummaryParser
import com.gogitit.rewinder.pipeline.SummaryRequest
import com.gogitit.rewinder.pipeline.Transcriber
import com.gogitit.rewinder.pipeline.Transcript
import com.gogitit.rewinder.pipeline.TranscriptCleaner
import com.gogitit.rewinder.pipeline.TranscriptionHints
import com.gogitit.rewinder.settings.TranscriptionLanguages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * whisper.cpp, one call per rewind. Android: JNI (whisper module). iOS: whisper.xcframework.
 * Implementations load the model, transcribe, free it, and throw on failure.
 */
interface SpeechEngine {
    fun transcribe(
        modelPath: String,
        pcm: ShortArray,
        language: String,
        prompt: String,
        vadModelPath: String?,
        threads: Int,
    ): String
}

/** llama.cpp, one completion per rewind. Android: JNI (llama module). iOS: llama.xcframework. */
interface TextEngine {
    fun complete(
        modelPath: String,
        prompt: String,
        nCtx: Int,
        threads: Int,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        presencePenalty: Float,
    ): String
}

/**
 * A built-in system model, today Apple Foundation Models (Apple Intelligence, iOS 26+).
 * Null on platforms without one.
 */
interface SystemModelEngine {
    /** Shown in the model picker, for example "Apple Foundation". */
    val label: String

    /** Null when usable, otherwise why not (for example "Turn on Apple Intelligence"). */
    fun unavailableReason(): String?

    fun complete(instructions: String, prompt: String, temperature: Float): String
}

/** Use the big cores; more threads than that just fight each other on phones. */
fun inferenceThreads(cpuCores: Int): Int = (cpuCores / 2).coerceIn(2, 4)

/**
 * Whisper large-v3-turbo with Silero VAD. Language comes from Settings: Filipino (with a
 * Taglish prompt), English, or auto-detect.
 * Loaded per rewind and freed right after, so it never sits in memory next to Qwen.
 */
class OnDeviceTranscriber(
    private val engine: SpeechEngine,
    private val files: ModelFiles,
    private val threads: Int,
    private val language: () -> String = { TranscriptionLanguages.FILIPINO },
) : Transcriber {
    override suspend fun transcribe(pcm: ShortArray, sampleRate: Int): Transcript =
        withContext(Dispatchers.Default) {
            val model = files.pathOf(ModelCatalog.whisper)
                ?: return@withContext Transcript(text = "", looksLikeConversation = false)
            val lang = language()
            val raw = engine.transcribe(
                modelPath = model,
                pcm = pcm,
                language = lang,
                prompt = if (lang == TranscriptionLanguages.FILIPINO) TranscriptionHints.TAGLISH_PROMPT else "",
                vadModelPath = files.pathOf(ModelCatalog.vad),
                threads = threads,
            )
            Transcript(text = TranscriptCleaner.clean(raw), looksLikeConversation = false)
        }
}

/**
 * Summarizes with the model picked in Settings: Apple Foundation (when available) or an
 * Unsloth Qwen3.5 GGUF. If Apple's model refuses or fails (for example a language it does not
 * support), an installed Qwen model takes over so the rewind is not lost.
 */
class OnDeviceSummarizer(
    private val engine: TextEngine,
    private val files: ModelFiles,
    private val threads: Int,
    private val system: SystemModelEngine? = null,
    private val selected: () -> String = { files.summarizerModel?.id ?: SummaryModelIds.QWEN_2B },
    private val fallback: Summarizer = PlaceholderSummarizer(),
) : Summarizer {
    override suspend fun summarize(request: SummaryRequest): Summary = withContext(Dispatchers.Default) {
        // Without Whisper there is no transcript to summarize: show the setup placeholder.
        if (files.pathOf(ModelCatalog.whisper) == null) return@withContext fallback.summarize(request)
        if (request.filteredTranscript.isBlank()) return@withContext Summaries.nothingHeard(request.redactedCount)

        if (selected() == SummaryModelIds.APPLE && system != null) {
            val reason = system.unavailableReason()
            val problem = if (reason == null) {
                try {
                    val raw = system.complete(
                        instructions = SummarizerPrompt.SYSTEM,
                        prompt = SummarizerPrompt.user(request, SummarizerPrompt.APPLE_MAX_TRANSCRIPT_CHARS),
                        temperature = 0.5f,
                    )
                    return@withContext SummaryParser.parse(raw, request.redactedCount)
                } catch (e: Exception) {
                    e.message ?: "unknown error"
                }
            } else {
                reason
            }
            val backup = ModelCatalog.qwenModels.firstNotNullOfOrNull { files.pathOf(it) }
                ?: error("${system.label} could not summarize this ($problem). Download a Qwen model in Settings as a backup.")
            return@withContext runQwen(backup, request)
        }

        val qwen = files.summarizerModel ?: return@withContext fallback.summarize(request)
        val path = files.pathOf(qwen) ?: return@withContext fallback.summarize(request)
        runQwen(path, request)
    }

    private fun runQwen(modelPath: String, request: SummaryRequest): Summary {
        val prompt = QwenChatFormat.build(SummarizerPrompt.SYSTEM, SummarizerPrompt.user(request))
        val raw = engine.complete(
            modelPath = modelPath,
            prompt = prompt,
            nCtx = 4096,
            threads = threads,
            maxTokens = 320,
            // Qwen3.5 non-thinking settings, a little cooler for factual recaps.
            temperature = 0.7f,
            topP = 0.8f,
            topK = 20,
            presencePenalty = 1.5f,
        )
        return SummaryParser.parse(raw, request.redactedCount)
    }
}
