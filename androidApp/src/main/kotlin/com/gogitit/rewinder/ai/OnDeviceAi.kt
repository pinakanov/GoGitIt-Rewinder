package com.gogitit.rewinder.ai

import com.gogitit.rewinder.llama.Llama
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
import com.gogitit.rewinder.whisper.Whisper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Use the big cores; more threads than that just fight each other on phones. */
private val inferenceThreads: Int = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 4)

/**
 * Whisper large-v3-turbo, forced to Tagalog with a Taglish prompt, Silero VAD on.
 * Loaded per rewind and freed right after, so it never sits in memory next to Qwen.
 */
class WhisperTranscriber(private val models: HuggingFaceModelManager) : Transcriber {
    override suspend fun transcribe(pcm: ShortArray, sampleRate: Int): Transcript =
        withContext(Dispatchers.Default) {
            val model = models.pathOf(ModelCatalog.whisper)
                ?: return@withContext Transcript(text = "", looksLikeConversation = false)
            val raw = Whisper.transcribe(
                modelPath = model,
                pcm = pcm,
                language = TranscriptionHints.LANGUAGE,
                prompt = TranscriptionHints.TAGLISH_PROMPT,
                vadModelPath = models.pathOf(ModelCatalog.vad),
                threads = inferenceThreads,
            )
            Transcript(text = TranscriptCleaner.clean(raw), looksLikeConversation = false)
        }
}

/** Unsloth Qwen3.5 GGUF through llama.cpp, non-thinking mode, short structured answer. */
class QwenSummarizer(
    private val models: HuggingFaceModelManager,
    private val fallback: Summarizer = PlaceholderSummarizer(),
) : Summarizer {
    override suspend fun summarize(request: SummaryRequest): Summary = withContext(Dispatchers.Default) {
        val model = models.pathOf(models.summarizerModel)
        if (!models.isReady || model == null) return@withContext fallback.summarize(request)
        if (request.filteredTranscript.isBlank()) return@withContext Summaries.nothingHeard(request.redactedCount)

        val prompt = QwenChatFormat.build(SummarizerPrompt.SYSTEM, SummarizerPrompt.user(request))
        val raw = Llama.complete(
            modelPath = model,
            prompt = prompt,
            nCtx = 4096,
            threads = inferenceThreads,
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
