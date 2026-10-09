package com.gogitit.rewinder.pipeline

/** Runs on the transcript before the summarizer ever sees it. */
interface SensitiveFilter {
    fun filter(text: String): FilterResult
}

data class FilterResult(
    val text: String,
    val redactedCount: Int,
)

/**
 * Best-effort rule based pass. A small on-device classifier model should sit next
 * to this later (names, health details, addresses), but these rules stay as a floor.
 */
class RegexSensitiveFilter : SensitiveFilter {

    private val rules: List<Regex> = listOf(
        // "my password is hunter2", "PIN: 1234", "the OTP is 99812"
        Regex(
            """\b(password|passcode|passphrase|pin|otp|one[- ]time code|security code|cvv)\b\s*(is|was|:)?\s*\S+""",
            RegexOption.IGNORE_CASE,
        ),
        // Email addresses
        Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"""),
        // Payment cards and other long grouped digit runs (12 to 19 digits)
        Regex("""\b(?:\d[ -]?){11,18}\d\b"""),
        // Philippine mobile numbers: 0917 123 4567, +63 917 123 4567
        Regex("""(?:\+?63|\b0)\s?9\d{2}[\s-]?\d{3}[\s-]?\d{4}\b"""),
        // Generic phone numbers: 555-123-4567, (02) 8123 4567
        Regex("""\(?\b\d{2,4}\)?[\s-]\d{3,4}[\s-]\d{4}\b"""),
        // IDs, account numbers, codes: any remaining run of 6+ digits
        Regex("""\b\d{6,}\b"""),
    )

    override fun filter(text: String): FilterResult {
        var count = 0
        var out = text
        for (rule in rules) {
            out = rule.replace(out) {
                count++
                PLACEHOLDER
            }
        }
        return FilterResult(out, count)
    }

    companion object {
        const val PLACEHOLDER = "[sensitive detail]"
    }
}
