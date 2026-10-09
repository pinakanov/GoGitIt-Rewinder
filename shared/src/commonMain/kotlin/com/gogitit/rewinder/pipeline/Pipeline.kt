package com.gogitit.rewinder.pipeline

import com.gogitit.rewinder.core.AudioGain
import com.gogitit.rewinder.core.formatDuration

/** Speech to text, on device. Implementations must not persist audio or text. */
interface Transcriber {
    suspend fun transcribe(pcm: ShortArray, sampleRate: Int): Transcript
}

data class Transcript(
    val text: String,
    /** True when more than one voice or turn-taking was detected. No speaker labels. */
    val looksLikeConversation: Boolean,
)

/** Local LLM. Input is the filtered transcript, output is the only thing a user ever sees. */
interface Summarizer {
    suspend fun summarize(request: SummaryRequest): Summary
}

data class SummaryRequest(
    val filteredTranscript: String,
    val looksLikeConversation: Boolean,
    val redactedCount: Int,
    val durationMillis: Long,
)

data class Summary(
    val title: String,
    val gist: String,
    val points: List<String>,
    val redactedCount: Int,
)

/** What the pipeline is doing right now, for the UI. */
enum class RewindStage { Transcribing, Summarizing }

/**
 * transcribe -> filter -> summarize. The PCM array is zero-filled as soon as the
 * transcriber is done with it, success or not.
 */
class RewindPipeline(
    private val transcriber: Transcriber,
    private val filter: SensitiveFilter,
    private val summarizer: Summarizer,
) {
    suspend fun run(
        pcm: ShortArray,
        sampleRate: Int,
        durationMillis: Long,
        onStage: (RewindStage) -> Unit = {},
    ): Summary {
        onStage(RewindStage.Transcribing)
        AudioGain.normalizeInPlace(pcm)
        val transcript = try {
            transcriber.transcribe(pcm, sampleRate)
        } finally {
            pcm.fill(0)
        }
        val filtered = filter.filter(transcript.text)
        onStage(RewindStage.Summarizing)
        return summarizer.summarize(
            SummaryRequest(
                filteredTranscript = filtered.text,
                looksLikeConversation = transcript.looksLikeConversation,
                redactedCount = filtered.redactedCount,
                durationMillis = durationMillis,
            )
        )
    }
}

/**
 * System prompt for the on-device summarizer, kept here so Android and iOS ship
 * the same behavior.
 */
object SummarizerPrompt {
    /** Keeps prompt + answer inside a 4096 token context, even for dense Filipino speech. */
    const val MAX_TRANSCRIPT_CHARS: Int = 8_000

    val SYSTEM: String = """
        You help a person with ADHD catch up on what was said around them in the last few minutes.
        You get a rough automatic transcript. It may be in Filipino, English or Taglish, it may contain recognition mistakes, and it never says who is speaking.
        Rules:
        - Paraphrase. Never quote sentences or copy the original wording.
        - Do not say who said what. Describe the conversation as a whole.
        - Never repeat, guess or describe anything written as [sensitive detail]. You may say that a sensitive detail came up.
        - Write in the language the speakers mostly used: English, Filipino or Taglish.
        - Be short and concrete: topics, decisions, and anything the listener was asked to do.
        Answer in exactly this format and nothing else:
        TITLE: <at most 5 words>
        GIST: <one sentence saying what this was about>
        - <key point>
        - <key point>
        Use 2 to 5 key points.
    """.trimIndent()

    /** Apple's on-device model has a 4096 token window for input and output together. */
    const val APPLE_MAX_TRANSCRIPT_CHARS: Int = 5_000

    fun user(request: SummaryRequest, maxChars: Int = MAX_TRANSCRIPT_CHARS): String {
        val text = request.filteredTranscript.trim()
        // If it is too long, keep the most recent part: that is what the listener just missed.
        val clipped = if (text.length > maxChars) "... " + text.takeLast(maxChars) else text
        return "Transcript of the last ${formatDuration(request.durationMillis)}:\n\"\"\"\n$clipped\n\"\"\"\nWrite the recap."
    }
}
