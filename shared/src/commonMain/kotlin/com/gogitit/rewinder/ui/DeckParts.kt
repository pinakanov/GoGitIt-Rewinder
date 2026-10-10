package com.gogitit.rewinder.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gogitit.rewinder.core.RewindPolicy
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// Performance: only the reels, the needle and blinking lamps move, and each is a
// graphicsLayer transform or a draw-phase read in its own layer, so nothing recomposes while
// they move. Static artwork is cached per size with drawWithCache. Modules are drawn in the
// design canvas's units (px there) and scaled to the available width.

// ---------------------------------------------------------------------------------------------
// LCD
// ---------------------------------------------------------------------------------------------

/** Green LCD glass with a deep inner shadow, a faint pixel grid and a hard diagonal glare. */
private fun Modifier.lcdGlass(radius: Dp, glare: Boolean): Modifier = this.drawWithCache {
    val r = radius.toPx()
    val path = roundRectPath(Offset.Zero, size, r)
    val glass = Brush.verticalGradient(listOf(Deck.LcdTop, Deck.LcdBottom))
    val rows = Path()
    val cols = Path()
    val step = 3.dp.toPx()
    val line = 1.dp.toPx()
    var y = 0f
    while (y < size.height) {
        rows.addRect(Rect(0f, y, size.width, y + line))
        y += step
    }
    var x = 0f
    while (x < size.width) {
        cols.addRect(Rect(x, 0f, x + line, size.height))
        x += step
    }
    val glareBrush = cssLinear(
        158f, Offset.Zero, size,
        0f to Color.White.copy(alpha = 0.34f),
        0.30f to Color.White.copy(alpha = 0.34f),
        0.304f to Color.Transparent,
        1f to Color.Transparent,
    )
    val depth = 9.dp.toPx()
    val ring = 1.dp.toPx()
    onDrawWithContent {
        val content = this
        clipPath(path) {
            drawRect(glass)
            insetShade(Offset.Zero, size, depth, 0.5f, sides = 0.35f)
            content.drawContent()
            drawPath(rows, Deck.LcdInk.copy(alpha = 0.07f))
            drawPath(cols, Deck.LcdInk.copy(alpha = 0.05f))
            if (glare) drawRect(glareBrush)
        }
        drawPath(path, Color.Black.copy(alpha = 0.4f), style = Stroke(ring))
    }
}

private val LcdInkShadow = Shadow(Color.Black.copy(alpha = 0.13f), Offset(1.5f, 1.5f), 0f)

private fun lcdStyle(
    family: FontFamily,
    size: TextUnit,
    weight: FontWeight,
    spacing: TextUnit = 0.sp,
    color: Color = Deck.LcdInk,
    shadow: Shadow? = LcdInkShadow,
) = TextStyle(
    color = color,
    fontFamily = family,
    fontWeight = weight,
    fontSize = size,
    letterSpacing = spacing,
    lineHeight = size * 1.1f,
    shadow = shadow,
)

private fun digitStyle(family: FontFamily, size: TextUnit, color: Color = Deck.LcdInk, shadow: Shadow? = LcdInkShadow) = TextStyle(
    color = color,
    fontFamily = family,
    fontWeight = FontWeight.Black,
    fontSize = size,
    lineHeight = size * 0.95f,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    shadow = shadow,
)

/** Small LCD in a bezel (download progress). */
@Composable
fun LcdPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val glare = !LocalDeckAccessibility.current.highContrast
    Box(
        modifier
            .bezel(12.dp, dropY = 4.dp, dropBlur = 8.dp, dropAlpha = 0.2f)
            .padding(5.dp)
            .lcdGlass(8.dp, glare)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) { content() }
}

/** LCD segments text for the small panel. */
@Composable
fun LcdText(text: String, size: TextUnit, modifier: Modifier = Modifier, weight: FontWeight = FontWeight.SemiBold, maxLines: Int = 1) {
    val fonts = LocalDeckFonts.current
    Text(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Clip,
        style = lcdStyle(fonts.mono, size, if (weight >= FontWeight.SemiBold) FontWeight.SemiBold else FontWeight.Medium, 0.08.em),
    )
}

/**
 * The deck's main display: mode tag (REC with a blinking dot), the tape counter in dot-matrix
 * digits over unlit "8" segments, the clock and date, a 24-segment tape position bar and a status
 * line. Read by screen readers as one sentence ([description]).
 */
@Composable
fun DeckLcd(
    mode: String,
    recording: Boolean,
    counter: String,
    position: Float,
    status: String,
    source: String?,
    description: String,
    modifier: Modifier = Modifier,
) {
    val fonts = LocalDeckFonts.current
    val glare = !LocalDeckAccessibility.current.highContrast
    val now by rememberLocalNow()
    val blink = rememberBlink(recording)
    val ghost = remember(counter) { counter.map { if (it.isDigit()) '8' else it }.joinToString("") }
    BoxWithConstraints(
        modifier
            .height(118.dp)
            .bezel(18.dp, dropY = 9.dp, dropBlur = 16.dp, dropAlpha = 0.25f)
            .padding(7.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val narrow = maxWidth < 320.dp
        Box(Modifier.fillMaxSize().lcdGlass(12.dp, glare)) {
            Column(
                Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = 9.dp, bottom = 11.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Row(Modifier.padding(bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (recording) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .graphicsLayer { alpha = if (blink.value) 1f else 0.2f }
                                    .background(Deck.LcdInk, CircleShape),
                            )
                            Spacer(Modifier.width(5.dp))
                        }
                        Text(mode, maxLines = 1, style = lcdStyle(fonts.mono, 12.sp, FontWeight.SemiBold, 0.12.em))
                    }
                    Spacer(Modifier.width(10.dp))
                    Box {
                        Text(ghost, maxLines = 1, style = digitStyle(fonts.dots, 44.sp, Deck.LcdGhost, null))
                        Text(counter, maxLines = 1, style = digitStyle(fonts.dots, 44.sp))
                    }
                    Spacer(Modifier.weight(1f))
                    Column(Modifier.padding(bottom = 1.dp), horizontalAlignment = Alignment.End) {
                        Text("${now.hour.pad2()}:${now.minute.pad2()}", maxLines = 1, style = digitStyle(fonts.dots, 25.sp))
                        Spacer(Modifier.height(3.dp))
                        Text(
                            if (narrow) now.shortDateLabel() else now.dateLabel().uppercase(),
                            maxLines = 1,
                            softWrap = false,
                            style = lcdStyle(fonts.mono, 9.5.sp, FontWeight.Medium, 0.1.em),
                        )
                    }
                }
                PositionBar(position, Modifier.fillMaxWidth().height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        status,
                        Modifier.weight(1f),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        style = lcdStyle(fonts.mono, 11.sp, FontWeight.SemiBold, 0.14.em),
                    )
                    if (source != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(source.uppercase(), maxLines = 1, softWrap = false, style = lcdStyle(fonts.mono, 9.5.sp, FontWeight.Medium, 0.06.em))
                    }
                }
            }
        }
    }
}

/**
 * Tape position as 24 segments: the playhead solid, tape behind it at 38 percent, tape ahead at
 * 13 percent. The 3:00 segment (Rewind unlocks) is outlined until the playhead passes it.
 */
@Composable
private fun PositionBar(position: Float, modifier: Modifier) {
    Canvas(modifier) {
        val count = 24
        val gap = 2.dp.toPx()
        val segW = (size.width - gap * (count - 1)) / count
        val head = (position.coerceIn(0f, 1f) * (count - 1)).roundToInt()
        val unlock = (RewindPolicy.MIN_REWIND_MILLIS.toFloat() / RewindPolicy.WINDOW_MILLIS * (count - 1)).roundToInt()
        val ring = 1.dp.toPx()
        for (i in 0 until count) {
            val x = i * (segW + gap)
            val color = when {
                i == head -> Deck.LcdInk
                i < head -> Deck.LcdInk.copy(alpha = 0.38f)
                else -> Deck.LcdInk.copy(alpha = 0.13f)
            }
            drawRect(color, Offset(x, 0f), Size(segW, size.height))
            if (i == unlock && i > head) {
                drawRect(Deck.LcdInk.copy(alpha = 0.5f), Offset(x + ring / 2f, ring / 2f), Size(segW - ring, size.height - ring), style = Stroke(ring))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Cassette bay
// ---------------------------------------------------------------------------------------------

enum class ReelMotion { Stopped, Forward, Rewind }

private const val BAY_W = 362f
private const val BAY_H = 200f
private const val REC_X = 18f
private const val REC_Y = 14f
private const val REC_W = 326f
private const val REC_H = 172f
private const val CAS_X = 49f
private const val CAS_Y = 16f
private const val CAS_W = 264f
private const val CAS_H = 168f
private const val WIN_X = 69f
private const val WIN_Y = 48f
private const val WIN_W = 224f
private const val WIN_H = 52f
private val LeftHub = Offset(113f, 74f)
private val RightHub = Offset(249f, 74f)
private const val PACK_MIN = 14.5f
private const val PACK_MAX = 25f

private fun packLeft(p: Float) = PACK_MAX - (PACK_MAX - PACK_MIN) * p
private fun packRight(p: Float) = PACK_MIN + (PACK_MAX - PACK_MIN) * p

/** Seconds per turn: a fuller reel turns slower for the same tape speed (1.0 s to 1.9 s). */
private fun turnSeconds(pack: Float) = 1.0f + (pack - PACK_MIN) / (PACK_MAX - PACK_MIN) * 0.9f

private class BayLabel(val side: TextLayoutResult, val title: TextLayoutResult)

/**
 * The tape bay: a dark bezel with corner screws, a smoked recess and the C-5 cassette inside.
 * [position] (0..1, the 5 minute memory) moves tape from the supply reel to the take-up reel, so
 * the packs double as a progress gauge. Reels turn counter-clockwise while recording (a fuller
 * reel turns slower) and clockwise fast while a rewind is summarized; stopping freezes them at
 * their current angle.
 */
@Composable
fun CassetteBay(position: Float, motion: ReelMotion, sheen: Boolean, modifier: Modifier = Modifier) {
    val a11y = LocalDeckAccessibility.current
    val fonts = LocalDeckFonts.current
    val measurer = rememberTextMeasurer()
    val tape = animateFloatAsState(position.coerceIn(0f, 1f), tween(600))
    val left = remember { mutableFloatStateOf(0f) }
    val right = remember { mutableFloatStateOf(0f) }
    val spin = if (a11y.reduceMotion) ReelMotion.Stopped else motion

    LaunchedEffect(spin) {
        if (spin == ReelMotion.Stopped) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = (now - last) / 1_000_000_000f
                    if (spin == ReelMotion.Rewind) {
                        val step = 360f / 0.32f * dt
                        left.floatValue = (left.floatValue + step) % 360f
                        right.floatValue = (right.floatValue + step) % 360f
                    } else {
                        val p = tape.value
                        left.floatValue = (left.floatValue - 360f / turnSeconds(packLeft(p)) * dt) % 360f
                        right.floatValue = (right.floatValue - 360f / turnSeconds(packRight(p)) * dt) % 360f
                    }
                }
                last = now
            }
        }
    }

    Box(modifier.aspectRatio(BAY_W / BAY_H)) {
        // 1. Bezel, recess and cassette body (static).
        Spacer(
            Modifier.fillMaxSize().drawWithCache {
                val u = size.width / BAY_W
                val label = BayLabel(
                    side = measurer.measure(
                        "A",
                        TextStyle(color = Deck.CassetteInk, fontFamily = fonts.logo, fontSize = (20f * u).toSp()),
                    ),
                    title = measurer.measure(
                        "REWINDER  C-5",
                        TextStyle(
                            color = Deck.CassetteInk,
                            fontFamily = fonts.mono,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = (13f * u).toSp(),
                            letterSpacing = 0.08.em,
                        ),
                    ),
                )
                onDrawBehind { drawBayBase(u, label) }
            },
        )
        // 2. Tape packs and the exposed tape (redrawn when the fill changes).
        Spacer(
            Modifier.fillMaxSize().drawWithCache {
                val u = size.width / BAY_W
                val window = roundRectPath(Offset(WIN_X * u, WIN_Y * u), Size(WIN_W * u, WIN_H * u), 26f * u)
                onDrawBehind { clipPath(window) { drawPacks(u, tape.value) } }
            },
        )
        // 3. Hubs, each turned by the GPU about its own center.
        listOf(LeftHub to left, RightHub to right).forEach { (hub, angle) ->
            Spacer(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = angle.floatValue
                        transformOrigin = TransformOrigin(hub.x / BAY_W, hub.y / BAY_H)
                    }
                    .drawWithCache {
                        val u = size.width / BAY_W
                        onDrawBehind { drawHub(Offset(hub.x * u, hub.y * u), u) }
                    },
            )
        }
        // 4. Window gloss and the glass over the recess (static).
        Spacer(
            Modifier.fillMaxSize().drawWithCache {
                val u = size.width / BAY_W
                onDrawBehind { drawBayGlass(u, reflections = !a11y.highContrast) }
            },
        )
    }
}

private fun DrawScope.drawBayBase(u: Float, label: BayLabel) {
    val bay = Size(BAY_W * u, BAY_H * u)
    val bayPath = roundRectPath(Offset.Zero, bay, 24f * u)
    softShadow(Offset.Zero, bay, 24f * u, 11f * u, 20f * u, 0.30f)
    ledge(bayPath, roundRectPath(Offset(0f, u), bay, 24f * u), 0.40f)
    drawPath(bayPath, Brush.verticalGradient(listOf(Color(0xFF35383E), Color(0xFF141518))))
    clipPath(bayPath) { drawRect(Color.White.copy(alpha = 0.22f), Offset.Zero, Size(bay.width, u)) }
    listOf(Offset(11.5f, 11.5f), Offset(350.5f, 11.5f), Offset(11.5f, 188.5f), Offset(350.5f, 188.5f)).forEach {
        drawScrew(Offset(it.x * u, it.y * u), 4.5f * u)
    }

    val recTL = Offset(REC_X * u, REC_Y * u)
    val recSize = Size(REC_W * u, REC_H * u)
    val recess = roundRectPath(recTL, recSize, 14f * u)
    ledge(recess, roundRectPath(Offset(recTL.x, recTL.y + u), recSize, 14f * u), 0.14f)
    drawPath(recess, Deck.Window)
    clipPath(recess) {
        insetShade(recTL, recSize, 16f * u, 0.85f, sides = 0.5f)
        drawPath(recess, Color.Black, style = Stroke(3f * u))

        // The cassette sits in the recess, shadow included.
        val casTL = Offset(CAS_X * u, CAS_Y * u)
        val casSize = Size(CAS_W * u, CAS_H * u)
        val cassette = roundRectPath(casTL, casSize, 12f * u)
        softShadow(casTL, casSize, 12f * u, 7f * u, 12f * u, 0.7f)
        drawPath(cassette, Brush.verticalGradient(listOf(Deck.PaperTop, Deck.PaperBottom), startY = casTL.y, endY = casTL.y + casSize.height))
        clipPath(cassette) {
            // Stripes.
            val orangeY = (CAS_Y + 88f) * u
            val amberY = (CAS_Y + 103f) * u
            drawRect(Deck.StripeOrange, Offset(casTL.x, orangeY), Size(casSize.width, 15f * u))
            drawRect(Color.White.copy(alpha = 0.35f), Offset(casTL.x, orangeY), Size(casSize.width, u))
            drawRect(Deck.StripeAmber, Offset(casTL.x, amberY), Size(casSize.width, 15f * u))
            drawRect(Color.Black.copy(alpha = 0.12f), Offset(casTL.x, amberY + 14f * u), Size(casSize.width, u))

            // Head opening: a trapezoid with two capstan holes and a center hole.
            val x0 = (CAS_X + 30f) * u
            val w = 204f * u
            val top = (CAS_Y + 126f) * u
            val h = 42f * u
            val head = Path().apply {
                moveTo(x0 + w * 0.07f, top)
                lineTo(x0 + w * 0.93f, top)
                lineTo(x0 + w, top + h)
                lineTo(x0, top + h)
                close()
            }
            drawPath(head, Brush.verticalGradient(listOf(Color(0xFF3A3530), Color(0xFF211E1B)), startY = top, endY = top + h))
            listOf(
                Offset(x0 + w * 0.19f + 5f * u, top + 19f * u) to 5f * u,
                Offset(x0 + w * 0.81f - 5f * u, top + 19f * u) to 5f * u,
                Offset(x0 + w / 2f, top + 31f * u) to 3f * u,
            ).forEach { (c, r) ->
                drawCircle(Color.White.copy(alpha = 0.18f), r, Offset(c.x, c.y + u))
                drawCircle(Color(0xFF050404), r, c)
            }

            // Molded edges: lit top, shaded bottom.
            drawRect(Color.White.copy(alpha = 0.8f), casTL, Size(casSize.width, u))
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.18f)),
                    startY = casTL.y + casSize.height - 4f * u,
                    endY = casTL.y + casSize.height,
                ),
                Offset(casTL.x, casTL.y + casSize.height - 4f * u),
                Size(casSize.width, 4f * u),
            )

            // Tape window, smoked.
            drawRoundRect(Color(0xFF15110E), Offset(WIN_X * u, WIN_Y * u), Size(WIN_W * u, WIN_H * u), CornerRadius(26f * u))

            // Label: "A" and "REWINDER  C-5" on one baseline.
            val textX = (CAS_X + 24f) * u
            val textY = (CAS_Y + 8f) * u
            drawText(label.side, topLeft = Offset(textX, textY))
            drawText(
                label.title,
                topLeft = Offset(
                    textX + label.side.size.width + 10f * u,
                    textY + label.side.firstBaseline - label.title.firstBaseline,
                ),
            )
        }
        listOf(Offset(59.5f, 26.5f), Offset(302.5f, 26.5f), Offset(59.5f, 173.5f), Offset(302.5f, 173.5f)).forEach {
            drawScrew(Offset(it.x * u, it.y * u), 4.5f * u)
        }
    }
}

private fun DrawScope.drawPacks(u: Float, p: Float) {
    val rl = packLeft(p) * u
    val rr = packRight(p) * u
    val lc = Offset(LeftHub.x * u, LeftHub.y * u)
    val rc = Offset(RightHub.x * u, RightHub.y * u)
    drawLine(Deck.TapeLine, Offset(lc.x, lc.y + rl), Offset(rc.x, rc.y + rr), strokeWidth = 1.8f * u)
    listOf(lc to rl, rc to rr).forEach { (c, r) ->
        drawCircle(Brush.radialGradient(0.55f to Deck.PackLight, 1f to Deck.PackDark, center = c, radius = r), r, c)
        drawCircle(Color.Black.copy(alpha = 0.35f), r, c, style = Stroke(u))
    }
}

/** Reel hub: white ring, dark core, six teeth pointing in. */
private fun DrawScope.drawHub(c: Offset, u: Float) {
    drawCircle(Deck.Hub, 13f * u, c)
    drawCircle(Color.Black.copy(alpha = 0.3f), 13f * u, c, style = Stroke(0.8f * u))
    drawCircle(Deck.HubCore, 8.4f * u, c)
    for (k in 0 until 6) {
        rotate(k * 60f, c) {
            drawRect(Deck.Hub, Offset(c.x - 1.4f * u, c.y - 8.4f * u), Size(2.8f * u, 4.4f * u))
        }
    }
}

private fun DrawScope.drawBayGlass(u: Float, reflections: Boolean) {
    val winTL = Offset(WIN_X * u, WIN_Y * u)
    val winSize = Size(WIN_W * u, WIN_H * u)
    val window = roundRectPath(winTL, winSize, 26f * u)
    clipPath(window) {
        drawRect(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.26f), Color.Transparent), startY = winTL.y, endY = winTL.y + 26f * u),
            winTL,
            Size(winSize.width, 26f * u),
        )
    }
    drawRoundRect(
        Color.Black.copy(alpha = 0.55f),
        Offset(winTL.x + 0.6f * u, winTL.y + 0.6f * u),
        Size(winSize.width - 1.2f * u, winSize.height - 1.2f * u),
        CornerRadius(25.4f * u),
        style = Stroke(1.2f * u),
    )

    val recTL = Offset(REC_X * u, REC_Y * u)
    val recSize = Size(REC_W * u, REC_H * u)
    val recess = roundRectPath(recTL, recSize, 14f * u)
    clipPath(recess) {
        if (reflections) {
            drawRect(
                cssLinear(
                    115f, recTL, recSize,
                    0f to Color.Transparent,
                    0.26f to Color.Transparent,
                    0.31f to Color.White.copy(alpha = 0.17f),
                    0.39f to Color.White.copy(alpha = 0.04f),
                    0.41f to Color.Transparent,
                    0.62f to Color.White.copy(alpha = 0.09f),
                    0.68f to Color.Transparent,
                    1f to Color.Transparent,
                ),
                recTL,
                recSize,
            )
        }
        drawRect(Color.White.copy(alpha = 0.3f), recTL, Size(recSize.width, u))
        insetShade(recTL, recSize, 18f * u, 0.35f, sides = 0.9f, bottom = 0.9f)
    }
}

// ---------------------------------------------------------------------------------------------
// VU meter
// ---------------------------------------------------------------------------------------------

private const val VU_W = 256f
private const val VU_H = 88f
private val VuPivot = Offset(128f, 78f)

private class VuLabels(val low: TextLayoutResult, val high: TextLayoutResult, val vu: TextLayoutResult)

/**
 * Analog VU meter on a cream face. [level] 0..1 swings the needle from the left stop (-52 deg)
 * with a little spring and overshoot, like a real movement. Face and glass are cached; only the
 * needle layers rotate.
 */
@Composable
fun VuMeter(level: Float, modifier: Modifier = Modifier) {
    val a11y = LocalDeckAccessibility.current
    val fonts = LocalDeckFonts.current
    val measurer = rememberTextMeasurer()
    val target = -52f + level.coerceIn(0f, 1f) * 100f
    val angle = animateFloatAsState(
        target,
        if (a11y.reduceMotion) tween(100, easing = LinearEasing) else spring(dampingRatio = 0.6f, stiffness = 380f),
    )
    val percent = (level.coerceIn(0f, 1f) * 10f).roundToInt() * 10
    Box(
        modifier
            .aspectRatio(VU_W / VU_H)
            .semantics {
                contentDescription = "Input level"
                stateDescription = "$percent percent"
            },
    ) {
        Spacer(
            Modifier.fillMaxSize().drawWithCache {
                val u = size.width / VU_W
                val labels = VuLabels(
                    low = measurer.measure("-20", TextStyle(color = Color(0xFF4A3F2B), fontFamily = fonts.mono, fontWeight = FontWeight.Medium, fontSize = (11f * u).toSp())),
                    high = measurer.measure("+3", TextStyle(color = Deck.VuRedText, fontFamily = fonts.mono, fontWeight = FontWeight.Medium, fontSize = (11f * u).toSp())),
                    vu = measurer.measure("VU", TextStyle(color = Deck.VuInk, fontFamily = fonts.logo, fontSize = (17f * u).toSp())),
                )
                onDrawBehind { drawVuFace(u, labels) }
            },
        )
        // Needle shadow, offset (2, 3) in screen space, then the needle.
        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val u = size.width / VU_W
                    rotationZ = angle.value
                    transformOrigin = TransformOrigin(VuPivot.x / VU_W, VuPivot.y / VU_H)
                    translationX = 2f * u
                    translationY = 3f * u
                }
                .drawBehind {
                    val u = size.width / VU_W
                    drawNeedle(u, Color(0x664A2D0A), 2.6f * u)
                },
        )
        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = angle.value
                    transformOrigin = TransformOrigin(VuPivot.x / VU_W, VuPivot.y / VU_H)
                }
                .drawBehind {
                    val u = size.width / VU_W
                    drawNeedle(u, Color(0xFF1C1915), 1.7f * u)
                },
        )
        Spacer(
            Modifier.fillMaxSize().drawWithCache {
                val u = size.width / VU_W
                onDrawBehind { drawVuGlass(u, reflections = !a11y.highContrast) }
            },
        )
    }
}

private fun DrawScope.drawNeedle(u: Float, color: Color, width: Float) {
    val p = Offset(VuPivot.x * u, VuPivot.y * u)
    drawLine(color, p, Offset(p.x, p.y - 63f * u), strokeWidth = width, cap = StrokeCap.Round)
}

private fun DrawScope.drawVuFace(u: Float, labels: VuLabels) {
    val outer = Size(VU_W * u, VU_H * u)
    val outerPath = roundRectPath(Offset.Zero, outer, 15f * u)
    softShadow(Offset.Zero, outer, 15f * u, 8f * u, 14f * u, 0.25f)
    ledge(outerPath, roundRectPath(Offset(0f, u), outer, 15f * u), 0.38f)
    drawPath(outerPath, Brush.verticalGradient(listOf(Deck.BezelTop, Deck.BezelBottom)))
    clipPath(outerPath) { drawRect(Color.White.copy(alpha = 0.2f), Offset.Zero, Size(outer.width, u)) }

    val faceTL = Offset(6f * u, 6f * u)
    val faceSize = Size(244f * u, 76f * u)
    val face = roundRectPath(faceTL, faceSize, 9f * u)
    clipPath(face) {
        drawRect(
            Brush.radialGradient(
                0f to Deck.VuCenter,
                0.55f to Deck.VuMid,
                1f to Deck.VuEdge,
                center = Offset(VuPivot.x * u, (6f + 76f) * u),
                radius = 150f * u,
            ),
            faceTL,
            faceSize,
        )
        insetShade(faceTL, faceSize, 8f * u, 0.5f, sides = 0.4f, color = Color(0xFF462D0A))

        val p = Offset(VuPivot.x * u, VuPivot.y * u)
        val r = 58f * u
        val arcTL = Offset(p.x - r, p.y - r)
        val arcSize = Size(r * 2, r * 2)
        drawArc(Deck.VuInk, -140f, 62f, false, arcTL, arcSize, style = Stroke(1.6f * u))
        drawArc(Deck.VuRed, -78f, 38f, false, arcTL, arcSize, style = Stroke(4.5f * u))
        for (i in 0..10) {
            val deg = -50f + i * 10f
            val inner = if (i == 0 || i == 5 || i == 10) 50f * u else 53f * u
            drawLine(
                if (deg >= 10f) Deck.VuRed else Deck.VuInk,
                polar(p, r, deg),
                polar(p, inner, deg),
                strokeWidth = 1.4f * u,
                cap = StrokeCap.Round,
            )
        }
        drawText(labels.low, topLeft = Offset(22f * u, 30f * u - labels.low.firstBaseline))
        drawText(labels.high, topLeft = Offset(212f * u, 30f * u - labels.high.firstBaseline))
        drawText(labels.vu, topLeft = Offset(p.x - labels.vu.size.width / 2f, 64f * u - labels.vu.firstBaseline))
    }
    drawPath(face, Color.Black.copy(alpha = 0.45f), style = Stroke(u))
}

private fun DrawScope.drawVuGlass(u: Float, reflections: Boolean) {
    val p = Offset(VuPivot.x * u, VuPivot.y * u)
    drawCircle(Color(0xFF1B1814), 9f * u, p)
    drawOval(Color.White.copy(alpha = 0.22f), Offset(p.x - 6.5f * u, p.y - 5.5f * u), Size(8f * u, 4f * u))
    if (!reflections) return
    val faceTL = Offset(6f * u, 6f * u)
    val faceSize = Size(244f * u, 76f * u)
    clipPath(roundRectPath(faceTL, faceSize, 9f * u)) {
        val top = faceSize.height * 0.46f
        drawRect(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.06f)), startY = faceTL.y, endY = faceTL.y + top),
            faceTL,
            Size(faceSize.width, top),
        )
        drawRect(
            cssLinear(
                120f, faceTL, faceSize,
                0f to Color.Transparent,
                0.55f to Color.Transparent,
                0.60f to Color.White.copy(alpha = 0.28f),
                0.66f to Color.Transparent,
                1f to Color.Transparent,
            ),
            faceTL,
            faceSize,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Lamp panel and grille
// ---------------------------------------------------------------------------------------------

/** INPUT, READY and AI jewel lamps in a recessed panel. */
@Composable
fun LampPanel(input: Boolean, ready: Boolean, ai: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier.recess(15.dp).padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        LampRow("INPUT", input, Deck.LampInput)
        LampRow("READY", ready, Deck.LampReady)
        LampRow("AI", ai, Deck.LampAi)
    }
}

@Composable
private fun LampRow(label: String, lit: Boolean, color: Color) {
    Row(
        Modifier.semantics(mergeDescendants = true) { stateDescription = if (lit) "on" else "off" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Lamp(lit, litColor = color, diameter = 14.dp)
        Spacer(Modifier.width(9.dp))
        Engraved(label, 10.5.sp, maxLines = 1)
    }
}

/**
 * Punched speaker grille in a recessed field: two interleaved 15 dp grids of holes, each with a
 * bright punched rim and the player's orange foam showing through, darker at the top where the
 * hole wall shades it. Decorative, so hidden from screen readers.
 */
@Composable
fun SpeakerGrille(modifier: Modifier = Modifier) {
    Spacer(
        modifier.drawWithCache {
            val radius = 14.dp.toPx()
            val field = roundRectPath(Offset.Zero, size, radius)
            val lowered = roundRectPath(Offset(0f, 1.dp.toPx()), size, radius)
            val pitch = 15.dp.toPx()
            val hole = 2.3.dp.toPx()
            val rim = 2.9.dp.toPx()
            val rims = Path()
            val walls = Path()
            val foam = Path()
            val left = 6.dp.toPx()
            val top = 4.dp.toPx()
            listOf(pitch / 2f, pitch).forEach { start ->
                var y = top + start
                while (y < size.height - top) {
                    var x = left + start
                    while (x < size.width - left) {
                        rims.addOval(Rect(Offset(x, y + 0.5.dp.toPx()), rim))
                        walls.addOval(Rect(Offset(x, y), hole))
                        foam.addOval(Rect(Offset(x, y + hole * 0.22f), hole * 0.78f))
                        x += pitch
                    }
                    y += pitch
                }
            }
            val foamBrush = Brush.verticalGradient(listOf(Color(0xFFF4F5F7), Color(0xFFD3D6DB), Color(0xFF9A9FA8)))
            val depth = 6.dp.toPx()
            onDrawBehind {
                ledge(field, lowered, 0.38f)
                drawPath(field, Color.Black.copy(alpha = 0.10f))
                clipPath(field) {
                    drawPath(rims, Color.White.copy(alpha = 0.55f))
                    drawPath(walls, Color(0xFF14161A))
                    drawPath(foam, foamBrush)
                    insetShade(Offset.Zero, size, depth, 0.28f)
                }
            }
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Keys
// ---------------------------------------------------------------------------------------------

/** Legends, drawn as shapes so they look the same on every platform (no emoji fonts). */
enum class KeyGlyph { None, Rec, Rewind, Stop, Tapes, Play, Back }

@Composable
private fun KeyGlyphIcon(glyph: KeyGlyph, enabled: Boolean, latch: () -> Float) {
    val alpha = if (enabled) 1f else 0.4f
    val box = when (glyph) {
        KeyGlyph.Back -> Modifier.size(14.dp, 14.dp)
        KeyGlyph.Play -> Modifier.size(16.dp, 16.dp)
        else -> Modifier.size(26.dp, 22.dp)
    }
    Canvas(box) {
        val k = 1.dp.toPx() // the icons are specified in a dp grid
        fun pt(x: Float, y: Float) = Offset(x * k, y * k)
        val ink = Deck.KeyInk.copy(alpha = alpha)
        when (glyph) {
            KeyGlyph.None -> Unit
            KeyGlyph.Tapes -> {
                drawRoundRect(ink, pt(2f, 3f), Size(22f * k, 16f * k), CornerRadius(3.5f * k), style = Stroke(2f * k))
                drawCircle(ink, 2.4f * k, pt(9f, 10f), style = Stroke(1.8f * k))
                drawCircle(ink, 2.4f * k, pt(17f, 10f), style = Stroke(1.8f * k))
                drawLine(ink, pt(8f, 15f), pt(18f, 15f), strokeWidth = 1.8f * k, cap = StrokeCap.Round)
            }
            KeyGlyph.Rec -> {
                val c = pt(13f, 11f)
                val r = 11f * k
                val spot = Offset(c.x - r * 0.28f, c.y - r * 0.4f)
                drawCircle(Color.Black.copy(alpha = 0.25f), r, Offset(c.x, c.y + k))
                drawCircle(
                    Brush.radialGradient(0f to Color(0xFFFF7B6B), 0.55f to Deck.Rec, 1f to Color(0xFF9E1D14), center = spot, radius = r * 1.5f),
                    r,
                    c,
                    alpha = alpha,
                )
            }
            KeyGlyph.Rewind -> {
                val path = Path().apply {
                    moveTo(13f * k, 4f * k); lineTo(3f * k, 11f * k); lineTo(13f * k, 18f * k); close()
                    moveTo(23f * k, 4f * k); lineTo(13f * k, 11f * k); lineTo(23f * k, 18f * k); close()
                }
                drawPath(path, ink, style = Fill)
                drawPath(path, ink, style = Stroke(1.6f * k, join = StrokeJoin.Round))
            }
            KeyGlyph.Stop -> drawRoundRect(Color(0xFF2A2622).copy(alpha = alpha), pt(5f, 3f), Size(16f * k, 16f * k), CornerRadius(2.5f * k))
            KeyGlyph.Play -> {
                val path = Path().apply { moveTo(4f * k, 2f * k); lineTo(14f * k, 8f * k); lineTo(4f * k, 14f * k); close() }
                drawPath(path, Color(0xFF2A2622).copy(alpha = alpha))
            }
            KeyGlyph.Back -> {
                val path = Path().apply { moveTo(9f * k, 2f * k); lineTo(4f * k, 7f * k); lineTo(9f * k, 12f * k) }
                drawPath(path, Deck.KnobGlyph.copy(alpha = alpha), style = Stroke(2.4f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/**
 * Piano key with a visible lip. Resting: raised on an 8 dp lip with a soft shadow. Pressed: the
 * face travels down 5 dp in 60 ms, the lip shrinks to 3 dp, the shadow tightens and an inner
 * shadow appears, then it springs back. [latched] holds it down (REC while recording) and lights
 * the red dot. Disabled keys dim their legend and do not travel. Small keys ([height] under
 * 64 dp) use a 6 dp lip and 4 dp travel. [progress] draws an orange charging strip (Rewind
 * before 3:00).
 */
@Composable
fun DeckKey(
    glyph: KeyGlyph,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    latched: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 80.dp,
    progress: Float? = null,
    horizontal: Boolean = false,
    a11yLabel: String = label,
) {
    val reduceMotion = LocalDeckAccessibility.current.reduceMotion
    val fonts = LocalDeckFonts.current
    val travel = remember { Animatable(0f) }
    val latch = animateFloatAsState(
        if (latched) 1f else 0f,
        if (latched) tween(60) else spring(dampingRatio = 0.45f, stiffness = 1100f),
    )
    val currentProgress by rememberUpdatedState(progress)
    val small = height < 64.dp
    val lipMax = if (small) 6.dp else 8.dp
    val lipMin = if (small) 2.dp else 3.dp
    val travelMax = if (reduceMotion) 2.dp else lipMax - lipMin
    val radiusTop = if (small) 9.dp else 10.dp
    val radiusBottom = if (small) 7.dp else 8.dp
    val faceHeight = height - lipMax - 4.dp
    val depth = { maxOf(travel.value, latch.value) }

    Box(
        modifier
            .height(height)
            .physicalPress(travel, enabled, a11yLabel, onClick, selected = if (glyph == KeyGlyph.Rec) latched else null),
    ) {
        Spacer(
            Modifier.fillMaxSize().drawWithCache {
                val w = size.width
                val faceH = faceHeight.toPx()
                val face = roundRectPath(Offset.Zero, Size(w, faceH), radiusTop.toPx(), radiusBottom.toPx())
                val faceBrush = Brush.verticalGradient(0f to Deck.KeyTop, 0.38f to Deck.KeyMid, 1f to Deck.KeyBottom, startY = 0f, endY = faceH)
                val bottomShade = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.12f)), startY = faceH - 3.dp.toPx(), endY = faceH)
                val px = 1.dp.toPx()
                onDrawBehind {
                    val d = depth()
                    val shift = travelMax.toPx() * d
                    val lip = lipMax.toPx() - shift
                    softShadow(Offset(0f, shift), Size(w, faceH), radiusTop.toPx(), lip + (5f - 2f * d) * px, (10f - 4f * d) * px, 0.45f)
                    translate(top = shift + lip + px) { drawPath(face, Deck.KeyLipLine) }
                    translate(top = shift + lip) { drawPath(face, Deck.KeyLip) }
                    translate(top = shift) {
                        drawPath(face, faceBrush)
                        clipPath(face) {
                            drawRect(Color.White, Offset.Zero, Size(w, px))
                            drawRect(Color.White.copy(alpha = 0.6f), Offset.Zero, Size(px, faceH))
                            drawRect(bottomShade, Offset(0f, faceH - 3.dp.toPx()), Size(w, 3.dp.toPx()))
                            if (d > 0f) insetShade(Offset.Zero, Size(w, faceH), 5.dp.toPx(), 0.18f * d, sides = 0.3f)
                            currentProgress?.let { p ->
                                val pad = 10.dp.toPx()
                                val y = faceH - 7.dp.toPx()
                                drawLine(Color.Black.copy(alpha = 0.18f), Offset(pad, y), Offset(w - pad, y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                                drawLine(Deck.Orange, Offset(pad, y), Offset(pad + (w - pad * 2) * p.coerceIn(0f, 1f), y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                            }
                        }
                    }
                }
            },
        )
        val legendStyle = TextStyle(
            color = if (enabled) Deck.KeyLegend else Deck.KeyLegendOff,
            fontFamily = fonts.mono,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            letterSpacing = 0.14.em,
        )
        val legend = Modifier
            .fillMaxWidth()
            .height(faceHeight)
            .graphicsLayer { translationY = travelMax.toPx() * depth() }
        if (horizontal) {
            Row(legend, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                KeyGlyphIcon(glyph, enabled) { latch.value }
                if (label.isNotEmpty()) {
                    Spacer(Modifier.width(7.dp))
                    Text(label, maxLines = 1, softWrap = false, style = legendStyle)
                }
            }
        } else {
            Column(legend, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (glyph != KeyGlyph.None) KeyGlyphIcon(glyph, enabled) { latch.value }
                if (glyph != KeyGlyph.None && label.isNotEmpty()) Spacer(Modifier.height(8.dp))
                if (label.isNotEmpty()) Text(label, maxLines = 1, softWrap = false, style = legendStyle)
            }
        }
    }
}

/** Small labelled key for actions like DOWNLOAD or REMOVE (44 dp face). */
@Composable
fun SmallKey(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    DeckKey(glyph = KeyGlyph.None, label = text, onClick = onClick, modifier = modifier, enabled = enabled, height = 54.dp)
}

/** The dark rail the transport keys sit in. */
@Composable
fun TransportRail(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .height(96.dp)
            .bezel(20.dp, top = Deck.RailTop, bottom = Deck.RailBottom, dropY = 9.dp, dropBlur = 16.dp, dropAlpha = 0.3f, well = true)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        content = content,
    )
}

/** VU meter and lamp panel side by side, sized from the width so the meter keeps its shape. */
@Composable
fun MeterRow(level: Float, input: Boolean, ready: Boolean, ai: Boolean, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = 12.dp
        val meterWidth = (maxWidth - gap) * (VU_W / (VU_W + 94f))
        val rowHeight = meterWidth * (VU_H / VU_W)
        Row(Modifier.fillMaxWidth().height(rowHeight)) {
            VuMeter(level, Modifier.width(meterWidth).fillMaxHeight())
            Spacer(Modifier.width(gap))
            LampPanel(input, ready, ai, Modifier.weight(1f).fillMaxHeight())
        }
    }
}
