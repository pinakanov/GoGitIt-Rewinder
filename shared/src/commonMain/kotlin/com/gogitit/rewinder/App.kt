package com.gogitit.rewinder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.gogitit.rewinder.ui.DrawerScaffold
import com.gogitit.rewinder.ui.HomeScreen
import com.gogitit.rewinder.ui.PrivacyScreen
import com.gogitit.rewinder.ui.RecentsDrawer
import com.gogitit.rewinder.ui.RewinderTheme
import com.gogitit.rewinder.ui.SettingsScreen
import com.gogitit.rewinder.ui.SummaryScreen

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
    val drawerOpen by navigator.drawerOpen.collectAsState()
    val models = graph.models
    val modelsState = models?.state?.collectAsState()?.value
    val ai = graph.ai

    LaunchedEffect(Unit) {
        controller.createdRecents.collect { id -> navigator.open(Screen.Summary(id)) }
    }

    RewinderTheme {
        when (val current = screen) {
            Screen.Home -> DrawerScaffold(
                open = drawerOpen,
                onOpenChange = navigator::setDrawerOpen,
                drawer = {
                    RecentsDrawer(
                        recents = recents,
                        onSelect = { navigator.open(Screen.Summary(it)) },
                        onSettings = { navigator.open(Screen.Settings) },
                    )
                },
            ) {
                // Collected here, not at the top, so the 10-per-second audio updates only
                // recompose the home screen, not the navigation and drawer around it.
                val state by controller.state.collectAsState()
                HomeScreen(
                    state = state,
                    onMenu = { navigator.setDrawerOpen(true) },
                    // Reads the live state instead of capturing it, so this lambda stays the same
                    // object and the keys do not recompose on every audio chunk.
                    onToggle = { if (controller.state.value.isRunning) controller.stop() else onStartRequested() },
                    onRewind = controller::rewind,
                    onDismissError = controller::clearError,
                    models = modelsState,
                    modelsDescription = models?.description.orEmpty(),
                    onDownloadModels = { models?.download() },
                    onCancelDownload = { models?.cancel() },
                )
            }

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
