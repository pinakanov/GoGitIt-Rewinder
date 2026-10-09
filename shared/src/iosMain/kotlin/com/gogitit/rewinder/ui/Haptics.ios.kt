package com.gogitit.rewinder.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UISelectionFeedbackGenerator

@Composable
actual fun rememberDeckHaptics(): DeckHaptics = remember { IosDeckHaptics() }

/** Taptic Engine: a rigid hit on press, a soft one on release, a selection tick for rows. */
private class IosDeckHaptics : DeckHaptics {
    private val hit = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleRigid)
    private val spring = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleSoft)
    private val selection = UISelectionFeedbackGenerator()

    init {
        hit.prepare()
        spring.prepare()
        selection.prepare()
    }

    override fun press() {
        hit.impactOccurredWithIntensity(1.0)
        hit.prepare()
    }

    override fun release() {
        spring.impactOccurredWithIntensity(0.55)
        spring.prepare()
    }

    override fun tick() {
        selection.selectionChanged()
        selection.prepare()
    }
}
