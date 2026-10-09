package com.gogitit.rewinder

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface Screen {
    data object Home : Screen
    data class Summary(val id: Long) : Screen
    data object Privacy : Screen
}

/** Tiny state-based navigation, so the platform back button can drive it too. */
class AppNavigator {
    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _drawerOpen = MutableStateFlow(false)
    val drawerOpen: StateFlow<Boolean> = _drawerOpen.asStateFlow()

    fun open(screen: Screen) {
        _screen.value = screen
        if (screen == Screen.Home) _drawerOpen.value = false
    }

    fun setDrawerOpen(open: Boolean) {
        _drawerOpen.value = open
    }

    /** Returns false when there is nothing left to go back to (let the OS handle it). */
    fun back(): Boolean = when {
        _screen.value != Screen.Home -> {
            _screen.value = Screen.Home
            true
        }
        _drawerOpen.value -> {
            _drawerOpen.value = false
            true
        }
        else -> false
    }
}
