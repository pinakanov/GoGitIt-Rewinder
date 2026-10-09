package com.gogitit.rewinder

import androidx.compose.ui.window.ComposeUIViewController
import com.gogitit.rewinder.audio.NoopSessionHooks
import com.gogitit.rewinder.ios.DownloadBridge
import com.gogitit.rewinder.ios.IosAudioSource
import com.gogitit.rewinder.ios.IosModelDownloader
import com.gogitit.rewinder.ios.IosModelStorage
import com.gogitit.rewinder.ios.IosSpeechEngine
import com.gogitit.rewinder.ios.IosTextEngine
import com.gogitit.rewinder.ios.MicBridge
import com.gogitit.rewinder.ios.PermissionCallback
import com.gogitit.rewinder.ios.SpeechBridge
import com.gogitit.rewinder.ios.TextBridge
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIViewController

private var iosGraph: RewinderGraph? = null

/**
 * iOS entry point, called from ContentView.swift with the Swift bridges.
 * Builds the same graph as Android (onDeviceGraph) once per process.
 *
 * TODO(ios): replace NoopSessionHooks with a Live Activity (Widget Extension target).
 */
fun MainViewController(
    mic: MicBridge,
    speech: SpeechBridge,
    text: TextBridge,
    downloader: DownloadBridge,
): UIViewController {
    val graph = iosGraph ?: onDeviceGraph(
        audioSource = IosAudioSource(mic),
        hooks = NoopSessionHooks,
        speech = IosSpeechEngine(speech),
        text = IosTextEngine(text),
        storage = IosModelStorage(),
        downloader = IosModelDownloader(downloader),
        totalRamBytes = NSProcessInfo.processInfo.physicalMemory.toLong(),
        cpuCores = NSProcessInfo.processInfo.activeProcessorCount.toInt(),
    ).also { iosGraph = it }

    return ComposeUIViewController {
        RewinderApp(
            graph = graph,
            onStartRequested = {
                mic.requestPermission(object : PermissionCallback {
                    override fun onResult(granted: Boolean) {
                        if (granted) {
                            graph.controller.start()
                        } else {
                            graph.controller.reportError(
                                "Rewinder needs microphone access. Turn it on in Settings > Rewinder > Microphone."
                            )
                        }
                    }
                })
            },
        )
    }
}
