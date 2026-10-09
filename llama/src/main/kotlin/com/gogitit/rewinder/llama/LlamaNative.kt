package com.gogitit.rewinder.llama

/** Thin JNI surface over llama.cpp. Text crosses as UTF-8 bytes. */
object LlamaNative {
    init {
        System.loadLibrary("rewinder_llama")
    }

    /** Returns 0 when the model cannot be loaded. */
    external fun load(pathUtf8: ByteArray, nCtx: Int, threads: Int): Long

    /** Returns null on failure (including a prompt that does not fit the context). */
    external fun generate(
        handle: Long,
        promptUtf8: ByteArray,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        presencePenalty: Float,
    ): ByteArray?

    external fun free(handle: Long)
}

/** Loads, generates and frees in one call, so the model is only in memory while needed. */
object Llama {
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
    ): String {
        val handle = LlamaNative.load(modelPath.encodeToByteArray(), nCtx, threads)
        check(handle != 0L) { "Could not load the summary model." }
        try {
            val bytes = LlamaNative.generate(
                handle, prompt.encodeToByteArray(), maxTokens, temperature, topP, topK, presencePenalty,
            ) ?: error("The summary model could not process this rewind.")
            return bytes.decodeToString()
        } finally {
            LlamaNative.free(handle)
        }
    }
}
