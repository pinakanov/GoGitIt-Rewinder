package com.gogitit.rewinder

import com.gogitit.rewinder.audio.AudioSource
import com.gogitit.rewinder.audio.SessionHooks
import com.gogitit.rewinder.core.AudioRingBuffer
import com.gogitit.rewinder.core.RewindPolicy
import com.gogitit.rewinder.data.RecentsRepository
import com.gogitit.rewinder.pipeline.RewindPipeline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class RewinderState(
    val isRunning: Boolean = false,
    /** Audio available for the next Rewind (since the last cut, capped at the window). */
    val bufferedMillis: Long = 0L,
    val isSummarizing: Boolean = false,
    /** Recent input levels, 0..1, oldest first. Drives the waveform. */
    val levels: List<Float> = emptyList(),
    val error: String? = null,
) {
    val canRewind: Boolean
        get() = isRunning && !isSummarizing && bufferedMillis >= RewindPolicy.MIN_REWIND_MILLIS

    /** 0..1 progress toward unlocking Rewind. */
    val unlockProgress: Float
        get() = (bufferedMillis.toFloat() / RewindPolicy.MIN_REWIND_MILLIS).coerceIn(0f, 1f)

    val millisUntilUnlock: Long
        get() = (RewindPolicy.MIN_REWIND_MILLIS - bufferedMillis).coerceAtLeast(0L)
}

/**
 * The whole session state machine, shared by Android and iOS.
 *
 * Every buffer access runs on [audioDispatcher] (single thread), so the ring
 * buffer needs no locks.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class RewinderController(
    private val audioSource: AudioSource,
    private val hooks: SessionHooks,
    private val pipeline: RewindPipeline,
    private val recents: RecentsRepository,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val audioDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val buffer = AudioRingBuffer(RewindPolicy.SAMPLE_RATE, RewindPolicy.WINDOW_SECONDS)
    private val levels = ArrayDeque<Float>()
    private var captureJob: Job? = null

    private val _state = MutableStateFlow(RewinderState())
    val state: StateFlow<RewinderState> = _state.asStateFlow()

    private val _created = MutableSharedFlow<Long>(extraBufferCapacity = 4)

    /** Emits the id of each new Recent, so the UI can open it. */
    val createdRecents: SharedFlow<Long> = _created.asSharedFlow()

    fun start() {
        if (captureJob?.isActive == true) return
        _state.value = RewinderState(isRunning = true)
        hooks.onSessionStarted()
        captureJob = scope.launch(audioDispatcher) {
            try {
                audioSource.frames(RewindPolicy.SAMPLE_RATE)
                    .buffer(capacity = 64)
                    .collect { chunk ->
                        buffer.write(chunk)
                        pushLevel(chunk)
                        chunk.fill(0)
                        _state.update {
                            it.copy(bufferedMillis = buffer.millisSinceCut(), levels = levels.toList())
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                reportError(t.message ?: "Microphone stopped unexpectedly.")
                stop()
            }
        }
    }

    fun stop() {
        val job = captureJob ?: return
        captureJob = null
        _state.update { it.copy(isRunning = false, bufferedMillis = 0L, levels = emptyList()) }
        scope.launch(audioDispatcher) {
            job.cancelAndJoin()
            buffer.wipe()
            levels.clear()
        }
        hooks.onSessionStopped()
    }

    fun toggle() = if (_state.value.isRunning) stop() else start()

    /** Summarizes the buffered audio since the last cut. Ignored until unlocked. */
    fun rewind() {
        if (!_state.value.canRewind) return
        _state.update { it.copy(isSummarizing = true) }
        scope.launch {
            try {
                val pcm = withContext(audioDispatcher) {
                    buffer.snapshotSinceCut().also {
                        buffer.advanceCut()
                        _state.update { s -> s.copy(bufferedMillis = 0L) }
                    }
                }
                val durationMillis = pcm.size * 1000L / RewindPolicy.SAMPLE_RATE
                val endedAt = nowMillis()
                val summary = pipeline.run(pcm, RewindPolicy.SAMPLE_RATE, durationMillis)
                val recent = recents.add(summary, endedAt - durationMillis, endedAt)
                _created.tryEmit(recent.id)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                reportError(t.message ?: "Could not summarize that rewind.")
            } finally {
                _state.update { it.copy(isSummarizing = false) }
            }
        }
    }

    fun delete(id: Long) {
        scope.launch { recents.delete(id) }
    }

    fun reportError(message: String) = _state.update { it.copy(error = message) }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun pushLevel(chunk: ShortArray) {
        if (chunk.isEmpty()) return
        var sum = 0.0
        for (s in chunk) sum += s.toDouble() * s.toDouble()
        val rms = sqrt(sum / chunk.size) / Short.MAX_VALUE
        levels.addLast((rms * 6.0).toFloat().coerceIn(0f, 1f))
        while (levels.size > LEVEL_HISTORY) levels.removeFirst()
    }

    private companion object {
        const val LEVEL_HISTORY = 48
    }
}
