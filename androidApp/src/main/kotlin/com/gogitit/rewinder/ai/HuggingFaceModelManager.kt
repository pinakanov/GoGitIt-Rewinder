package com.gogitit.rewinder.ai

import android.app.ActivityManager
import android.content.Context
import com.gogitit.rewinder.models.ModelProvider
import com.gogitit.rewinder.models.ModelsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.currentCoroutineContext

/**
 * Downloads the models once into no-backup app storage (never synced to the cloud).
 * Downloads resume from where they stopped. The only network use in the app.
 *
 * Faster for development: run scripts/models.sh on your computer, which downloads the
 * files once and copies them into this same folder over USB.
 */
class HuggingFaceModelManager(context: Context) : ModelProvider {

    private val dir = File(context.noBackupFilesDir, "models").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    /** Qwen size this phone should get, based on its RAM. */
    private val preferredSummarizer: ModelFile = ModelCatalog.summarizerFor(totalRam(context))

    /**
     * The Qwen file to use: whichever is installed (scripts/models.sh may push either size),
     * otherwise the one this phone should download.
     */
    val summarizerModel: ModelFile
        get() = listOf(preferredSummarizer, ModelCatalog.qwen2b, ModelCatalog.qwen08b)
            .firstOrNull { pathOf(it) != null } ?: preferredSummarizer

    private val files: List<ModelFile>
        get() = listOf(ModelCatalog.whisper, ModelCatalog.vad, summarizerModel)

    private val totalBytes: Long
        get() = files.sumOf { it.approxBytes }

    private val _state = MutableStateFlow(initialState())
    override val state: StateFlow<ModelsState> = _state.asStateFlow()

    override val description: String
        get() = "Whisper turbo + ${summarizerModel.label} (about ${"%.1f".format(totalBytes / 1e9)} GB)"

    /** Absolute path if the file is fully downloaded, else null. */
    fun pathOf(model: ModelFile): String? =
        File(dir, model.fileName).takeIf { it.isFile && it.length() > 0L }?.absolutePath

    val isReady: Boolean get() = files.all { pathOf(it) != null }

    override fun download() {
        if (job?.isActive == true) return
        _state.update { it.copy(downloading = true, error = null, totalBytes = totalBytes) }
        job = scope.launch {
            try {
                var doneBefore = 0L
                for (model in files.toList()) {
                    if (pathOf(model) == null) fetch(model, doneBefore)
                    doneBefore += model.approxBytes
                }
                _state.value = initialState()
            } catch (e: CancellationException) {
                _state.update { it.copy(downloading = false) }
                throw e
            } catch (e: Exception) {
                // Any failure (network, storage, server) becomes a resumable error, never a crash.
                _state.update {
                    it.copy(downloading = false, error = "Download stopped: ${e.message ?: "network error"}. Tap to resume.")
                }
            }
        }
    }

    override fun cancel() {
        job?.cancel()
        job = null
        _state.update { it.copy(downloading = false) }
    }

    private suspend fun fetch(model: ModelFile, doneBefore: Long) {
        val target = File(dir, model.fileName)
        val part = File(dir, model.fileName + ".part")
        var existing = if (part.isFile) part.length() else 0L

        val conn = (URL(model.url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true // Hugging Face redirects to its CDN
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "Rewinder-Android/0.1")
            if (existing > 0L) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            val code = conn.responseCode
            if (code == 416 && existing > 0L) {
                // Range not satisfiable: the partial file is already complete.
                if (!part.renameTo(target)) throw IOException("could not save ${model.fileName}")
                return
            }
            if (code == HttpURLConnection.HTTP_OK && existing > 0L) existing = 0L // server ignored Range
            if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
                throw IOException("HTTP $code for ${model.fileName}")
            }
            val length = conn.contentLengthLong.takeIf { it > 0L }?.plus(existing)

            conn.inputStream.use { input ->
                FileOutputStream(part, existing > 0L).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var written = existing
                    var lastReport = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        written += n
                        if (written - lastReport >= 2_000_000L) {
                            lastReport = written
                            _state.update {
                                it.copy(current = model.label, downloadedBytes = doneBefore + written)
                            }
                        }
                    }
                    if (length != null && written != length) {
                        throw IOException("incomplete file (${written} of ${length} bytes)")
                    }
                }
            }
            if (!part.renameTo(target)) throw IOException("could not save ${model.fileName}")
        } finally {
            conn.disconnect()
        }
    }

    private fun initialState(): ModelsState {
        val ready = isReady
        val downloaded = files.sumOf { m ->
            pathOf(m)?.let { m.approxBytes } ?: File(dir, m.fileName + ".part").let { if (it.isFile) it.length() else 0L }
        }
        return ModelsState(ready = ready, downloadedBytes = downloaded, totalBytes = totalBytes)
    }

    private companion object {
        fun totalRam(context: Context): Long {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            return ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.totalMem
        }
    }
}
