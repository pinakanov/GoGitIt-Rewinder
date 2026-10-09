package com.gogitit.rewinder.whisper

/**
 * Thin JNI surface over whisper.cpp. Strings cross as UTF-8 bytes so Filipino text
 * (and anything outside the BMP) survives intact.
 */
object WhisperNative {
    init {
        System.loadLibrary("rewinder_whisper")
    }

    /** Returns 0 when the model cannot be loaded. */
    external fun load(pathUtf8: ByteArray): Long

    /** Returns null on failure. */
    external fun transcribe(
        handle: Long,
        pcm: ShortArray,
        languageUtf8: ByteArray,
        promptUtf8: ByteArray,
        vadModelPathUtf8: ByteArray,
        threads: Int,
    ): ByteArray?

    external fun free(handle: Long)
}

/** Loads, transcribes and frees in one call, so the model is only in memory while needed. */
object Whisper {
    fun transcribe(
        modelPath: String,
        pcm: ShortArray,
        language: String,
        prompt: String,
        vadModelPath: String?,
        threads: Int,
    ): String {
        val handle = WhisperNative.load(modelPath.encodeToByteArray())
        check(handle != 0L) { "Could not load the speech model." }
        try {
            val bytes = WhisperNative.transcribe(
                handle,
                pcm,
                language.encodeToByteArray(),
                prompt.encodeToByteArray(),
                (vadModelPath ?: "").encodeToByteArray(),
                threads,
            ) ?: error("Transcription failed.")
            return bytes.decodeToString()
        } finally {
            WhisperNative.free(handle)
        }
    }
}
