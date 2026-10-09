package com.gogitit.rewinder.pipeline

import com.gogitit.rewinder.settings.SummaryLanguages

/** Settings shared by every Whisper-based transcriber. */
object TranscriptionHints {
    /** Whisper code for Tagalog/Filipino. English words inside Taglish still come out in English. */
    const val LANGUAGE: String = "tl"

    /**
     * Whisper's initial prompt nudges spelling and style. A Taglish sample keeps it from
     * translating Tagalog into English, a known failure when the language is left on auto.
     */
    const val TAGLISH_PROMPT: String =
        "Usapan ito sa Taglish, halo ang Tagalog at English. Okay, so ayun nga, mag-meet tayo bukas ng hapon."
}

/** Qwen chat template (ChatML), with thinking turned off for a fast, direct answer. */
object QwenChatFormat {
    fun build(system: String, user: String): String =
        "<|im_start|>system\n$system<|im_end|>\n" +
            "<|im_start|>user\n$user<|im_end|>\n" +
            "<|im_start|>assistant\n<think>\n\n</think>\n\n"
}

/** Removes Whisper's non-speech annotations and hallucinated repeats. */
object TranscriptCleaner {
    private val bracketTags = Regex("""\[[^\]]{1,40}]""")
    private val soundTags = Regex(
        """\((?:music|musika|applause|palakpakan|laughs?|laughter|tawa|silence|inaudible|static|noise|ingay)\)""",
        RegexOption.IGNORE_CASE,
    )
    private val spaces = Regex("""\s+""")
    private val sentenceEnd = Regex("""(?<=[.!?])\s+""")

    fun clean(raw: String): String {
        val stripped = raw
            .replace(bracketTags, " ")
            .replace(soundTags, " ")
            .replace(spaces, " ")
            .trim()
        if (stripped.isEmpty()) return ""
        // Whisper sometimes loops on one sentence. Keep a sentence only if it differs from the last.
        val out = ArrayList<String>()
        for (sentence in stripped.split(sentenceEnd)) {
            val s = sentence.trim()
            if (s.isEmpty()) continue
            if (out.isNotEmpty() && out.last().equals(s, ignoreCase = true)) continue
            out.add(s)
        }
        return out.joinToString(" ")
    }
}

/** Turns the model's TITLE / GIST / bullets answer into a Summary, tolerating sloppy output. */
object SummaryParser {
    private val thinkBlock = Regex("""<think>.*?</think>""", RegexOption.DOT_MATCHES_ALL)
    private val bullet = Regex("""^(?:[-*•]|\d+[.)])\s+(.+)$""")
    private val label = Regex("""^\**\s*(TITLE|GIST)\s*\**\s*:\s*\**\s*(.*)$""", RegexOption.IGNORE_CASE)

    fun parse(raw: String, redactedCount: Int): Summary {
        val text = raw.replace(thinkBlock, "").replace("<think>", "").replace("</think>", "").trim()
        var title: String? = null
        var gist: String? = null
        val points = ArrayList<String>()
        val loose = ArrayList<String>()

        for (line in text.lines().map { it.trim() }.filter { it.isNotEmpty() }) {
            val labelled = label.find(line)
            if (labelled != null) {
                val value = tidy(labelled.groupValues[2])
                if (value.isNotEmpty()) {
                    if (labelled.groupValues[1].equals("TITLE", ignoreCase = true)) title = value else gist = value
                }
                continue
            }
            val b = bullet.find(line)
            if (b != null) {
                tidy(b.groupValues[1]).takeIf { it.isNotEmpty() }?.let(points::add)
            } else {
                loose.add(tidy(line))
            }
        }

        val finalGist = gist ?: loose.firstOrNull() ?: points.firstOrNull()
            ?: "No clear summary could be made from this stretch."
        val finalTitle = (title ?: "Rewind")
            .split(' ').filter { it.isNotBlank() }.take(6).joinToString(" ")
            .trimEnd('.', ',', ';', ':')
        return Summary(
            title = finalTitle,
            gist = finalGist,
            points = points.filterNot { it == finalGist }.take(5),
            redactedCount = redactedCount,
        )
    }

    private fun tidy(s: String): String = s.replace("**", "").trim().trim('"', '“', '”').trim()
}

/** Canned results that never need a model. */
object Summaries {
    fun nothingHeard(redactedCount: Int, outputLanguage: String = SummaryLanguages.AUTO): Summary =
        if (outputLanguage == SummaryLanguages.FILIPINO) {
            Summary(
                title = "Walang malinaw na narinig",
                gist = "Walang malinaw na pananalita na nakuha ang Rewinder sa bahaging iyon.",
                points = listOf("Maaaring tahimik, malayo ang nagsasalita, o puro ingay sa paligid."),
                redactedCount = redactedCount,
            )
        } else {
            Summary(
                title = "Nothing clear heard",
                gist = "Rewinder could not make out any speech in that stretch.",
                points = listOf("It may have been quiet, too far away, or mostly background noise."),
                redactedCount = redactedCount,
            )
        }
}
