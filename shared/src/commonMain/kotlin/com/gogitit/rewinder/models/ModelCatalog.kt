package com.gogitit.rewinder.models

/** One downloadable model file. */
data class ModelFile(
    val id: String,
    val label: String,
    val url: String,
    val fileName: String,
    val approxBytes: Long,
)

/**
 * Model choices, all free and from Hugging Face. Shared by Android and iOS, and mirrored in
 * scripts/models.sh.
 *
 * - Speech: Whisper large-v3-turbo (q5_0). The smallest Whisper that is usable for Tagalog
 *   and Taglish; small/base are much weaker on Filipino.
 * - Voice activity: Silero VAD, so silence is skipped instead of hallucinated.
 * - Summary: Unsloth's Qwen3.5 GGUF, Q4_0 (fastest 4-bit on ARM CPUs, repacked by llama.cpp).
 *   2B on devices with about 4 GB RAM or more, 0.8B below that.
 */
object ModelCatalog {
    val whisper = ModelFile(
        id = "whisper",
        label = "Whisper large-v3-turbo",
        url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin",
        fileName = "ggml-large-v3-turbo-q5_0.bin",
        approxBytes = 574_000_000L,
    )

    val vad = ModelFile(
        id = "vad",
        label = "Silero VAD",
        url = "https://huggingface.co/ggml-org/whisper-vad/resolve/main/ggml-silero-v6.2.0.bin",
        fileName = "ggml-silero-v6.2.0.bin",
        approxBytes = 885_000L,
    )

    val qwen2b = ModelFile(
        id = "qwen2b",
        label = "Qwen3.5 2B",
        url = "https://huggingface.co/unsloth/Qwen3.5-2B-GGUF/resolve/main/Qwen3.5-2B-Q4_0.gguf",
        fileName = "Qwen3.5-2B-Q4_0.gguf",
        approxBytes = 1_210_000_000L,
    )

    val qwen08b = ModelFile(
        id = "qwen08b",
        label = "Qwen3.5 0.8B",
        url = "https://huggingface.co/unsloth/Qwen3.5-0.8B-GGUF/resolve/main/Qwen3.5-0.8B-Q4_0.gguf",
        fileName = "Qwen3.5-0.8B-Q4_0.gguf",
        approxBytes = 507_000_000L,
    )

    val all: List<ModelFile> = listOf(whisper, vad, qwen2b, qwen08b)
    val qwenModels: List<ModelFile> = listOf(qwen2b, qwen08b)

    /** A "4 GB" device reports roughly 3.5 to 3.8 GB of total memory. */
    fun summarizerFor(totalRamBytes: Long): ModelFile =
        if (totalRamBytes >= 3_400_000_000L) qwen2b else qwen08b

    fun qwenById(id: String): ModelFile? = qwenModels.firstOrNull { it.id == id }
}

/** Ids for the summary model setting. Qwen ids match [ModelFile.id]. */
object SummaryModelIds {
    const val APPLE = "apple"
    const val QWEN_2B = "qwen2b"
    const val QWEN_08B = "qwen08b"
}
