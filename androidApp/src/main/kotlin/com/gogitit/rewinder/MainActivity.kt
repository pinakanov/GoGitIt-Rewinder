package com.gogitit.rewinder

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.gogitit.rewinder.audio.DeviceAudioConsent
import com.gogitit.rewinder.settings.InputSources

class MainActivity : ComponentActivity() {

    private val graph get() = rewinderGraph

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        // Notifications are optional; the microphone is not.
        if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
            startWithOptionalPhoneAudio()
        } else {
            graph.controller.reportError("Rewinder needs microphone access to listen. You can allow it in Settings.")
        }
    }

    /** Android's "Start recording or casting?" prompt, needed for phone audio on every session. */
    private val screenCapture = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            DeviceAudioConsent.grant(result.resultCode, data)
        } else {
            DeviceAudioConsent.clear()
        }
        // Either way recording starts; without consent it is the microphone only.
        graph.controller.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )

        // Shared navigator decides what "back" means (summary -> home -> close drawer).
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!graph.navigator.back()) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        setContent {
            RewinderApp(graph = graph, onStartRequested = ::requestStart)
        }
    }

    private fun requestStart() {
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filterNot(::hasPermission)

        if (needed.isEmpty()) startWithOptionalPhoneAudio() else permissions.launch(needed.toTypedArray())
    }

    private fun startWithOptionalPhoneAudio() {
        val wantsPhoneAudio = graph.ai?.settings?.inputSource?.value == InputSources.MIC_AND_DEVICE &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        if (!wantsPhoneAudio) {
            graph.controller.start()
            return
        }
        val projections = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        runCatching { screenCapture.launch(projections.createScreenCaptureIntent()) }
            .onFailure { graph.controller.start() }
    }

    private fun hasPermission(permission: String): Boolean =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
