package com.gogitit.rewinder.ai

import com.gogitit.rewinder.models.ModelCatalog
import com.gogitit.rewinder.models.ModelEntry
import com.gogitit.rewinder.models.ModelFile
import com.gogitit.rewinder.models.ModelStore
import com.gogitit.rewinder.models.SummaryModelIds
import com.gogitit.rewinder.settings.AppSettings
import com.gogitit.rewinder.settings.TranscriptionLanguages

/** One row in the "Select model" picker. */
data class SummaryModelOption(
    val id: String,
    val label: String,
    val detail: String,
    /** False when it cannot be chosen (Apple Intelligence off or unsupported). */
    val enabled: Boolean,
    /** A model file still has to be downloaded before it works. */
    val needsDownload: Boolean,
)

data class LanguageOption(val id: String, val label: String, val detail: String)

/** What Settings and the model picker read and change. Shared by both apps. */
class AiControls(
    val settings: AppSettings,
    private val models: ModelStore,
    private val system: SystemModelEngine?,
    private val totalRamBytes: Long,
) {
    fun summaryOptions(): List<SummaryModelOption> {
        val options = mutableListOf<SummaryModelOption>()
        system?.let { apple ->
            val reason = apple.unavailableReason()
            options += SummaryModelOption(
                id = SummaryModelIds.APPLE,
                label = apple.label,
                detail = reason ?: "Built in · Apple Intelligence · best for English",
                enabled = reason == null,
                needsDownload = false,
            )
        }
        options += qwenOption(ModelCatalog.qwen2b, "Best quality · Filipino and English")
        options += qwenOption(ModelCatalog.qwen08b, "Faster and lighter")
        return options
    }

    private fun qwenOption(file: ModelFile, note: String): SummaryModelOption {
        val installed = models.pathOf(file) != null
        val lowRam = file == ModelCatalog.qwen2b && ModelCatalog.summarizerFor(totalRamBytes) != file
        val detail = buildString {
            append(if (installed) "On device · " else "Download · ")
            append(gigabytes(file.approxBytes))
            append(" · ")
            append(if (lowRam) "may be slow on this phone" else note)
        }
        return SummaryModelOption(file.id, file.label, detail, enabled = true, needsDownload = !installed)
    }

    fun selectSummaryModel(id: String) {
        settings.setSummaryModel(id)
        models.refresh()
    }

    val languageOptions: List<LanguageOption> = listOf(
        LanguageOption(TranscriptionLanguages.FILIPINO, "Filipino / Taglish", "Best for Tagalog and mixed speech"),
        LanguageOption(TranscriptionLanguages.ENGLISH, "English", "Best when everyone speaks English"),
        LanguageOption(TranscriptionLanguages.AUTO, "Auto-detect", "Whisper guesses; may turn Tagalog into English"),
    )

    fun selectLanguage(id: String) = settings.setLanguage(id)

    fun modelEntries(): List<ModelEntry> = models.entries()

    fun removeModel(file: ModelFile) = models.remove(file)

    companion object {
        fun gigabytes(bytes: Long): String {
            val mb = bytes / 1_000_000L
            return if (mb >= 1000) "${mb / 1000}.${(mb % 1000) / 100} GB" else "$mb MB"
        }
    }
}
