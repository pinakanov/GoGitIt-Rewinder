import Foundation
import Shared
import llama

/// llama.cpp (llama.xcframework) for the shared Kotlin OnDeviceSummarizer.
/// Loads the Qwen GGUF, runs one prompt, frees everything. Uses Metal on a real device.
final class TextBridgeImpl: NSObject, TextBridge {
    private static var backendReady = false

    func complete(modelPath: String, prompt: String, nCtx: Int32, threads: Int32, maxTokens: Int32,
                  temperature: Float, topP: Float, topK: Int32, presencePenalty: Float) -> String? {
        if !Self.backendReady {
            llama_backend_init()
            Self.backendReady = true
        }

        var mparams = llama_model_default_params()
        #if targetEnvironment(simulator)
        mparams.n_gpu_layers = 0
        #endif
        guard let model = llama_model_load_from_file(modelPath, mparams) else { return nil }
        defer { llama_model_free(model) }

        var cparams = llama_context_default_params()
        cparams.n_ctx = UInt32(nCtx)
        cparams.n_batch = UInt32(nCtx)
        cparams.n_ubatch = 512
        cparams.n_threads = threads
        cparams.n_threads_batch = threads
        cparams.no_perf = true
        guard let ctx = llama_init_from_model(model, cparams) else { return nil }
        defer { llama_free(ctx) }
        let vocab = llama_model_get_vocab(model)

        // Tokenize; special tokens (<|im_start|> ...) must be parsed, not treated as text.
        let length = Int32(prompt.utf8.count)
        let nPrompt = -llama_tokenize(vocab, prompt, length, nil, 0, true, true)
        guard nPrompt > 0, Int(nPrompt) + Int(maxTokens) <= Int(nCtx) else { return nil }
        var tokens = [llama_token](repeating: 0, count: Int(nPrompt))
        guard llama_tokenize(vocab, prompt, length, &tokens, nPrompt, true, true) >= 0 else { return nil }
        let decoded = tokens.withUnsafeMutableBufferPointer { buf in
            llama_decode(ctx, llama_batch_get_one(buf.baseAddress, Int32(buf.count))) == 0
        }
        guard decoded else { return nil }

        guard let sampler = llama_sampler_chain_init(llama_sampler_chain_default_params()) else { return nil }
        defer { llama_sampler_free(sampler) }
        llama_sampler_chain_add(sampler, llama_sampler_init_penalties(llama_vocab_n_tokens(vocab), 64, 1.0, 0.0, presencePenalty))
        llama_sampler_chain_add(sampler, llama_sampler_init_top_k(topK))
        llama_sampler_chain_add(sampler, llama_sampler_init_top_p(topP, 1))
        llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature))
        llama_sampler_chain_add(sampler, llama_sampler_init_dist(UInt32.max)) // random seed

        var bytes = [UInt8]()
        var piece = [CChar](repeating: 0, count: 256)
        for _ in 0..<maxTokens {
            var token = llama_sampler_sample(sampler, ctx, -1)
            if llama_vocab_is_eog(vocab, token) { break }
            let n = llama_token_to_piece(vocab, token, &piece, Int32(piece.count), 0, false)
            if n > 0 { bytes.append(contentsOf: piece[0..<Int(n)].map { UInt8(bitPattern: $0) }) }
            if llama_decode(ctx, llama_batch_get_one(&token, 1)) != 0 { break }
        }
        return String(decoding: bytes, as: UTF8.self)
    }
}
