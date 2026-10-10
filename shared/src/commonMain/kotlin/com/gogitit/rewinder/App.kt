package com.gogitit.rewinder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.gogitit.rewinder.ui.HomeScreen
import com.gogitit.rewinder.ui.LocalDeckAccessibility
import com.gogitit.rewinder.ui.PrivacyScreen
import com.gogitit.rewinder.ui.RewinderTheme
import com.gogitit.rewinder.ui.SettingsScreen
import com.gogitit.rewinder.ui.SummaryScreen
import com.gogitit.rewinder.ui.TapesScreen
import com.gogitit.rewinder.ui.grainOverlay

/**
 * Root UI, shared by Android (MainActivity) and iOS (MainViewController).
 *
 * @param onStartRequested platform hook to ask for mic/notification permission
 *   before calling controller.start().
 */
@Composable
fun RewinderApp(
    graph: RewinderGraph,
    onStartRequested: () -> Unit = { graph.controller.start() },
) {
    val controller = graph.controller
    val navigator = graph.navigator
    val recents by graph.recents.recents.collectAsState()
    val screen by navigator.screen.collectAsState()
    val models = graph.models
    val modelsState = models?.state?.collectAsState()?.value
    val ai = graph.ai

    LaunchedEffect(Unit) {
        controller.createdRecents.collect { id -> navigator.open(Screen.Summary(id)) }
    }

    RewinderTheme {
        // One grain layer over every screen, so all parts read as the same molded plastic.
        Box(Modifier.fillMaxSize().grainOverlay(enabled = !LocalDeckAccessibility.current.highContrast)) {
            when (val current = screen) {
                Screen.Home -> {
                    // Collected here, not at the top, so the 10-per-second audio updates only
                    // recompose the home screen, not the navigation around it.
                    val state by controller.state.collectAsState()
                    HomeScreen(
                        state = state,
                        onTapes = { navigator.open(Screen.Tapes) },
                        // Reads the live state instead of capturing it, so this lambda stays the
                        // same object and the keys do not recompose on every audio chunk.
                        onToggle = { if (controller.state.value.isRunning) controller.stop() else onStartRequested() },
                        onRewind = controller::rewind,
                        onDismissError = controller::clearError,
                        models = modelsState,
                        modelsDescription = models?.description.orEmpty(),
                        onDownloadModels = { models?.download() },
                        onCancelDownload = { models?.cancel() },
                    )
                }

                Screen.Tapes -> TapesScreen(
                    recents = recents,
                    onBack = { navigator.back() },
                    onSelect = { navigator.open(Screen.Summary(it)) },
                    onSettings = { navigator.open(Screen.Settings) },
                )

                is Screen.Summary -> {
                    val recent = recents.firstOrNull { it.id == current.id }
                    if (recent != null) {
                        SummaryScreen(
                            recent = recent,
                            onBack = { navigator.back() },
                            onDelete = {
                                controller.delete(recent.id)
                                navigator.back()
                            },
                        )
                    } else {
                        LaunchedEffect(current) { navigator.back() }
                    }
                }

                Screen.Settings -> {
                    if (ai != null) {
                        SettingsScreen(
                            ai = ai,
                            models = modelsState,
                            modelsDescription = models?.description.orEmpty(),
                            onDownload = { models?.download() },
                            onCancelDownload = { models?.cancel() },
                            onPrivacy = { navigator.open(Screen.Privacy) },
                            onBack = { navigator.back() },
                        )
                    } else {
                        PrivacyScreen(onBack = { navigator.back() })
                    }
                }

                Screen.Privacy -> PrivacyScreen(onBack = { navigator.back() })
            }
        }
    }
}
