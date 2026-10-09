package com.gogitit.rewinder.models

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where model files live on this platform. Paths are absolute. */
interface ModelStorage {
    /** Folder holding the models (created if missing). */
    val directory: String

    /** File size in bytes, or -1 if the file does not exist. */
    fun sizeOf(path: String): Long

    /** Replaces [to] with [from]. */
    fun move(from: String, to: String): Boolean

    fun delete(path: String): Boolean
}

/** Streams [url] into [partPath], resuming after [existingBytes]. Returns when complete. */
interface ModelDownloader {
    suspend fun download(url: String, partPath: String, existingBytes: Long, onProgress: (writtenBytes: Long) -> Unit)
}

/** Read access to installed models, used by the transcriber and summarizer. */
interface ModelFiles {
    /** Absolute path if the file is fully installed, else null. */
    fun pathOf(model: ModelFile): String?

    /** The selected Qwen file, or null when the selected summarizer is not a downloaded model (Apple). */
    val summarizerModel: ModelFile?

    val isReady: Boolean
}

/** One row in Settings > On-device models. */
data class ModelEntry(
    val file: ModelFile,
    val installed: Boolean,
    /** Needed by the current settings. */
    val needed: Boolean,
)

/**
 * Shared model manager for Android and iOS: which files the current settings need, whether
 * they are installed, resumable downloads from Hugging Face, and removal. Platforms only supply
 * file access ([ModelStorage]) and HTTP ([ModelDownloader]).
 *
 * Files copied in by hand (scripts/models.sh, Finder file sharing, Device Explorer) are
 * picked up automatically within a few seconds.
 */
class ModelStore(
    private val storage: ModelStorage,
    private val downloader: ModelDownloader,
    /** Current summary model id from settings (a Qwen id, or Apple). */
    private val selectedSummaryModel: () -> String,
) : ModelProvider, ModelFiles {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override val summarizerModel: ModelFile?
        get() = ModelCatalog.qwenById(selectedSummaryModel())

    private val files: List<ModelFile>
        get() = listOfNotNull(ModelCatalog.whisper, ModelCatalog.vad, summarizerModel)

    private val totalBytes: Long
        get() = files.sumOf { it.approxBytes }

    private val _state = MutableStateFlow(scan())
    override val state: StateFlow<ModelsState> = _state.asStateFlow()

    override val description: String
        get() {
            val parts = listOfNotNull("Whisper turbo", summarizerModel?.label).joinToString(" + ")
            val tenths = (totalBytes + 50_000_000L) / 100_000_000L
            return "$parts (about ${tenths / 10}.${tenths % 10} GB)"
        }

    override val isReady: Boolean get() = files.all { pathOf(it) != null }

    init {
        // Notice models that were copied in by hand while the app is open.
        scope.launch {
            while (true) {
                delay(3_000)
                if (!_state.value.downloading) refresh()
            }
        }
    }

    override fun pathOf(model: ModelFile): String? {
        val path = pathFor(model.fileName)
        return if (storage.sizeOf(path) > 0L) path else null
    }

    fun entries(): List<ModelEntry> {
        val needed = files.map { it.id }.toSet()
        return ModelCatalog.all.map { ModelEntry(it, installed = pathOf(it) != null, needed = it.id in needed) }
    }

    /** Re-reads what is installed, for example after the summary model setting changes. */
    override fun refresh() {
        val current = _state.value
        if (current.downloading) return
        val fresh = scan()
        if (fresh.ready != current.ready || fresh.downloadedBytes != current.downloadedBytes ||
            fresh.totalBytes != current.totalBytes
        ) {
            _state.value = fresh.copy(error = current.error)
        }
    }

    fun remove(model: ModelFile) {
        storage.delete(pathFor(model.fileName))
        storage.delete(pathFor(model.fileName + ".part"))
        _state.value = scan()
    }

    override fun download() {
        if (job?.isActive == true) return
        _state.update { it.copy(downloading = true, error = null, totalBytes = totalBytes) }
        job = scope.launch {
            try {
                var doneBefore = 0L
                for (model in files.toList()) {
                    if (pathOf(model) == null) {
                        val part = pathFor(model.fileName + ".part")
                        val existing = storage.sizeOf(part).coerceAtLeast(0L)
                        downloader.download(model.url, part, existing) { written ->
                            _state.update { it.copy(current = model.label, downloadedBytes = doneBefore + written) }
                        }
                        if (!storage.move(part, pathFor(model.fileName))) {
                            error("could not save ${model.fileName}")
                        }
                    }
                    doneBefore += model.approxBytes
                }
                _state.value = scan()
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

    private fun pathFor(name: String) = storage.directory.trimEnd('/') + "/" + name

    private fun scan(): ModelsState {
        val downloaded = files.sumOf { m ->
            if (pathOf(m) != null) m.approxBytes else storage.sizeOf(pathFor(m.fileName + ".part")).coerceAtLeast(0L)
        }
        return ModelsState(ready = isReady, downloadedBytes = downloaded, totalBytes = totalBytes)
    }
}
