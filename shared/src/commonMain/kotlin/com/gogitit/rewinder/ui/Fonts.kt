package com.gogitit.rewinder.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.gogitit.rewinder.resources.Res
import com.gogitit.rewinder.resources.archivo_black
import com.gogitit.rewinder.resources.caveat_semibold
import com.gogitit.rewinder.resources.doto_black
import com.gogitit.rewinder.resources.plex_mono_medium
import com.gogitit.rewinder.resources.plex_mono_semibold
import org.jetbrains.compose.resources.Font

/**
 * Bundled OFL fonts (licenses in composeResources/files/licenses):
 * Archivo Black for the wordmark, IBM Plex Mono for silkscreen, Doto (black, static instance)
 * for the dot-matrix LCD, Caveat (semibold, static instance) for handwritten tape labels.
 * Each family registers only the weights it ships, so ask for exactly these weights:
 * logo Normal, mono Medium / SemiBold, dots Black, hand SemiBold.
 */
@Composable
fun rememberDeckFonts(): DeckFonts {
    val logo = Font(Res.font.archivo_black, FontWeight.Normal)
    val monoMedium = Font(Res.font.plex_mono_medium, FontWeight.Medium)
    val monoSemi = Font(Res.font.plex_mono_semibold, FontWeight.SemiBold)
    val dots = Font(Res.font.doto_black, FontWeight.Black)
    val hand = Font(Res.font.caveat_semibold, FontWeight.SemiBold)
    return remember(logo, monoMedium, monoSemi, dots, hand) {
        DeckFonts(
            logo = FontFamily(logo),
            mono = FontFamily(monoMedium, monoSemi),
            dots = FontFamily(dots),
            hand = FontFamily(hand),
        )
    }
}
