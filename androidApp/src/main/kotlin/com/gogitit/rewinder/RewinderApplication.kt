package com.gogitit.rewinder

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import com.gogitit.rewinder.ai.AndroidModelStorage
import com.gogitit.rewinder.ai.AndroidSpeechEngine
import com.gogitit.rewinder.ai.AndroidTextEngine
import com.gogitit.rewinder.ai.HttpModelDownloader
import com.gogitit.rewinder.ai.PrefsStore
import com.gogitit.rewinder.audio.AndroidAudioSource
import com.gogitit.rewinder.audio.AndroidDeviceAudioSource
import com.gogitit.rewinder.audio.SessionHooks

class RewinderApplication : Application() {

    /** One graph per process, shared by MainActivity and RewinderService. */
    lateinit var graph: RewinderGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = onDeviceGraph(
            audioSource = AndroidAudioSource(this),
            hooks = ForegroundServiceHooks(this),
            speech = AndroidSpeechEngine,
            text = AndroidTextEngine,
            storage = AndroidModelStorage(this),
            downloader = HttpModelDownloader(),
            settingsStore = PrefsStore(this),
            totalRamBytes = totalRam(),
            cpuCores = Runtime.getRuntime().availableProcessors(),
            deviceAudio = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) AndroidDeviceAudioSource(this) else null,
        )
    }

    private fun totalRam(): Long {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        return ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.totalMem
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
