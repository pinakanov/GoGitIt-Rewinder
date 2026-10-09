// JNI bridge for whisper.cpp. Audio arrives as 16 kHz mono PCM16 from the in-memory
// ring buffer, is converted to float here, and the float copy is zeroed before returning.

#include <jni.h>
#include <android/log.h>
#include <algorithm>
#include <string>
#include <vector>

#include "whisper.h"

#define TAG "RewinderWhisper"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

std::string to_string(JNIEnv *env, jbyteArray utf8) {
    if (utf8 == nullptr) return {};
    const jsize n = env->GetArrayLength(utf8);
    std::string out(static_cast<size_t>(n), '\0');
    env->GetByteArrayRegion(utf8, 0, n, reinterpret_cast<jbyte *>(out.data()));
    return out;
}

jbyteArray to_bytes(JNIEnv *env, const std::string &s) {
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(s.size()));
    env->SetByteArrayRegion(arr, 0, static_cast<jsize>(s.size()), reinterpret_cast<const jbyte *>(s.data()));
    return arr;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_gogitit_rewinder_whisper_WhisperNative_load(JNIEnv *env, jobject, jbyteArray jpath) {
    const std::string path = to_string(env, jpath);
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    cparams.flash_attn = true; // faster encoder, less memory; same transcript
    whisper_context *ctx = whisper_init_from_file_with_params(path.c_str(), cparams);
    if (ctx == nullptr) LOGE("failed to load model %s", path.c_str());
    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_gogitit_rewinder_whisper_WhisperNative_transcribe(
        JNIEnv *env, jobject, jlong handle, jshortArray jpcm, jbyteArray jlanguage,
        jbyteArray jprompt, jbyteArray jvadPath, jint threads) {
    auto *ctx = reinterpret_cast<whisper_context *>(handle);
    if (ctx == nullptr) return nullptr;

    const jsize n = env->GetArrayLength(jpcm);
    std::vector<float> pcm(static_cast<size_t>(n));
    {
        jshort *src = env->GetShortArrayElements(jpcm, nullptr);
        for (jsize i = 0; i < n; ++i) pcm[i] = static_cast<float>(src[i]) / 32768.0f;
        env->ReleaseShortArrayElements(jpcm, src, JNI_ABORT);
    }

    const std::string language = to_string(env, jlanguage);
    const std::string prompt = to_string(env, jprompt);
    const std::string vad_path = to_string(env, jvadPath);

    whisper_full_params p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    p.n_threads = threads;
    p.language = language.empty() ? "auto" : language.c_str();
    p.detect_language = false;
    p.translate = false;          // transcribe, never translate
    p.no_context = true;          // fewer repetition loops on long audio
    p.no_timestamps = true;
    p.single_segment = false;
    p.print_special = false;
    p.print_progress = false;
    p.print_realtime = false;
    p.print_timestamps = false;
    p.suppress_blank = true;
    p.suppress_nst = true;        // drop "(music)", "[BLANK_AUDIO]" style tokens
    p.initial_prompt = prompt.empty() ? nullptr : prompt.c_str();
    if (!vad_path.empty()) {
        // Silero VAD skips silence: faster, and stops Whisper inventing text in quiet stretches.
        p.vad = true;
        p.vad_model_path = vad_path.c_str();
        p.vad_params.threshold = 0.35f; // a little more sensitive than 0.5 for distant voices
    }

    const int rc = whisper_full(ctx, p, pcm.data(), static_cast<int>(pcm.size()));
    std::fill(pcm.begin(), pcm.end(), 0.0f);
    if (rc != 0) {
        LOGE("whisper_full failed: %d", rc);
        return nullptr;
    }

    std::string text;
    const int segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < segments; ++i) {
        const char *seg = whisper_full_get_segment_text(ctx, i);
        if (seg != nullptr) text += seg;
    }
    jbyteArray out = to_bytes(env, text);
    std::fill(text.begin(), text.end(), '\0');
    return out;
}

extern "C" JNIEXPORT void JNICALL
Java_com_gogitit_rewinder_whisper_WhisperNative_free(JNIEnv *, jobject, jlong handle) {
    auto *ctx = reinterpret_cast<whisper_context *>(handle);
    if (ctx != nullptr) whisper_free(ctx);
}
