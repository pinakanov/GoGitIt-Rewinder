package com.gogitit.rewinder

import androidx.compose.ui.window.ComposeUIViewController
import com.gogitit.rewinder.audio.NoopSessionHooks
import com.gogitit.rewinder.audio.SimulatedAudioSource
import platform.UIKit.UIViewController

/**
 * iOS wiring. TODO(ios):
 *  - replace SimulatedAudioSource with an AVAudioEngine source (16 kHz mono PCM16)
 *  - replace NoopSessionHooks with a Swift SessionHooks that starts/ends a Live Activity
 * See docs/ARCHITECTURE.md.
 */
private val iosGraph: RewinderGraph by lazy {
    RewinderGraph(
        audioSource = SimulatedAudioSource(),
        hooks = NoopSessionHooks,
    )
}

fun MainViewController(): UIViewController = ComposeUIViewController { RewinderApp(iosGraph) }
