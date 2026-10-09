package com.gogitit.rewinder.models

import kotlinx.coroutines.flow.StateFlow

/** Download / readiness state of the on-device AI models. */
data class ModelsState(
    val ready: Boolean = false,
    val downloading: Boolean = false,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    /** Which file is downloading now, for example "Qwen3.5 2B". */
    val current: String? = null,
    val error: String? = null,
) {
    val progress: Float
        get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

/**
 * Platform model store. Android downloads from Hugging Face once, then everything runs offline.
 * Null in RewinderGraph means the platform has no real models yet (iOS for now).
 */
interface ModelProvider {
    val state: StateFlow<ModelsState>

    /** Short label, for example "Whisper turbo + Qwen3.5 2B, about 1.8 GB". */
    val description: String

    fun download()
    fun cancel()
}
