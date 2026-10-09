package com.gogitit.rewinder.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberDeckHaptics(): DeckHaptics {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidDeckHaptics(context) }
}

/**
 * Uses haptic primitives (Pixel and most recent phones) for a real "clunk", falling back to
 * predefined clicks, then to a short one-shot buzz on older devices.
 */
private class AndroidDeckHaptics(context: Context) : DeckHaptics {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }?.takeIf { it.hasVibrator() }

    private val pressEffect: VibrationEffect? by lazy { build(primitiveThud(), VibrationEffect.EFFECT_HEAVY_CLICK, 28L, 255) }
    private val releaseEffect: VibrationEffect? by lazy { build(primitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.7f), VibrationEffect.EFFECT_TICK, 10L, 120) }
    private val tickEffect: VibrationEffect? by lazy { build(primitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.6f), VibrationEffect.EFFECT_CLICK, 15L, 160) }

    override fun press() = play(pressEffect)
    override fun release() = play(releaseEffect)
    override fun tick() = play(tickEffect)

    private fun play(effect: VibrationEffect?) {
        val v = vibrator ?: return
        effect ?: return
        runCatching { v.vibrate(effect) }
    }

    /** THUD (Android 12+) feels like a key bottoming out; CLICK otherwise. */
    private fun primitiveThud(): VibrationEffect? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            primitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f)?.let { return it }
        }
        return primitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f)
    }

    private fun primitive(id: Int, scale: Float): VibrationEffect? {
        val v = vibrator ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        if (!v.areAllPrimitivesSupported(id)) return null
        return VibrationEffect.startComposition().addPrimitive(id, scale).compose()
    }

    private fun build(primitive: VibrationEffect?, predefined: Int, millis: Long, amplitude: Int): VibrationEffect? {
        if (vibrator == null) return null
        if (primitive != null) return primitive
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return VibrationEffect.createPredefined(predefined)
        val amp = if (vibrator.hasAmplitudeControl()) amplitude else VibrationEffect.DEFAULT_AMPLITUDE
        return VibrationEffect.createOneShot(millis, amp)
    }
}
