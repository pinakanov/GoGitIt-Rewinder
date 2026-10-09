package com.gogitit.rewinder.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Materials of a portable cassette recorder: brushed silver body, black window bezel,
 * green-grey LCD, cream cassette labels, red REC, amber lamps.
 */
object Deck {
    // Body (brushed aluminium, warm)
    val BodyTop = Color(0xFFE4E1DA)
    val BodyMid = Color(0xFFD2CEC6)
    val BodyBottom = Color(0xFFC2BDB4)
    val BodyShade = Color(0xFF8E8A82)
    val Engraved = Color(0xFF45423C)
    val EngravedSoft = Color(0xFF6E6A62)

    // Keys
    val KeyTop = Color(0xFFF1EFEA)
    val KeyBase = Color(0xFFCFCBC3)
    val KeyLip = Color(0xFF8D8981)
    val KeyDark = Color(0xFF2E2D2B)

    // Window, cassette, tape
    val Bezel = Color(0xFF161616)
    val Smoke = Color(0xFF242424)
    val Shell = Color(0xFF3A3634)
    val Label = Color(0xFFF1E8D4)
    val StripeOrange = Color(0xFFE4572E)
    val StripeMustard = Color(0xFFF2A541)
    val Tape = Color(0xFF4A3323)
    val Hub = Color(0xFFEDEBE6)

    // Displays and lamps
    val Lcd = Color(0xFFA4B08C)
    val LcdInk = Color(0xFF1D2617)
    val LcdGhost = Color(0x221D2617)
    val VuFace = Color(0xFFF3E7C4)
    val Rec = Color(0xFFD7372B)
    val Amber = Color(0xFFFFA81F)

    // Paper (J-cards, manual pages)
    val Paper = Color(0xFFF5EEDC)
    val PaperLine = Color(0x332F6DB5)
    val Ink = Color(0xFF23211E)
    val InkSoft = Color(0xFF5E5A53)

    // Drawer (tape storage)
    val Storage = Color(0xFF26241F)
}

@Composable
fun RewinderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Deck.Rec,
            onPrimary = Color.White,
            background = Deck.BodyMid,
            onBackground = Deck.Engraved,
            surface = Deck.BodyMid,
            onSurface = Deck.Engraved,
        ),
        content = content,
    )
}
