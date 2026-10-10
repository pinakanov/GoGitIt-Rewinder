package com.gogitit.rewinder.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Chrome knob with an icon on its domed center. */
@Composable
fun MetalIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 46.dp,
    tint: Color = Deck.KnobGlyph,
) {
    RoundButton(onClick = onClick, modifier = modifier, diameter = diameter, label = contentDescription) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(diameter * 0.44f))
    }
}

/** Top bar for inner screens: a chrome back knob and the screen name printed on the shell. */
@Composable
fun DeckTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MetalIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back", onBack)
        Spacer(Modifier.width(14.dp))
        Engraved(title, 26.sp, logo = true, spacing = 0.08.em, maxLines = 1)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}
