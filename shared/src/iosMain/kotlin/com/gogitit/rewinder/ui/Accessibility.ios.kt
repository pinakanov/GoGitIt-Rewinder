package com.gogitit.rewinder.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIAccessibilityDarkerSystemColorsEnabled
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityIsReduceTransparencyEnabled

/** Reduce Motion, Increase Contrast and Reduce Transparency from iOS Accessibility settings. */
@Composable
actual fun rememberPlatformAccessibility(): DeckAccessibility = remember {
    DeckAccessibility(
        reduceMotion = UIAccessibilityIsReduceMotionEnabled(),
        highContrast = UIAccessibilityDarkerSystemColorsEnabled() || UIAccessibilityIsReduceTransparencyEnabled(),
    )
}
