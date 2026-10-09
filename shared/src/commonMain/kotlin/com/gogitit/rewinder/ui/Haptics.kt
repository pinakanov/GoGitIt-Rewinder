package com.gogitit.rewinder.ui

import androidx.compose.runtime.Composable

/**
 * Mechanical clicks for the deck's keys. The Compose haptics API maps to whatever the OS
 * considers a long-press buzz (often weak or switched off), so each platform plays its own
 * short, crisp effects: Android Vibrator primitives, iOS UIImpactFeedbackGenerator.
 */
interface DeckHaptics {
    /** Key hits the bottom: a firm thud. */
    fun press()

    /** Key springs back: a light tick. */
    fun release()

    /** Selector rows and small switches. */
    fun tick()
}

@Composable
expect fun rememberDeckHaptics(): DeckHaptics
