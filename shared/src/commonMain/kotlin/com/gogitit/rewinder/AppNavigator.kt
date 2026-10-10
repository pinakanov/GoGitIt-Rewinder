package com.gogitit.rewinder

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface Screen {
    data object Home : Screen
    data object Tapes : Screen
    data class Summary(val id: Long) : Screen
    data object Settings : Screen
    data object Privacy : Screen
}

/** Tiny back-stack navigation, so the platform back button can drive it too. */
class AppNavigator {
    private val stack = mutableListOf<Screen>(Screen.Home)

    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    fun open(screen: Screen) {
        if (screen == Screen.Home) {
            stack.clear()
            stack.add(Screen.Home)
        } else if (stack.lastOrNull() != screen) {
            stack.add(screen)
        }
        _screen.value = screen
    }

    /** Returns false when there is nothing left to go back to (let the OS handle it). */
    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        _screen.value = stack.last()
        return true
    }
}
