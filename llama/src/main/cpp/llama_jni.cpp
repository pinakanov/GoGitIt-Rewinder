// JNI bridge for llama.cpp: load a GGUF model, run one prompt, return the completion.
// Used only for summaries; no chat history is kept between calls.

#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>

#include "llama.h"

#define TAG "RewinderLlama"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

struct Session {
    llama_model *model;
    llama_context *ctx;
    const llama_vocab *vocab;
    int n_ctx;
};

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

bool backend_ready = false;

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_gogitit_rewinder_llama_LlamaNative_load(
        JNIEnv *env, jobject, jbyteArray jpath, jint n_ctx, jint threads) {
    if (!backend_ready) {
        llama_backend_init();
        backend_ready = true;
    }
    const std::string path = to_string(env, jpath);

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;
    llama_model *model = llama_model_load_from_file(path.c_str(), mparams);
    if (model == nullptr) {
        LOGE("failed to load model %s", path.c_str());
        return 0;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = static_cast<uint32_t>(n_ctx);
    cparams.n_batch = static_cast<uint32_t>(n_ctx);
    cparams.n_ubatch = 512;
    cparams.n_threads = threads;
    cparams.n_threads_batch = threads;
    cparams.no_perf = true;
    llama_context *ctx = llama_init_from_model(model, cparams);
    if (ctx == nullptr) {
        LOGE("failed to create context");
        llama_model_free(model);
        return 0;
    }
    auto *s = new Session{model, ctx, llama_model_get_vocab(model), n_ctx};
    return reinterpret_cast<jlong>(s);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_gogitit_rewinder_llama_LlamaNative_generate(
        JNIEnv *env, jobject, jlong handle, jbyteArray jprompt, jint max_tokens,
        jfloat temperature, jfloat top_p, jint top_k, jfloat presence_penalty) {
    auto *s = reinterpret_cast<Session *>(handle);
    if (s == nullptr) return nullptr;

    std::string prompt = to_string(env, jprompt);
    llama_memory_clear(llama_get_memory(s->ctx), true);

    // Tokenize. Special tokens (<|im_start|> etc.) must be parsed, not treated as text.
    const int n_prompt = -llama_tokenize(s->vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
                                         nullptr, 0, true, true);
    std::vector<llama_token> tokens(static_cast<size_t>(n_prompt));
    if (llama_tokenize(s->vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()),
                       tokens.data(), n_prompt, true, true) < 0) {
        LOGE("tokenize failed");
        return nullptr;
    }
    std::fill(prompt.begin(), prompt.end(), '\0');
    if (n_prompt + max_tokens > s->n_ctx) {
        LOGE("prompt too long: %d tokens", n_prompt);
        return nullptr;
    }

    if (llama_decode(s->ctx, llama_batch_get_one(tokens.data(), n_prompt)) != 0) {
        LOGE("prompt decode failed");
        return nullptr;
    }

    llama_sampler *smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_penalties(
            llama_vocab_n_tokens(s->vocab), 64, 1.0f, 0.0f, presence_penalty));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_k(top_k));
    llama_sampler_chain_add(smpl, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    std::string out;
    char piece[256];
    for (int i = 0; i < max_tokens; ++i) {
        llama_token tok = llama_sampler_sample(smpl, s->ctx, -1);
        if (llama_vocab_is_eog(s->vocab, tok)) break;
        const int n = llama_token_to_piece(s->vocab, tok, piece, sizeof(piece), 0, false);
        if (n > 0) out.append(piece, static_cast<size_t>(n));
        if (llama_decode(s->ctx, llama_batch_get_one(&tok, 1)) != 0) {
            LOGE("decode failed at token %d", i);
            break;
        }
    }
    llama_sampler_free(smpl);
    llama_memory_clear(llama_get_memory(s->ctx), true);

    jbyteArray result = to_bytes(env, out);
    std::fill(out.begin(), out.end(), '\0');
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_gogitit_rewinder_llama_LlamaNative_free(JNIEnv *, jobject, jlong handle) {
    auto *s = reinterpret_cast<Session *>(handle);
    if (s == nullptr) return;
    llama_free(s->ctx);
    llama_model_free(s->model);
    delete s;
}
