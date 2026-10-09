package com.gogitit.rewinder

import com.gogitit.rewinder.audio.AudioSource
import com.gogitit.rewinder.audio.SessionHooks
import com.gogitit.rewinder.core.AudioGain
import com.gogitit.rewinder.core.AudioRingBuffer
import com.gogitit.rewinder.core.RewindPolicy
import com.gogitit.rewinder.data.RecentsRepository
import com.gogitit.rewinder.pipeline.RewindPipeline
import com.gogitit.rewinder.pipeline.RewindStage
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.log10
import kotlin.math.sqrt
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class RewinderState(
    val isRunning: Boolean = false,
    /** Audio in memory since Start, capped at the 5 minute window. Every Rewind uses all of it. */
    val bufferedMillis: Long = 0L,
    /** Rewinds captured but not yet summarized (they run one at a time, in order). */
    val pendingRewinds: Int = 0,
    /** Recent input levels, 0..1, oldest first. Drives the waveform. */
    val levels: List<Float> = emptyList(),
    /** Which part of a rewind is running (transcribing, then summarizing). */
    val stage: RewindStage? = null,
    val error: String? = null,
) {
    val isSummarizing: Boolean
        get() = pendingRewinds > 0

    /** Only the 3 minute minimum applies. Rewinds may overlap and may be tapped while others process. */
    val canRewind: Boolean
        get() = isRunning && bufferedMillis >= RewindPolicy.MIN_REWIND_MILLIS

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
    private val pipelineLock = Mutex()
    private var silentMillis = 0L
    private var silenceReported = false

    private val _state = MutableStateFlow(RewinderState())
    val state: StateFlow<RewinderState> = _state.asStateFlow()

    private val _created = MutableSharedFlow<Long>(extraBufferCapacity = 4)

    /** Emits the id of each new Recent, so the UI can open it. */
    val createdRecents: SharedFlow<Long> = _created.asSharedFlow()

    fun start() {
        if (captureJob?.isActive == true) return
        _state.value = RewinderState(isRunning = true)
        silentMillis = 0L
        silenceReported = false
        hooks.onSessionStarted()
        captureJob = scope.launch(audioDispatcher) {
            try {
                audioSource.frames(RewindPolicy.SAMPLE_RATE)
                    .buffer(capacity = 64)
                    .collect { chunk ->
                        checkForBlockedMic(chunk)
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

    /**
     * Captures the last (up to) 5 minutes right now and summarizes it. Windows overlap:
     * nothing is cut, so two rewinds a minute apart share four minutes of audio.
     * Snapshots are taken instantly; summaries run one at a time so only one model is
     * ever loaded. Ignored until 3 minutes are in memory.
     */
    fun rewind() {
        if (!_state.value.canRewind) return
        _state.update { it.copy(pendingRewinds = it.pendingRewinds + 1) }
        scope.launch {
            try {
                val pcm = withContext(audioDispatcher) { buffer.snapshotSinceCut() }
                val endedAt = nowMillis()
                val durationMillis = pcm.size * 1000L / RewindPolicy.SAMPLE_RATE
                pipelineLock.withLock {
                    val summary = pipeline.run(pcm, RewindPolicy.SAMPLE_RATE, durationMillis) { stage ->
                        _state.update { it.copy(stage = stage) }
                    }
                    val recent = recents.add(summary, endedAt - durationMillis, endedAt)
                    _created.tryEmit(recent.id)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                reportError(t.message ?: "Could not summarize that rewind.")
            } finally {
                _state.update {
                    val left = (it.pendingRewinds - 1).coerceAtLeast(0)
                    it.copy(pendingRewinds = left, stage = if (left == 0) null else it.stage)
                }
            }
        }
    }

    fun delete(id: Long) {
        scope.launch { recents.delete(id) }
    }

    fun reportError(message: String) = _state.update { it.copy(error = message) }

    fun clearError() = _state.update { it.copy(error = null) }

    /**
     * Real microphones always pick up some noise. Several seconds of exact zeros means the
     * OS is feeding silence: mic access switched off, or another app (a call, recorder or
     * voice assistant) holding the microphone.
     */
    private fun checkForBlockedMic(chunk: ShortArray) {
        if (!AudioGain.isDigitalSilence(chunk)) {
            silentMillis = 0L
            return
        }
        silentMillis += chunk.size * 1000L / RewindPolicy.SAMPLE_RATE
        if (silentMillis >= 4_000L && !silenceReported) {
            silenceReported = true
            reportError(
                "The microphone is sending silence. Check that Microphone access is on in Quick Settings " +
                    "and that no call, recorder or voice assistant is using the mic, then Stop and Start again."
            )
        }
    }

    /**
     * Maps chunk loudness to 0..1 on a decibel scale, so normal speech (around -30 dBFS)
     * fills about half the bar instead of barely moving a linear meter.
     * Bars rise instantly and fall back gently.
     */
    private fun pushLevel(chunk: ShortArray) {
        if (chunk.isEmpty()) return
        var sum = 0.0
        for (s in chunk) sum += s.toDouble() * s.toDouble()
        val rms = sqrt(sum / chunk.size) / Short.MAX_VALUE
        val db = 20.0 * log10(rms.coerceAtLeast(1e-6))
        val target = ((db - FLOOR_DB) / (CEIL_DB - FLOOR_DB)).toFloat().coerceIn(0f, 1f)
        val previous = levels.lastOrNull() ?: 0f
        val level = if (target >= previous) target else previous * 0.6f + target * 0.4f
        levels.addLast(level)
        while (levels.size > LEVEL_HISTORY) levels.removeFirst()
    }

    private companion object {
        const val LEVEL_HISTORY = 48
        const val FLOOR_DB = -72.0 // near silence
        const val CEIL_DB = -24.0  // close, normal speech
    }
}
