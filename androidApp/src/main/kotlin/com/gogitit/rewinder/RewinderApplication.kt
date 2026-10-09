package com.gogitit.rewinder

import android.app.Application
import android.content.Context
import android.content.Intent
import com.gogitit.rewinder.ai.HuggingFaceModelManager
import com.gogitit.rewinder.ai.QwenSummarizer
import com.gogitit.rewinder.ai.WhisperTranscriber
import com.gogitit.rewinder.audio.AndroidAudioSource
import com.gogitit.rewinder.audio.SessionHooks

class RewinderApplication : Application() {

    /** One graph per process, shared by MainActivity and RewinderService. */
    lateinit var graph: RewinderGraph
        private set

    override fun onCreate() {
        super.onCreate()
        val models = HuggingFaceModelManager(this)
        graph = RewinderGraph(
            audioSource = AndroidAudioSource(),
            hooks = ForegroundServiceHooks(this),
            transcriber = WhisperTranscriber(models),
            summarizer = QwenSummarizer(models),
            models = models,
        )
    }
}

val Context.rewinderGraph: RewinderGraph
    get() = (applicationContext as RewinderApplication).graph

/** Keeps the mic alive in the background and puts Rewinder on the lock screen. */
private class ForegroundServiceHooks(private val context: Context) : SessionHooks {
    override fun onSessionStarted() {
        context.startForegroundService(Intent(context, RewinderService::class.java))
    }

    override fun onSessionStopped() {
        context.stopService(Intent(context, RewinderService::class.java))
    }
}
