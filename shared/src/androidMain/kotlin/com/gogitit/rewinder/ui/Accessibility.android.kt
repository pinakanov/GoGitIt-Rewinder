package com.gogitit.rewinder.ui

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * "Remove animations" sets the animator scale to 0; Android 14 added a contrast level
 * (UiModeManager.getContrast, 0 standard, 0.5 medium, 1 high). Re-read on configuration changes.
 */
@Composable
actual fun rememberPlatformAccessibility(): DeckAccessibility {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) { readAccessibility(context) }
}

private fun readAccessibility(context: Context): DeckAccessibility {
    val animator = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)
    val contrast = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        (context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.contrast ?: 0f
    } else {
        0f
    }
    return DeckAccessibility(reduceMotion = animator == 0f, highContrast = contrast >= 0.5f)
}
