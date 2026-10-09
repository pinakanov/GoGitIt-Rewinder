package com.gogitit.rewinder.ai

import com.gogitit.rewinder.models.ModelCatalog
import com.gogitit.rewinder.models.ModelFiles
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

/** Use the big cores; more threads than that just fight each other on phones. */
fun inferenceThreads(cpuCores: Int): Int = (cpuCores / 2).coerceIn(2, 4)

/**
 * Whisper large-v3-turbo, forced to Tagalog with a Taglish prompt, Silero VAD on.
 * Loaded per rewind and freed right after, so it never sits in memory next to Qwen.
 */
class OnDeviceTranscriber(
    private val engine: SpeechEngine,
    private val files: ModelFiles,
    private val threads: Int,
) : Transcriber {
    override suspend fun transcribe(pcm: ShortArray, sampleRate: Int): Transcript =
        withContext(Dispatchers.Default) {
            val model = files.pathOf(ModelCatalog.whisper)
                ?: return@withContext Transcript(text = "", looksLikeConversation = false)
            val raw = engine.transcribe(
                modelPath = model,
                pcm = pcm,
                language = TranscriptionHints.LANGUAGE,
                prompt = TranscriptionHints.TAGLISH_PROMPT,
                vadModelPath = files.pathOf(ModelCatalog.vad),
                threads = threads,
            )
            Transcript(text = TranscriptCleaner.clean(raw), looksLikeConversation = false)
        }
}

/** Unsloth Qwen3.5 GGUF, non-thinking mode, short structured answer. */
class OnDeviceSummarizer(
    private val engine: TextEngine,
    private val files: ModelFiles,
    private val threads: Int,
    private val fallback: Summarizer = PlaceholderSummarizer(),
) : Summarizer {
    override suspend fun summarize(request: SummaryRequest): Summary = withContext(Dispatchers.Default) {
        val model = files.pathOf(files.summarizerModel)
        if (!files.isReady || model == null) return@withContext fallback.summarize(request)
        if (request.filteredTranscript.isBlank()) return@withContext Summaries.nothingHeard(request.redactedCount)

        val prompt = QwenChatFormat.build(SummarizerPrompt.SYSTEM, SummarizerPrompt.user(request))
        val raw = engine.complete(
            modelPath = model,
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
        SummaryParser.parse(raw, request.redactedCount)
    }
}
