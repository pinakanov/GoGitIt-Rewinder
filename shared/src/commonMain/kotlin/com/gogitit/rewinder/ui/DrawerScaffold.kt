package com.gogitit.rewinder.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Locally-style drawer: the drawer sits underneath and the main screen slides
 * right as a rounded card, still peeking at the edge. Swipe or tap to toggle.
 */
@Composable
fun DrawerScaffold(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    drawer: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Deck.BodyShade)) {
        val drawerWidth = maxWidth * 0.82f
        val widthPx = with(LocalDensity.current) { drawerWidth.toPx() }
        val progress = remember { Animatable(if (open) 1f else 0f) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(open) {
            progress.animateTo(if (open) 1f else 0f, tween(durationMillis = 280))
        }

        val dragState = rememberDraggableState { delta ->
            scope.launch { progress.snapTo((progress.value + delta / widthPx).coerceIn(0f, 1f)) }
        }

        // Only composed and drawn while it can be seen, so a closed drawer costs nothing.
        val drawerVisible by remember { derivedStateOf { progress.value > 0f } }
        if (drawerVisible) {
            Box(Modifier.width(drawerWidth).fillMaxHeight()) { drawer() }
        }

        Box(
            Modifier
                .fillMaxSize()
                // Slide and round the card in the graphics layer: no recomposition while dragging.
                .graphicsLayer {
                    translationX = progress.value * widthPx
                    shape = RoundedCornerShape(36.dp * progress.value)
                    clip = progress.value > 0f
                }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = { velocity ->
                        val target = when {
                            velocity > 600f -> true
                            velocity < -600f -> false
                            else -> progress.value > 0.5f
                        }
                        progress.animateTo(if (target) 1f else 0f, tween(durationMillis = 220))
                        onOpenChange(target)
                    },
                ),
        ) {
            content()
            if (drawerVisible) {
                // Tapping the peeking card closes the drawer.
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onOpenChange(false) },
                )
            }
        }
    }
}
