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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Round metal push button with an engraved icon. */
@Composable
fun MetalIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 44.dp,
    tint: Color = Deck.Engraved,
) {
    RoundButton(onClick = onClick, modifier = modifier, diameter = diameter) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(diameter * 0.48f))
    }
}

/** Top bar for inner screens: a metal back button and an engraved title. */
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
        Engraved(title, 24.sp, weight = FontWeight.Black, spacing = 2.sp)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}
