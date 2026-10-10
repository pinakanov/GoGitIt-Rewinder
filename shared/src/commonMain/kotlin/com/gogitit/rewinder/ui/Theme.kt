package com.gogitit.rewinder.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density

/**
 * Materials of the RW-5 in its cobalt finish. The shell blue, the cool grey keys and plates and
 * the orange foam behind the grille are sampled from a blue EPOK cassette player; bezels, the
 * green LCD, the cream cassette and the cream VU face follow the Rewinder design canvas.
 */
object Deck {
    // Shell: cobalt plastic, lit from the top
    val ShellTop = Color(0xFF1B6AC2)
    val ShellMid = Color(0xFF0A59AD)
    val ShellBottom = Color(0xFF044694)
    val ShellDeep = Color(0xFF03397A)

    // Silkscreen printed on the shell (white and the player's pale blue print)
    val Silk = Color(0xFFF3F6FA)
    val SilkSoft = Color(0xFFC9DDF1)
    val Engraved = Silk
    val EngravedSoft = SilkSoft

    // Accents from the player's orange ear pads
    val Orange = Color(0xFFFF8A2A)
    val Foam = Color(0xFFE0681F)
    val FoamDeep = Color(0xFF7A2E0C)

    // Dark frames: LCD, meter, tape bay, key rail, tape rack
    val BezelTop = Color(0xFF30333A)
    val BezelBottom = Color(0xFF121418)
    val Bezel = Color(0xFF17191D)
    val Window = Color(0xFF0D0E10)
    val RailTop = Color(0xFF1E2024)
    val RailBottom = Color(0xFF0D0E10)
    val SlotTop = Color(0xFF2F3238)
    val SlotBottom = Color(0xFF1A1C20)

    // LCD glass
    val LcdTop = Color(0xFFB4C298)
    val LcdBottom = Color(0xFF9DAE82)
    val Lcd = Color(0xFFA9B88D)
    val LcdInk = Color(0xFF1E2418)
    val LcdGhost = Color(0x1A1E2418)

    // Cassette
    val PaperTop = Color(0xFFF8F0DE)
    val PaperBottom = Color(0xFFEBDFC7)
    val Label = Color(0xFFF1E8D4)
    val StripeOrange = Color(0xFFE5592B)
    val StripeAmber = Color(0xFFF3A63D)
    val PackLight = Color(0xFF5C4232)
    val PackDark = Color(0xFF3B291E)
    val TapeLine = Color(0xFF6B4C39)
    val Hub = Color(0xFFF3EFE6)
    val HubCore = Color(0xFF26221E)
    val CassetteInk = Color(0xFF2B2620)

    // VU meter
    val VuCenter = Color(0xFFFCF3D6)
    val VuMid = Color(0xFFF2E3B5)
    val VuEdge = Color(0xFFE0CA90)
    val VuInk = Color(0xFF2A251E)
    val VuRed = Color(0xFFC8372D)
    val VuRedText = Color(0xFFA9281F)

    // Keys: the player's cool grey buttons
    val KeyTop = Color(0xFFFAFAFB)
    val KeyMid = Color(0xFFEDEDEF)
    val KeyBottom = Color(0xFFDCDCE0)
    val KeyLip = Color(0xFFA0A1A8)
    val KeyLipLine = Color(0xFF7B7C84)
    val KeyInk = Color(0xFF34353A)
    val KeyLegend = Color(0xFF3E3F45)
    val KeyLegendOff = Color(0xFF8E8F96)

    // Lamps
    val Rec = Color(0xFFD9352A)
    val RecGlow = Color(0xFFFF3B2B)
    val LampInput = Color(0xFFFFB02E)
    val LampReady = Color(0xFF5EE07A)
    val LampAi = Color(0xFF4FE0C6)
    val Amber = LampInput
    val Focus = Color(0xFF2F6FED)

    // Chrome knobs
    val ChromeA = Color(0xFFF6F4EE)
    val ChromeB = Color(0xFFA9A59B)
    val ChromeC = Color(0xFFF1EEE6)
    val ChromeD = Color(0xFF8F8B82)
    val KnobGlyph = Color(0xFF3A3631)

    // Silver plates for settings groups (the player's grey side panel)
    val PlateTop = Color(0xFFE0E1E4)
    val PlateBottom = Color(0xFFCACBCF)

    // Paper (J-cards, manual pages) and the ink printed on paper and plates
    val Paper = Color(0xFFF5EEDC)
    val PaperLine = Color(0x332F6DB5)
    val Ink = Color(0xFF23211E)
    val InkSoft = Color(0xFF55524C)
    val HandInk = Color(0xFFF3EAD6)
    val SlotMeta = Color(0xFFCFC6B2)
}

/** The four printed faces: wordmark, silkscreen, dot-matrix LCD digits, handwritten labels. */
@Immutable
class DeckFonts(
    val logo: FontFamily,
    val mono: FontFamily,
    val dots: FontFamily,
    val hand: FontFamily,
)

val LocalDeckFonts = staticCompositionLocalOf {
    DeckFonts(FontFamily.SansSerif, FontFamily.Monospace, FontFamily.Monospace, FontFamily.Cursive)
}

/**
 * Accessibility preferences that change how the deck draws: [reduceMotion] stops the reels and
 * the glass sheen, [highContrast] drops grain and reflections and prints legends at full contrast,
 * [largeText] shows the LCD status in a large readable strip.
 */
@Immutable
data class DeckAccessibility(
    val reduceMotion: Boolean = false,
    val highContrast: Boolean = false,
    val largeText: Boolean = false,
)

val LocalDeckAccessibility = staticCompositionLocalOf { DeckAccessibility() }

/** Reads the OS settings (Reduce Motion / animator scale, Increase Contrast). */
@Composable
expect fun rememberPlatformAccessibility(): DeckAccessibility

@Composable
fun RewinderTheme(content: @Composable () -> Unit) {
    val fonts = rememberDeckFonts()
    val platform = rememberPlatformAccessibility()
    val fontScale = LocalDensity.current.fontScale
    val a11y = remember(platform, fontScale) { platform.copy(largeText = fontScale >= 1.3f) }
    CompositionLocalProvider(LocalDeckFonts provides fonts, LocalDeckAccessibility provides a11y) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = Deck.Rec,
                onPrimary = Color.White,
                background = Deck.ShellMid,
                onBackground = Deck.Silk,
                surface = Deck.ShellMid,
                onSurface = Deck.Silk,
            ),
            content = content,
        )
    }
}

/**
 * The deck is a physical object, so its printed legends keep their size when the system text
 * size grows (they would overflow the LCD and keys). Large text sizes get the readable status
 * strip instead (see [DeckAccessibility.largeText]).
 */
@Composable
fun PrintedScale(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale.coerceAtMost(1f)),
        content = content,
    )
}

/** Secondary silkscreen color, raised to full contrast when the OS asks for it. */
@Composable
fun silkSoft(): Color = if (LocalDeckAccessibility.current.highContrast) Deck.Silk else Deck.SilkSoft
