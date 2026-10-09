package com.gogitit.rewinder.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// Materials
// ---------------------------------------------------------------------------------------------

/** Brushed aluminium body: a soft top-to-bottom falloff plus fine horizontal grain. */
fun Modifier.deckBody(): Modifier = this
    .background(Brush.verticalGradient(listOf(Deck.BodyTop, Deck.BodyMid, Deck.BodyBottom)))
    .drawBehind {
        val step = 2.dp.toPx()
        var y = 0f
        var i = 0
        while (y < size.height) {
            // Deterministic pseudo-random grain so it does not shimmer between frames.
            val n = ((i * 1103515245 + 12345) ushr 16) and 0xFF
            val alpha = 0.015f + (n / 255f) * 0.035f
            val c = if (n % 2 == 0) Color.White.copy(alpha = alpha) else Color.Black.copy(alpha = alpha * 0.6f)
            drawLine(c, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += step
            i++
        }
    }

/** A slightly recessed plate in the body (for settings groups and the mic panel). */
fun Modifier.insetPlate(radius: Dp = 12.dp): Modifier = this
    .clip(RoundedCornerShape(radius))
    .background(Color(0xFFC9C5BC), RoundedCornerShape(radius))
    .border(
        1.5.dp,
        Brush.verticalGradient(listOf(Color(0xFF7F7B73), Color(0xFFF4F2ED))),
        RoundedCornerShape(radius),
    )

/** Cream paper with faint ruled lines, like a cassette J-card. */
fun Modifier.paper(radius: Dp = 6.dp, ruled: Boolean = true): Modifier = this
    .clip(RoundedCornerShape(radius))
    .background(Deck.Paper)
    .drawBehind {
        if (ruled) {
            val gap = 28.dp.toPx()
            var y = 56.dp.toPx()
            while (y < size.height) {
                drawLine(Deck.PaperLine, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                y += gap
            }
            drawLine(Color(0x55D7372B), Offset(28.dp.toPx(), 0f), Offset(28.dp.toPx(), size.height), strokeWidth = 1.dp.toPx())
        }
    }

/** Text stamped into the body: dark ink with a light edge below it. */
@Composable
fun Engraved(
    text: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Bold,
    color: Color = Deck.Engraved,
    spacing: TextUnit = 0.sp,
    mono: Boolean = false,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size,
        fontWeight = weight,
        letterSpacing = spacing,
        fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        style = TextStyle(shadow = Shadow(Color.White.copy(alpha = 0.75f), Offset(0f, 1.5f), 0f)),
    )
}

/** Phillips-head screw for the body corners. */
@Composable
fun Screw(modifier: Modifier = Modifier, angle: Float = 20f) {
    Canvas(modifier.size(12.dp)) {
        val r = size.minDimension / 2f
        drawCircle(Brush.radialGradient(listOf(Color(0xFFF2F0EB), Color(0xFF8E8A82)), center = Offset(r * 0.7f, r * 0.6f), radius = r * 1.6f), r)
        drawCircle(Color(0x55000000), r, style = Stroke(1f))
        rotate(angle) {
            drawLine(Color(0xFF55524C), Offset(r * 0.45f, r), Offset(r * 1.55f, r), strokeWidth = 1.6f)
            drawLine(Color(0xFF55524C), Offset(r, r * 0.45f), Offset(r, r * 1.55f), strokeWidth = 1.6f)
        }
    }
}

/** Small indicator lamp. Lit = amber (or [litColor]) with a glow. */
@Composable
fun Lamp(lit: Boolean, modifier: Modifier = Modifier, litColor: Color = Deck.Amber, diameter: Dp = 12.dp) {
    Canvas(modifier.size(diameter)) {
        val r = size.minDimension / 2f
        if (lit) {
            drawCircle(litColor.copy(alpha = 0.35f), r * 1.0f)
            drawCircle(Brush.radialGradient(listOf(Color.White, litColor, litColor.copy(alpha = 0.9f)), center = Offset(r * 0.8f, r * 0.7f), radius = r), r * 0.8f)
        } else {
            drawCircle(Brush.radialGradient(listOf(Color(0xFF8A857C), Color(0xFF4B4842)), center = Offset(r * 0.8f, r * 0.7f), radius = r), r * 0.8f)
        }
        drawCircle(Color(0x66000000), r * 0.8f, style = Stroke(1f))
    }
}

/** Blinking REC lamp. */
@Composable
fun RecLamp(on: Boolean, modifier: Modifier = Modifier) {
    val blink = rememberInfiniteTransition()
    val alpha by blink.animateFloat(1f, 0.25f, infiniteRepeatable(tween(650), RepeatMode.Reverse))
    Lamp(lit = on, modifier = modifier, litColor = Deck.Rec.copy(alpha = if (on) alpha else 1f), diameter = 14.dp)
}

// ---------------------------------------------------------------------------------------------
// LCD
// ---------------------------------------------------------------------------------------------

/** Recessed reflective LCD in a black frame. */
@Composable
fun LcdPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Deck.Bezel)
            .padding(5.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF8E9A78), Deck.Lcd, Color(0xFFB0BB98))))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) { content() }
}

/** LCD text: dark segments with a faint offset shadow, the way LCD ink sits above the reflector. */
@Composable
fun LcdText(text: String, size: TextUnit, modifier: Modifier = Modifier, weight: FontWeight = FontWeight.Bold) {
    Text(
        text = text,
        modifier = modifier,
        color = Deck.LcdInk,
        fontSize = size,
        fontWeight = weight,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.sp,
        style = TextStyle(shadow = Shadow(Color(0x33000000), Offset(1.5f, 1.5f), 0f)),
    )
}

// ---------------------------------------------------------------------------------------------
// Cassette window with spinning reels
// ---------------------------------------------------------------------------------------------

/**
 * The cassette behind the smoked window. Reels turn at play speed while recording and spin
 * backwards fast while a rewind is being processed. [fill] (0..1) moves tape from the left reel
 * to the right one as the 5 minute memory fills.
 */
@Composable
fun CassetteWindow(
    reelSpeedDegPerSec: Float,
    fill: Float,
    label: String,
    modifier: Modifier = Modifier,
) {
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reelSpeedDegPerSec) {
        if (reelSpeedDegPerSec == 0f) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) angle = (angle + reelSpeedDegPerSec * (now - last) / 1_000_000_000f) % 360f
                last = now
            }
        }
    }
    val tape by animateFloatAsState(fill.coerceIn(0f, 1f), tween(800))
    val measurer = rememberTextMeasurer()

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        // Bezel and smoked window
        drawRoundRect(Deck.Bezel, cornerRadius = CornerRadius(18.dp.toPx()))
        val inset = 9.dp.toPx()
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFF34312E), Deck.Smoke, Color(0xFF121212))),
            topLeft = Offset(inset, inset),
            size = Size(w - inset * 2, h - inset * 2),
            cornerRadius = CornerRadius(12.dp.toPx()),
        )

        // Cassette shell
        val sx = inset + 12.dp.toPx()
        val sy = inset + 10.dp.toPx()
        val sw = w - sx * 2
        val sh = h - sy * 2
        drawRoundRect(Deck.Shell, Offset(sx, sy), Size(sw, sh), CornerRadius(10.dp.toPx()))
        // Head area trapezoid at the bottom
        val headTop = sy + sh * 0.80f
        drawRoundRect(Color(0xFF2C2927), Offset(sx + sw * 0.18f, headTop), Size(sw * 0.64f, sh * 0.20f), CornerRadius(6.dp.toPx()))
        listOf(0.34f, 0.66f).forEach { fx ->
            drawCircle(Color(0xFF151413), 4.dp.toPx(), Offset(sx + sw * fx, headTop + sh * 0.10f))
        }
        // Shell screws
        listOf(Offset(sx + 10.dp.toPx(), sy + 10.dp.toPx()), Offset(sx + sw - 10.dp.toPx(), sy + 10.dp.toPx()),
            Offset(sx + 10.dp.toPx(), sy + sh - 10.dp.toPx()), Offset(sx + sw - 10.dp.toPx(), sy + sh - 10.dp.toPx()),
            Offset(sx + sw / 2f, sy + sh - 8.dp.toPx())).forEach {
            drawCircle(Color(0xFF6B6763), 3.dp.toPx(), it)
            drawCircle(Color(0xFF2A2826), 1.dp.toPx(), it)
        }

        // Paper label with stripes
        val lx = sx + sw * 0.06f
        val ly = sy + sh * 0.07f
        val lw = sw * 0.88f
        val lh = sh * 0.66f
        drawRoundRect(Deck.Label, Offset(lx, ly), Size(lw, lh), CornerRadius(5.dp.toPx()))
        drawRect(Deck.StripeOrange, Offset(lx, ly + lh * 0.62f), Size(lw, lh * 0.10f))
        drawRect(Deck.StripeMustard, Offset(lx, ly + lh * 0.72f), Size(lw, lh * 0.08f))
        drawText(
            measurer, "A",
            topLeft = Offset(lx + 8.dp.toPx(), ly + 4.dp.toPx()),
            style = TextStyle(color = Deck.Ink, fontSize = 18.sp, fontWeight = FontWeight.Black),
        )
        drawText(
            measurer, label,
            topLeft = Offset(lx + 30.dp.toPx(), ly + 8.dp.toPx()),
            style = TextStyle(color = Deck.Ink, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
        )

        // Tape window in the label, with the reels
        val wy = ly + lh * 0.24f
        val wh = lh * 0.34f
        val wx = lx + lw * 0.16f
        val ww = lw * 0.68f
        drawRoundRect(Color(0xFF151413), Offset(wx, wy), Size(ww, wh), CornerRadius(wh / 2f))
        val cy = wy + wh / 2f
        val leftC = Offset(sx + sw * 0.30f, cy)
        val rightC = Offset(sx + sw * 0.70f, cy)
        val hubR = wh * 0.34f
        val minPack = hubR * 1.15f
        val maxPack = wh * 0.95f
        val leftR = minPack + (maxPack - minPack) * (1f - tape)
        val rightR = minPack + (maxPack - minPack) * tape
        clipRect(wx, wy, wx + ww, wy + wh) {
            drawCircle(Deck.Tape, leftR, leftC)
            drawCircle(Deck.Tape, rightR, rightC)
            // tape path along the bottom
            drawLine(Color(0xFF2E2016), Offset(leftC.x, leftC.y + leftR), Offset(rightC.x, rightC.y + rightR), strokeWidth = 2.dp.toPx())
        }
        drawReel(leftC, hubR, angle)
        drawReel(rightC, hubR, angle)

        // Glass reflection
        drawRoundRect(
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.10f), Color.Transparent, Color.White.copy(alpha = 0.05f)),
                start = Offset(0f, 0f), end = Offset(w, h),
            ),
            topLeft = Offset(inset, inset),
            size = Size(w - inset * 2, h - inset * 2),
            cornerRadius = CornerRadius(12.dp.toPx()),
        )
    }
}

private fun DrawScope.drawReel(center: Offset, r: Float, angle: Float) {
    drawCircle(Deck.Hub, r, center)
    drawCircle(Color(0x33000000), r, center, style = Stroke(1.5f))
    drawCircle(Color(0xFF151413), r * 0.55f, center)
    rotate(angle, center) {
        for (k in 0 until 6) {
            val a = (k * 60.0) * PI / 180.0
            val inner = r * 0.30f
            val outer = r * 0.55f
            drawLine(
                Deck.Hub,
                Offset(center.x + (cos(a) * inner).toFloat(), center.y + (sin(a) * inner).toFloat()),
                Offset(center.x + (cos(a) * outer).toFloat(), center.y + (sin(a) * outer).toFloat()),
                strokeWidth = r * 0.16f,
                cap = StrokeCap.Round,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// VU meter
// ---------------------------------------------------------------------------------------------

/** Analog VU meter. [level] 0..1 drives the needle, with a little spring like a real movement. */
@Composable
fun VuMeter(level: Float, modifier: Modifier = Modifier) {
    val needle by animateFloatAsState(level.coerceIn(0f, 1f), spring(dampingRatio = 0.55f, stiffness = 260f))
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(Deck.Bezel, cornerRadius = CornerRadius(10.dp.toPx()))
        val inset = 5.dp.toPx()
        drawRoundRect(
            Brush.radialGradient(listOf(Color(0xFFFFF4D6), Deck.VuFace, Color(0xFFD9C99D)), center = Offset(w / 2f, h * 0.9f), radius = w * 0.8f),
            topLeft = Offset(inset, inset),
            size = Size(w - inset * 2, h - inset * 2),
            cornerRadius = CornerRadius(6.dp.toPx()),
        )
        clipRect(inset, inset, w - inset, h - inset) {
            val pivot = Offset(w / 2f, h * 1.25f)
            val radius = h * 1.05f
            val start = -50f
            val sweep = 100f
            // scale arc, red zone in the last quarter
            drawArc(Deck.Ink, startAngle = -90f + start, sweepAngle = sweep * 0.75f, useCenter = false,
                topLeft = Offset(pivot.x - radius, pivot.y - radius), size = Size(radius * 2, radius * 2), style = Stroke(1.5.dp.toPx()))
            drawArc(Deck.Rec, startAngle = -90f + start + sweep * 0.75f, sweepAngle = sweep * 0.25f, useCenter = false,
                topLeft = Offset(pivot.x - radius, pivot.y - radius), size = Size(radius * 2, radius * 2), style = Stroke(4.dp.toPx()))
            for (t in 0..10) {
                val deg = start + sweep * t / 10f
                val a = (deg - 90f) * PI / 180.0
                val r1 = radius
                val r2 = radius + if (t % 5 == 0) 9.dp.toPx() else 5.dp.toPx()
                drawLine(
                    if (t >= 8) Deck.Rec else Deck.Ink,
                    Offset(pivot.x + (cos(a) * r1).toFloat(), pivot.y + (sin(a) * r1).toFloat()),
                    Offset(pivot.x + (cos(a) * r2).toFloat(), pivot.y + (sin(a) * r2).toFloat()),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }
            drawText(measurer, "VU", topLeft = Offset(w / 2f - 9.dp.toPx(), h * 0.48f),
                style = TextStyle(color = Deck.Ink, fontSize = 14.sp, fontWeight = FontWeight.Black))
            drawText(measurer, "-20", topLeft = Offset(w * 0.12f, h * 0.18f),
                style = TextStyle(color = Deck.Ink, fontSize = 9.sp, fontFamily = FontFamily.Monospace))
            drawText(measurer, "+3", topLeft = Offset(w * 0.80f, h * 0.18f),
                style = TextStyle(color = Deck.Rec, fontSize = 9.sp, fontFamily = FontFamily.Monospace))
            // needle with a soft shadow
            val deg = start + sweep * needle
            val a = (deg - 90f) * PI / 180.0
            val tip = Offset(pivot.x + (cos(a) * (radius + 6.dp.toPx())).toFloat(), pivot.y + (sin(a) * (radius + 6.dp.toPx())).toFloat())
            drawLine(Color(0x33000000), Offset(pivot.x + 2f, pivot.y), Offset(tip.x + 3f, tip.y + 2f), strokeWidth = 2.dp.toPx())
            drawLine(Color(0xFF1A1A1A), pivot, tip, strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(Deck.Bezel, 7.dp.toPx(), Offset(w / 2f, h - inset))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Keys and buttons
// ---------------------------------------------------------------------------------------------

/** Transport symbols, drawn as shapes so they look the same on every platform (no emoji fonts). */
enum class KeyGlyph { None, Rec, Rewind, Stop, Tapes }

@Composable
fun KeyGlyphIcon(glyph: KeyGlyph, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        when (glyph) {
            KeyGlyph.None -> Unit
            KeyGlyph.Rec -> drawCircle(color, w * 0.36f)
            KeyGlyph.Stop -> drawRect(color, Offset(w * 0.18f, h * 0.18f), Size(w * 0.64f, h * 0.64f))
            KeyGlyph.Rewind -> {
                listOf(0f, w * 0.42f).forEach { dx ->
                    val p = androidx.compose.ui.graphics.Path().apply {
                        moveTo(dx + w * 0.04f, h / 2f)
                        lineTo(dx + w * 0.50f, h * 0.16f)
                        lineTo(dx + w * 0.50f, h * 0.84f)
                        close()
                    }
                    drawPath(p, color)
                }
            }
            KeyGlyph.Tapes -> {
                // a little cassette: outline with two hubs
                drawRoundRect(color, Offset(0f, h * 0.18f), Size(w, h * 0.64f), CornerRadius(3.dp.toPx()), style = Stroke(2.dp.toPx()))
                drawCircle(color, w * 0.10f, Offset(w * 0.32f, h * 0.46f), style = Stroke(1.8.dp.toPx()))
                drawCircle(color, w * 0.10f, Offset(w * 0.68f, h * 0.46f), style = Stroke(1.8.dp.toPx()))
            }
        }
    }
}

/**
 * Chunky piano key, like the transport keys of a cassette deck. Travels down when pressed,
 * stays down when [latched] (REC while recording), clicks with a haptic.
 */
@Composable
fun DeckKey(
    glyph: KeyGlyph,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyphColor: Color = Deck.KeyDark,
    latched: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 86.dp,
    progress: Float? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val down = (pressed && enabled) || latched
    val travel by animateFloatAsState(if (down) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 900f))
    val haptics = LocalHapticFeedback.current

    Box(
        modifier
            .height(height)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val lipMax = 11.dp.toPx()
            val shift = 7.dp.toPx() * travel
            val faceH = h - lipMax - 8.dp.toPx()
            val r = CornerRadius(9.dp.toPx())
            // shadow on the body
            drawRoundRect(Color(0x40000000), Offset(2.dp.toPx(), faceH + 6.dp.toPx()), Size(w - 4.dp.toPx(), lipMax + 2.dp.toPx()), r)
            // front lip (shrinks as the key goes down)
            drawRoundRect(
                Brush.verticalGradient(listOf(Deck.KeyBase, Deck.KeyLip)),
                Offset(0f, shift + faceH - 10.dp.toPx()),
                Size(w, lipMax + 10.dp.toPx() - shift),
                r,
            )
            // top face
            drawRoundRect(
                Brush.verticalGradient(listOf(Deck.KeyTop, Deck.KeyBase)),
                Offset(0f, shift),
                Size(w, faceH),
                r,
            )
            drawRoundRect(Color(0x55FFFFFF), Offset(1.dp.toPx(), shift + 1.dp.toPx()), Size(w - 2.dp.toPx(), faceH * 0.45f), r)
            drawRoundRect(Color(0x26000000), Offset(0f, shift), Size(w, faceH), r, style = Stroke(1f))
            // charging strip for a locked key (Rewind before 3:00)
            if (progress != null) {
                val pad = 10.dp.toPx()
                val y = shift + faceH - 7.dp.toPx()
                drawLine(Color(0x33000000), Offset(pad, y), Offset(w - pad, y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                drawLine(Deck.Amber, Offset(pad, y), Offset(pad + (w - pad * 2) * progress.coerceIn(0f, 1f), y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .offset(y = 7.dp * travel)
                .padding(bottom = 19.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (glyph != KeyGlyph.None) {
                KeyGlyphIcon(glyph, if (enabled) glyphColor else glyphColor.copy(alpha = 0.3f))
                Spacer(Modifier.height(4.dp))
            }
            Text(
                label,
                color = if (enabled) Deck.EngravedSoft else Deck.EngravedSoft.copy(alpha = 0.4f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.5.sp,
                maxLines = 1,
            )
        }
    }
}

/** Small round metal push button (settings, back, delete). */
@Composable
fun RoundButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 44.dp,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    Box(
        modifier
            .size(diameter)
            .clickable(interactionSource = interaction, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val c = Offset(r, r)
            drawCircle(Color(0x40000000), r, Offset(r, r + 2.dp.toPx()))
            drawCircle(Deck.BodyShade, r, c)
            drawCircle(
                Brush.radialGradient(
                    if (pressed) listOf(Deck.KeyBase, Color(0xFFB9B5AD)) else listOf(Color.White, Deck.KeyBase),
                    center = Offset(r * 0.7f, r * 0.55f),
                    radius = r * 1.5f,
                ),
                r * 0.86f,
                c,
            )
        }
        Box(Modifier.offset(y = if (pressed) 1.dp else 0.dp)) { content() }
    }
}

/** Flat rectangular key with a label, for actions like DOWNLOAD or REMOVE. */
@Composable
fun SmallKey(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    DeckKey(glyph = KeyGlyph.None, label = text, onClick = onClick, modifier = modifier, enabled = enabled, height = 50.dp)
}

/** Perforated speaker grille: rows of punched holes in the body, each with a lit lower edge. */
@Composable
fun SpeakerGrille(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val pitch = 9.dp.toPx()
        val r = 2.2.dp.toPx()
        val cols = (size.width / pitch).toInt()
        val rows = (size.height / pitch).toInt()
        if (cols <= 0 || rows <= 0) return@Canvas
        val x0 = (size.width - (cols - 1) * pitch) / 2f
        val y0 = (size.height - (rows - 1) * pitch) / 2f
        for (row in 0 until rows) {
            val shift = if (row % 2 == 0) 0f else pitch / 2f
            for (col in 0 until cols) {
                val x = x0 + col * pitch + shift
                if (x > size.width - r) continue
                val c = Offset(x, y0 + row * pitch)
                drawCircle(Color(0x99FFFFFF), r, c + Offset(0f, 0.8f))
                drawCircle(Color(0xFF3A3834), r, c)
            }
        }
    }
}

/** Clamp a level the way the meter shows it, for reuse. */
internal fun meterLevel(levels: List<Float>): Float {
    if (levels.isEmpty()) return 0f
    val recent = levels.takeLast(3)
    return min(1f, max(0f, recent.max()))
}
