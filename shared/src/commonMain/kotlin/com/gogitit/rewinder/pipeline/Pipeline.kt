package com.gogitit.rewinder.pipeline

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

/**
 * transcribe -> filter -> summarize. The PCM array is zero-filled as soon as the
 * transcriber is done with it, success or not.
 */
class RewindPipeline(
    private val transcriber: Transcriber,
    private val filter: SensitiveFilter,
    private val summarizer: Summarizer,
) {
    suspend fun run(pcm: ShortArray, sampleRate: Int, durationMillis: Long): Summary {
        val transcript = try {
            transcriber.transcribe(pcm, sampleRate)
        } finally {
            pcm.fill(0)
        }
        val filtered = filter.filter(transcript.text)
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
    val SYSTEM: String = """
        You help a person with ADHD catch up on the last few minutes of what they heard.
        Write a short recap of the transcript.
        Rules:
        - Paraphrase. Never quote sentences or reproduce the original wording.
        - Do not say who said what. Describe the conversation as a whole.
        - Never restate, guess or describe anything marked [sensitive detail]. You may say that a sensitive detail came up.
        - Output a title of at most 5 words, one line saying what this was about, then 2 to 5 short bullet points: key points, decisions, and anything asked of the listener.
        - If the transcript is empty, silent or unclear, say so plainly in one line.
    """.trimIndent()
}
