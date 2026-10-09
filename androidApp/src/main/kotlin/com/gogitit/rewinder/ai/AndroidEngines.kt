package com.gogitit.rewinder.ai

import com.gogitit.rewinder.llama.Llama
import com.gogitit.rewinder.whisper.Whisper

/** whisper.cpp through the :whisper JNI module. The shared OnDeviceTranscriber drives it. */
object AndroidSpeechEngine : SpeechEngine {
    override fun transcribe(
        modelPath: String,
        pcm: ShortArray,
        language: String,
        prompt: String,
        vadModelPath: String?,
        threads: Int,
    ): String = Whisper.transcribe(modelPath, pcm, language, prompt, vadModelPath, threads)
}

/** llama.cpp through the :llama JNI module. The shared OnDeviceSummarizer drives it. */
object AndroidTextEngine : TextEngine {
    override fun complete(
        modelPath: String,
        prompt: String,
        nCtx: Int,
        threads: Int,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        presencePenalty: Float,
    ): String = Llama.complete(modelPath, prompt, nCtx, threads, maxTokens, temperature, topP, topK, presencePenalty)
}
