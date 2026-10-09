package com.gogitit.rewinder.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Performance notes: everything that moves (reels, VU needle, blinking lamps, pressed keys)
// is a graphicsLayer transform or a draw-phase read, so it never recomposes the screen.
// Static artwork is built once per size with drawWithCache.

// ---------------------------------------------------------------------------------------------
// Materials
// ---------------------------------------------------------------------------------------------

private val BodyBrush = Brush.verticalGradient(listOf(Deck.BodyTop, Deck.BodyMid, Deck.BodyBottom))

/** Brushed aluminium body: a soft top-to-bottom falloff plus fine horizontal grain. */
fun Modifier.deckBody(): Modifier = this.drawWithCache {
    // Grain as two paths (light and dark lines), built once per size: 2 draw calls per frame
    // instead of hundreds of lines.
    val light = Path()
    val dark = Path()
    val step = 2.dp.toPx()
    var y = 0f
    var i = 0
    while (y < size.height) {
        // Deterministic pseudo-random grain so it does not shimmer.
        val n = ((i * 1103515245 + 12345) ushr 16) and 0xFF
        if (n % 3 != 0) {
            val target = if (n % 2 == 0) light else dark
            target.moveTo(0f, y)
            target.lineTo(size.width, y)
        }
        y += step
        i++
    }
    onDrawBehind {
        drawRect(BodyBrush)
        drawPath(light, Color.White.copy(alpha = 0.035f), style = Stroke(1f))
        drawPath(dark, Color.Black.copy(alpha = 0.02f), style = Stroke(1f))
    }
}

/** A slightly recessed plate in the body (for settings groups and the lamp panel). */
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
    .drawWithCache {
        val lines = Path()
        if (ruled) {
            val gap = 28.dp.toPx()
            var y = 56.dp.toPx()
            while (y < size.height) {
                lines.moveTo(0f, y)
                lines.lineTo(size.width, y)
                y += gap
            }
        }
        val margin = 28.dp.toPx()
        val stroke = Stroke(1.dp.toPx())
        onDrawBehind {
            if (ruled) {
                drawPath(lines, Deck.PaperLine, style = stroke)
                drawLine(Color(0x55D7372B), Offset(margin, 0f), Offset(margin, size.height), strokeWidth = stroke.width)
            }
        }
    }

private val EngravedShadow = TextStyle(shadow = Shadow(Color.White.copy(alpha = 0.75f), Offset(0f, 1.5f), 0f))

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
        style = EngravedShadow,
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

/**
 * On/off blink at a steady rate, like an LED driven by a timer. Changes only twice a second,
 * and only while [enabled]; read it in a draw or graphicsLayer lambda to avoid recomposition.
 */
@Composable
fun rememberBlink(enabled: Boolean, periodMillis: Long = 1000L): State<Boolean> {
    val on = remember { mutableStateOf(true) }
    LaunchedEffect(enabled) {
        on.value = true
        if (!enabled) return@LaunchedEffect
        while (true) {
            delay(periodMillis / 2)
            on.value = !on.value
        }
    }
    return on
}

/** Small indicator lamp. Lit = amber (or [litColor]) with a glow; [blinking] flashes it. */
@Composable
fun Lamp(
    lit: Boolean,
    modifier: Modifier = Modifier,
    litColor: Color = Deck.Amber,
    diameter: Dp = 12.dp,
    blinking: Boolean = false,
) {
    val blink = rememberBlink(lit && blinking, 900L)
    Canvas(modifier.size(diameter)) {
        val r = size.minDimension / 2f
        val on = lit && blink.value // draw-phase read: only this lamp redraws
        if (on) {
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
    Lamp(lit = on, modifier = modifier, litColor = Deck.Rec, diameter = 14.dp, blinking = true)
}

// ---------------------------------------------------------------------------------------------
// LCD
// ---------------------------------------------------------------------------------------------

private val LcdBrush = Brush.verticalGradient(listOf(Color(0xFF8E9A78), Deck.Lcd, Color(0xFFB0BB98)))

/** Recessed reflective LCD in a black frame. */
@Composable
fun LcdPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Deck.Bezel)
            .padding(5.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(LcdBrush)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) { content() }
}

private val LcdShadow = TextStyle(shadow = Shadow(Color(0x33000000), Offset(1.5f, 1.5f), 0f))

/** LCD text: dark segments with a faint offset shadow, the way LCD ink sits above the reflector. */
@Composable
fun LcdText(text: String, size: TextUnit, modifier: Modifier = Modifier, weight: FontWeight = FontWeight.Bold, maxLines: Int = 1) {
    Text(
        text = text,
        modifier = modifier,
        color = Deck.LcdInk,
        fontSize = size,
        fontWeight = weight,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.sp,
        maxLines = maxLines,
        style = LcdShadow,
    )
}

// ---------------------------------------------------------------------------------------------
// Cassette window with spinning reels
// ---------------------------------------------------------------------------------------------

/** Geometry shared by the cassette layers, in pixels for one size. */
private class CassetteGeometry(w: Float, h: Float, density: Float) {
    val inset = 9f * density
    val sx = inset + 12f * density
    val sy = inset + 10f * density
    val sw = w - sx * 2
    val sh = h - sy * 2
    val lx = sx + sw * 0.06f
    val ly = sy + sh * 0.07f
    val lw = sw * 0.88f
    val lh = sh * 0.66f
    val wy = ly + lh * 0.24f
    val wh = lh * 0.34f
    val wx = lx + lw * 0.16f
    val ww = lw * 0.68f
    val cy = wy + wh / 2f
    val leftC = Offset(sx + sw * 0.30f, cy)
    val rightC = Offset(sx + sw * 0.70f, cy)
    val hubR = wh * 0.34f
    val minPack = hubR * 1.15f
    val maxPack = wh * 0.95f
}

/**
 * The cassette behind the smoked window. Reels turn at play speed while recording and spin
 * backwards fast while a rewind is being processed. [fill] (0..1) moves tape from the left reel
 * to the right one as the 5 minute memory fills.
 *
 * Drawn as layers: static shell and label (cached), tape packs (redrawn only when the fill
 * changes), and two hubs rotated on the GPU with graphicsLayer.
 */
@Composable
fun CassetteWindow(
    reelSpeedDegPerSec: Float,
    fill: Float,
    label: String,
    modifier: Modifier = Modifier,
) {
    val angle = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reelSpeedDegPerSec) {
        if (reelSpeedDegPerSec == 0f) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    angle.floatValue = (angle.floatValue + reelSpeedDegPerSec * (now - last) / 1_000_000_000f) % 360f
                }
                last = now
            }
        }
    }
    val tape by animateFloatAsState(fill.coerceIn(0f, 1f), tween(600))
    val measurer = rememberTextMeasurer()

    BoxWithConstraints(modifier) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val g = remember(wPx, hPx, density.density) { CassetteGeometry(wPx, hPx, density.density) }

        // 1. Static: bezel, smoked window, shell, label, tape window background.
        Canvas(
            Modifier.fillMaxSize().drawWithCache {
                val sideA = measurer.measure("A", TextStyle(color = Deck.Ink, fontSize = 18.sp, fontWeight = FontWeight.Black))
                val title = measurer.measure(label, TextStyle(color = Deck.Ink, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold))
                val smoke = Brush.verticalGradient(listOf(Color(0xFF34312E), Deck.Smoke, Color(0xFF121212)))
                onDrawBehind {
                    drawShell(g, smoke)
                    drawText(sideA, topLeft = Offset(g.lx + 8.dp.toPx(), g.ly + 4.dp.toPx()))
                    drawText(title, topLeft = Offset(g.lx + 30.dp.toPx(), g.ly + 8.dp.toPx()))
                    drawRoundRect(Color(0xFF151413), Offset(g.wx, g.wy), Size(g.ww, g.wh), CornerRadius(g.wh / 2f))
                }
            },
        ) {}

        // 2. Tape packs: redraw only when the fill changes.
        Canvas(Modifier.fillMaxSize()) {
            val leftR = g.minPack + (g.maxPack - g.minPack) * (1f - tape)
            val rightR = g.minPack + (g.maxPack - g.minPack) * tape
            clipRect(g.wx, g.wy, g.wx + g.ww, g.wy + g.wh) {
                drawCircle(Deck.Tape, leftR, g.leftC)
                drawCircle(Deck.Tape, rightR, g.rightC)
                drawLine(Color(0xFF2E2016), Offset(g.leftC.x, g.leftC.y + leftR), Offset(g.rightC.x, g.rightC.y + rightR), strokeWidth = 2.dp.toPx())
            }
        }

        // 3. Hubs: drawn once, turned by the GPU.
        val hubSize = with(density) { (g.hubR * 2f).toDp() }
        listOf(g.leftC, g.rightC).forEach { c ->
            Canvas(
                Modifier
                    .offset { IntOffset((c.x - g.hubR).roundToInt(), (c.y - g.hubR).roundToInt()) }
                    .size(hubSize)
                    .graphicsLayer { rotationZ = angle.floatValue },
            ) { drawHub(size.minDimension / 2f) }
        }

        // 4. Glass reflection over everything.
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.10f), Color.Transparent, Color.White.copy(alpha = 0.05f)),
                    start = Offset(0f, 0f), end = Offset(size.width, size.height),
                ),
                topLeft = Offset(g.inset, g.inset),
                size = Size(size.width - g.inset * 2, size.height - g.inset * 2),
                cornerRadius = CornerRadius(12.dp.toPx()),
            )
        }
    }
}

private fun DrawScope.drawShell(g: CassetteGeometry, smoke: Brush) {
    val w = size.width
    val h = size.height
    drawRoundRect(Deck.Bezel, cornerRadius = CornerRadius(18.dp.toPx()))
    drawRoundRect(smoke, topLeft = Offset(g.inset, g.inset), size = Size(w - g.inset * 2, h - g.inset * 2), cornerRadius = CornerRadius(12.dp.toPx()))
    drawRoundRect(Deck.Shell, Offset(g.sx, g.sy), Size(g.sw, g.sh), CornerRadius(10.dp.toPx()))
    val headTop = g.sy + g.sh * 0.80f
    drawRoundRect(Color(0xFF2C2927), Offset(g.sx + g.sw * 0.18f, headTop), Size(g.sw * 0.64f, g.sh * 0.20f), CornerRadius(6.dp.toPx()))
    listOf(0.34f, 0.66f).forEach { fx -> drawCircle(Color(0xFF151413), 4.dp.toPx(), Offset(g.sx + g.sw * fx, headTop + g.sh * 0.10f)) }
    val s = 10.dp.toPx()
    listOf(
        Offset(g.sx + s, g.sy + s), Offset(g.sx + g.sw - s, g.sy + s),
        Offset(g.sx + s, g.sy + g.sh - s), Offset(g.sx + g.sw - s, g.sy + g.sh - s),
        Offset(g.sx + g.sw / 2f, g.sy + g.sh - 8.dp.toPx()),
    ).forEach {
        drawCircle(Color(0xFF6B6763), 3.dp.toPx(), it)
        drawCircle(Color(0xFF2A2826), 1.dp.toPx(), it)
    }
    drawRoundRect(Deck.Label, Offset(g.lx, g.ly), Size(g.lw, g.lh), CornerRadius(5.dp.toPx()))
    drawRect(Deck.StripeOrange, Offset(g.lx, g.ly + g.lh * 0.62f), Size(g.lw, g.lh * 0.10f))
    drawRect(Deck.StripeMustard, Offset(g.lx, g.ly + g.lh * 0.72f), Size(g.lw, g.lh * 0.08f))
}

/** A white hub with six teeth, centered in the canvas. */
private fun DrawScope.drawHub(r: Float) {
    val c = center
    drawCircle(Deck.Hub, r, c)
    drawCircle(Color(0x33000000), r, c, style = Stroke(1.5f))
    drawCircle(Color(0xFF151413), r * 0.55f, c)
    for (k in 0 until 6) {
        val a = (k * 60.0) * PI / 180.0
        val inner = r * 0.30f
        val outer = r * 0.55f
        drawLine(
            Deck.Hub,
            Offset(c.x + (cos(a) * inner).toFloat(), c.y + (sin(a) * inner).toFloat()),
            Offset(c.x + (cos(a) * outer).toFloat(), c.y + (sin(a) * outer).toFloat()),
            strokeWidth = r * 0.16f,
            cap = StrokeCap.Round,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// VU meter
// ---------------------------------------------------------------------------------------------

private const val VU_START = -50f
private const val VU_SWEEP = 100f
private const val VU_PIVOT_Y = 1.25f // pivot below the face, as on real meters
private const val VU_RADIUS = 1.05f

/**
 * Analog VU meter. [level] 0..1 drives the needle with a little spring, like a real movement.
 * The face is cached; only the needle layer rotates.
 */
@Composable
fun VuMeter(level: Float, modifier: Modifier = Modifier) {
    val needle = animateFloatAsState(level.coerceIn(0f, 1f), spring(dampingRatio = 0.55f, stiffness = 260f))
    val measurer = rememberTextMeasurer()
    Box(modifier) {
        Canvas(
            Modifier.fillMaxSize().drawWithCache {
                val vu = measurer.measure("VU", TextStyle(color = Deck.Ink, fontSize = 14.sp, fontWeight = FontWeight.Black))
                val low = measurer.measure("-20", TextStyle(color = Deck.Ink, fontSize = 9.sp, fontFamily = FontFamily.Monospace))
                val high = measurer.measure("+3", TextStyle(color = Deck.Rec, fontSize = 9.sp, fontFamily = FontFamily.Monospace))
                val face = Brush.radialGradient(listOf(Color(0xFFFFF4D6), Deck.VuFace, Color(0xFFD9C99D)), center = Offset(size.width / 2f, size.height * 0.9f), radius = size.width * 0.8f)
                onDrawBehind {
                    val w = size.width
                    val h = size.height
                    val inset = 5.dp.toPx()
                    drawRoundRect(Deck.Bezel, cornerRadius = CornerRadius(10.dp.toPx()))
                    drawRoundRect(face, topLeft = Offset(inset, inset), size = Size(w - inset * 2, h - inset * 2), cornerRadius = CornerRadius(6.dp.toPx()))
                    clipRect(inset, inset, w - inset, h - inset) {
                        val pivot = Offset(w / 2f, h * VU_PIVOT_Y)
                        val radius = h * VU_RADIUS
                        val arcTopLeft = Offset(pivot.x - radius, pivot.y - radius)
                        val arcSize = Size(radius * 2, radius * 2)
                        drawArc(Deck.Ink, -90f + VU_START, VU_SWEEP * 0.75f, false, arcTopLeft, arcSize, style = Stroke(1.5.dp.toPx()))
                        drawArc(Deck.Rec, -90f + VU_START + VU_SWEEP * 0.75f, VU_SWEEP * 0.25f, false, arcTopLeft, arcSize, style = Stroke(4.dp.toPx()))
                        for (t in 0..10) {
                            val a = (VU_START + VU_SWEEP * t / 10f - 90f) * PI / 180.0
                            val r2 = radius + if (t % 5 == 0) 9.dp.toPx() else 5.dp.toPx()
                            drawLine(
                                if (t >= 8) Deck.Rec else Deck.Ink,
                                Offset(pivot.x + (cos(a) * radius).toFloat(), pivot.y + (sin(a) * radius).toFloat()),
                                Offset(pivot.x + (cos(a) * r2).toFloat(), pivot.y + (sin(a) * r2).toFloat()),
                                strokeWidth = 1.5.dp.toPx(),
                            )
                        }
                        drawText(vu, topLeft = Offset(w / 2f - vu.size.width / 2f, h * 0.48f))
                        drawText(low, topLeft = Offset(w * 0.12f, h * 0.18f))
                        drawText(high, topLeft = Offset(w * 0.80f, h * 0.18f))
                    }
                }
            },
        ) {}
        // Needle layer, clipped to the face. Drawn pointing straight up, then rotated around
        // the pivot by the GPU (no redraw, no recomposition while it moves).
        Box(Modifier.fillMaxSize().padding(5.dp).clip(RoundedCornerShape(6.dp))) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val inset = with(androidx.compose.ui.platform.LocalDensity.current) { 5.dp.toPx() }
                val outerH = constraints.maxHeight + inset * 2
                val pivotY = (outerH * VU_PIVOT_Y - inset) / constraints.maxHeight.coerceAtLeast(1)
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = VU_START + VU_SWEEP * needle.value
                            transformOrigin = TransformOrigin(0.5f, pivotY)
                        },
                ) {
                    val pivot = Offset(size.width / 2f, size.height * pivotY)
                    val length = outerH * VU_RADIUS + 6.dp.toPx()
                    val tip = Offset(pivot.x, pivot.y - length)
                    drawLine(Color(0x33000000), Offset(pivot.x + 2f, pivot.y), Offset(tip.x + 3f, tip.y + 2f), strokeWidth = 2.dp.toPx())
                    drawLine(Color(0xFF1A1A1A), pivot, tip, strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
                }
            }
        }
        // Pivot cover at the bottom edge.
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Deck.Bezel, 7.dp.toPx(), Offset(size.width / 2f, size.height - 5.dp.toPx()))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Keys and buttons
// ---------------------------------------------------------------------------------------------

/**
 * A physical press: the part travels down the instant a finger lands, always reaches the
 * bottom (even on a very quick tap), clunks with a haptic, and springs back on release.
 * Returns the travel (0 up, 1 down) for drawing; read it in draw/graphicsLayer lambdas.
 */
@Composable
fun Modifier.physicalPress(
    travel: Animatable<Float, AnimationVector1D>,
    enabled: Boolean,
    label: String,
    onClick: () -> Unit,
): Modifier {
    val haptics = rememberDeckHaptics()
    val scope = rememberCoroutineScope()
    val click by rememberUpdatedState(onClick)
    return this
        .semantics(mergeDescendants = true) {
            role = Role.Button
            contentDescription = label
            if (!enabled) disabled()
            onClick(label = label) {
                if (enabled) click()
                enabled
            }
        }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onPress = {
                    haptics.press()
                    val down = scope.launch { travel.animateTo(1f, tween(50, easing = FastOutLinearInEasing)) }
                    val released = tryAwaitRelease()
                    down.join() // a quick tap still bottoms out, so it is seen and felt
                    if (released) haptics.release()
                    scope.launch { travel.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 900f)) }
                },
                onTap = { click() },
            )
        }
}

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
                    val p = Path().apply {
                        moveTo(dx + w * 0.04f, h / 2f)
                        lineTo(dx + w * 0.50f, h * 0.16f)
                        lineTo(dx + w * 0.50f, h * 0.84f)
                        close()
                    }
                    drawPath(p, color)
                }
            }
            KeyGlyph.Tapes -> {
                drawRoundRect(color, Offset(0f, h * 0.18f), Size(w, h * 0.64f), CornerRadius(3.dp.toPx()), style = Stroke(2.dp.toPx()))
                drawCircle(color, w * 0.10f, Offset(w * 0.32f, h * 0.46f), style = Stroke(1.8.dp.toPx()))
                drawCircle(color, w * 0.10f, Offset(w * 0.68f, h * 0.46f), style = Stroke(1.8.dp.toPx()))
            }
        }
    }
}

private val KeyFaceBrush = Brush.verticalGradient(listOf(Deck.KeyTop, Deck.KeyBase))
private val KeyLipBrush = Brush.verticalGradient(listOf(Deck.KeyBase, Deck.KeyLip))
private val KeyTravel = 7.dp

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
    val travel = remember { Animatable(0f) }
    val latch by animateFloatAsState(if (latched) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 900f))
    val depth = { maxOf(travel.value, latch) }
    val currentProgress by rememberUpdatedState(progress)

    Box(modifier.height(height).physicalPress(travel, enabled, label, onClick)) {
        Canvas(Modifier.fillMaxSize()) {
            val d = depth()
            val w = size.width
            val h = size.height
            val lipMax = 11.dp.toPx()
            val shift = KeyTravel.toPx() * d
            val faceH = h - lipMax - 8.dp.toPx()
            val r = CornerRadius(9.dp.toPx())
            drawRoundRect(Color(0x40000000), Offset(2.dp.toPx(), faceH + 6.dp.toPx()), Size(w - 4.dp.toPx(), lipMax + 2.dp.toPx()), r)
            drawRoundRect(KeyLipBrush, Offset(0f, shift + faceH - 10.dp.toPx()), Size(w, lipMax + 10.dp.toPx() - shift), r)
            translate(top = shift) {
                drawRoundRect(KeyFaceBrush, Offset.Zero, Size(w, faceH), r)
                drawRoundRect(Color(0x55FFFFFF), Offset(1.dp.toPx(), 1.dp.toPx()), Size(w - 2.dp.toPx(), faceH * 0.45f), r)
                drawRoundRect(Color(0x26000000), Offset.Zero, Size(w, faceH), r, style = Stroke(1f))
                // Charging strip for a locked key (Rewind before 3:00).
                currentProgress?.let { p ->
                    val pad = 10.dp.toPx()
                    val y = faceH - 7.dp.toPx()
                    drawLine(Color(0x33000000), Offset(pad, y), Offset(w - pad, y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(Deck.Amber, Offset(pad, y), Offset(pad + (w - pad * 2) * p.coerceIn(0f, 1f), y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                }
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = KeyTravel.toPx() * depth() }
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

/** Small round metal push button (back, delete, search, settings). */
@Composable
fun RoundButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 44.dp,
    label: String = "",
    content: @Composable () -> Unit,
) {
    val travel = remember { Animatable(0f) }
    Box(
        modifier
            .size(diameter)
            .physicalPress(travel, enabled = true, label = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = travel.value
            val r = size.minDimension / 2f
            val c = Offset(r, r)
            drawCircle(Color(0x40000000), r, Offset(r, r + 2.dp.toPx() * (1f - t)))
            drawCircle(Deck.BodyShade, r, c)
            drawCircle(
                Brush.radialGradient(
                    listOf(lerpColor(Color.White, Deck.KeyBase, t), lerpColor(Deck.KeyBase, Color(0xFFB9B5AD), t)),
                    center = Offset(r * 0.7f, r * 0.55f),
                    radius = r * 1.5f,
                ),
                r * 0.86f,
                c,
            )
        }
        Box(Modifier.graphicsLayer { translationY = 1.5.dp.toPx() * travel.value }) { content() }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)

/** Flat rectangular key with a label, for actions like DOWNLOAD or REMOVE. */
@Composable
fun SmallKey(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    DeckKey(glyph = KeyGlyph.None, label = text, onClick = onClick, modifier = modifier, enabled = enabled, height = 50.dp)
}

/** Perforated speaker grille: rows of punched holes, each with a lit lower edge. Cached. */
@Composable
fun SpeakerGrille(modifier: Modifier = Modifier) {
    Canvas(
        modifier.drawWithCache {
            val pitch = 9.dp.toPx()
            val r = 2.2.dp.toPx()
            val holes = Path()
            val edges = Path()
            val cols = (size.width / pitch).toInt()
            val rows = (size.height / pitch).toInt()
            if (cols > 0 && rows > 0) {
                val x0 = (size.width - (cols - 1) * pitch) / 2f
                val y0 = (size.height - (rows - 1) * pitch) / 2f
                for (row in 0 until rows) {
                    val shift = if (row % 2 == 0) 0f else pitch / 2f
                    for (col in 0 until cols) {
                        val x = x0 + col * pitch + shift
                        if (x > size.width - r) continue
                        val y = y0 + row * pitch
                        edges.addOval(androidx.compose.ui.geometry.Rect(Offset(x, y + 0.8f), r))
                        holes.addOval(androidx.compose.ui.geometry.Rect(Offset(x, y), r))
                    }
                }
            }
            onDrawBehind {
                drawPath(edges, Color(0x99FFFFFF))
                drawPath(holes, Color(0xFF3A3834))
            }
        },
    ) {}
}
