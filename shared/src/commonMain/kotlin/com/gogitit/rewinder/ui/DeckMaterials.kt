package com.gogitit.rewinder.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as PixelCanvas
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// The plastic, glass and metal the deck is made of. Everything here is drawn with shapes and
// gradients (no bitmaps except the one grain tile), built once per size with drawWithCache.
// Values follow the RW-5 design canvas; px there are dp here.

// ---------------------------------------------------------------------------------------------
// Geometry helpers
// ---------------------------------------------------------------------------------------------

internal fun roundRectPath(topLeft: Offset, size: Size, radius: Float): Path = Path().apply {
    addRoundRect(RoundRect(Rect(topLeft, size), CornerRadius(radius)))
}

internal fun roundRectPath(topLeft: Offset, size: Size, top: Float, bottom: Float): Path = Path().apply {
    addRoundRect(
        RoundRect(
            rect = Rect(topLeft, size),
            topLeft = CornerRadius(top),
            topRight = CornerRadius(top),
            bottomRight = CornerRadius(bottom),
            bottomLeft = CornerRadius(bottom),
        ),
    )
}

/**
 * A CSS linear-gradient over a box: [angleDeg] 0 points up, 90 right, 180 down; stops are
 * fractions of the gradient line, which spans the box corner to corner along that angle.
 */
internal fun cssLinear(angleDeg: Float, topLeft: Offset, size: Size, vararg stops: Pair<Float, Color>): Brush {
    val rad = angleDeg * PI / 180.0
    val dx = sin(rad).toFloat()
    val dy = -cos(rad).toFloat()
    val half = (abs(size.width * dx) + abs(size.height * dy)) / 2f
    val c = Offset(topLeft.x + size.width / 2f, topLeft.y + size.height / 2f)
    return Brush.linearGradient(
        colorStops = stops,
        start = Offset(c.x - dx * half, c.y - dy * half),
        end = Offset(c.x + dx * half, c.y + dy * half),
    )
}

/**
 * Inner shadow for a recess: dark along the top edge fading down, a little along the sides
 * (and the bottom when [bottom] > 0). Call it inside a clip of the recess shape.
 */
internal fun DrawScope.insetShade(
    topLeft: Offset,
    size: Size,
    depth: Float,
    alpha: Float,
    sides: Float = 0.45f,
    bottom: Float = 0f,
    color: Color = Color.Black,
) {
    if (alpha <= 0f) return
    val dark = color.copy(alpha = alpha)
    drawRect(
        Brush.verticalGradient(listOf(dark, Color.Transparent), startY = topLeft.y, endY = topLeft.y + depth),
        topLeft,
        Size(size.width, depth),
    )
    if (sides > 0f) {
        val w = depth * 0.7f
        val side = color.copy(alpha = alpha * sides)
        drawRect(
            Brush.horizontalGradient(listOf(side, Color.Transparent), startX = topLeft.x, endX = topLeft.x + w),
            topLeft,
            Size(w, size.height),
        )
        drawRect(
            Brush.horizontalGradient(listOf(Color.Transparent, side), startX = topLeft.x + size.width - w, endX = topLeft.x + size.width),
            Offset(topLeft.x + size.width - w, topLeft.y),
            Size(w, size.height),
        )
    }
    if (bottom > 0f) {
        val h = depth * 0.7f
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, color.copy(alpha = alpha * bottom)),
                startY = topLeft.y + size.height - h,
                endY = topLeft.y + size.height,
            ),
            Offset(topLeft.x, topLeft.y + size.height - h),
            Size(size.width, h),
        )
    }
}

/** Soft drop shadow under a rounded box, faked with a few growing translucent layers. */
internal fun DrawScope.softShadow(
    topLeft: Offset,
    size: Size,
    radius: Float,
    dy: Float,
    blur: Float,
    alpha: Float,
    color: Color = Color.Black,
) {
    if (alpha <= 0f) return
    val steps = 5
    val layer = color.copy(alpha = alpha / steps)
    for (i in steps downTo 1) {
        val grow = blur * i / steps
        drawRoundRect(
            layer,
            Offset(topLeft.x - grow / 2f, topLeft.y + dy - grow / 2f),
            Size(size.width + grow, size.height + grow),
            CornerRadius(radius + grow / 2f),
        )
    }
}

/** A light hairline just below a shape, where its cut edge catches the light. */
internal fun DrawScope.ledge(path: Path, lowered: Path, alpha: Float) {
    clipPath(path, ClipOp.Difference) { drawPath(lowered, Color.White.copy(alpha = alpha)) }
}

// ---------------------------------------------------------------------------------------------
// Shell plastic
// ---------------------------------------------------------------------------------------------

/**
 * Cobalt shell: top-to-bottom light falloff, fine brushed lines (1 dp light, 1 dp dark) and a
 * soft bloom where the light hits the top left.
 */
fun Modifier.deckBody(): Modifier = this.drawWithCache {
    val shell = Brush.verticalGradient(0f to Deck.ShellTop, 0.52f to Deck.ShellMid, 1f to Deck.ShellBottom)
    val light = Path()
    val dark = Path()
    val line = 1.dp.toPx()
    var y = 0f
    while (y < size.height) {
        light.addRect(Rect(0f, y, size.width, y + line))
        dark.addRect(Rect(0f, y + line, size.width, y + line * 2))
        y += line * 2
    }
    val bloomHeight = minOf(size.height, 260.dp.toPx())
    val bloom = Brush.radialGradient(
        listOf(Color.White.copy(alpha = 0.30f), Color.Transparent),
        center = Offset(size.width * 0.3f, 0f),
        radius = 265.dp.toPx(),
    )
    onDrawBehind {
        drawRect(shell)
        drawPath(light, Color.White.copy(alpha = 0.07f))
        drawPath(dark, Color.Black.copy(alpha = 0.035f))
        drawRect(bloom, size = Size(size.width, bloomHeight))
    }
}

private var grainCache: Pair<Float, ImageBitmap>? = null

/**
 * One tileable grain texture (two octaves of value noise, 1 dp cells), made once per screen
 * density and reused by every screen.
 */
internal fun grainTile(density: Float): ImageBitmap {
    grainCache?.let { (d, image) -> if (d == density) return image }
    val cells = 128
    val cell = density.coerceAtLeast(1f)
    val px = (cells * cell).roundToInt().coerceAtLeast(1)
    val image = ImageBitmap(px, px)
    val canvas = PixelCanvas(image)
    var seed = 0x2545F491
    fun next(): Float {
        seed = seed * 1103515245 + 12345
        return ((seed ushr 8) and 0xFFFF) / 65535f
    }
    val half = cells / 2
    val coarse = FloatArray(half * half) { next() }
    val buckets = Array(6) { ArrayList<Offset>() }
    for (y in 0 until cells) {
        for (x in 0 until cells) {
            val v = 0.6f * next() + 0.4f * coarse[(y / 2) * half + x / 2]
            buckets[(v * buckets.size).toInt().coerceIn(0, buckets.lastIndex)]
                .add(Offset(x * cell + cell / 2f, y * cell + cell / 2f))
        }
    }
    val paint = Paint().apply {
        strokeWidth = cell
        strokeCap = StrokeCap.Square
        isAntiAlias = false
    }
    buckets.forEachIndexed { i, points ->
        paint.color = Color(0xFF2E3138).copy(alpha = 0.6f * (i + 0.5f) / buckets.size)
        canvas.drawPoints(PointMode.Points, points, paint)
    }
    grainCache = density to image
    return image
}

/**
 * Grain over everything (keys included) so every part reads as the same molded plastic.
 * Drawn after the content, at 20 percent, with no hit testing.
 */
fun Modifier.grainOverlay(enabled: Boolean): Modifier = if (!enabled) this else this.drawWithCache {
    val brush = ShaderBrush(ImageShader(grainTile(density), TileMode.Repeated, TileMode.Repeated))
    onDrawWithContent {
        drawContent()
        drawRect(brush, alpha = 0.2f)
    }
}

/**
 * A panel cut into the shell: slightly darker fill, inner shadow along the top, and a light
 * hairline under the bottom edge.
 */
fun Modifier.recess(
    radius: Dp,
    fill: Color = Color.Black.copy(alpha = 0.13f),
    depth: Dp = 6.dp,
    shade: Float = 0.30f,
    ledgeAlpha: Float = 0.35f,
): Modifier = this.drawWithCache {
    val r = radius.toPx()
    val path = roundRectPath(Offset.Zero, size, r)
    val lowered = roundRectPath(Offset(0f, 1.dp.toPx()), size, r)
    onDrawBehind {
        ledge(path, lowered, ledgeAlpha)
        drawPath(path, fill)
        clipPath(path) { insetShade(Offset.Zero, size, depth.toPx(), shade) }
    }
}

/**
 * The dark molded frame around the LCD, meter, tape bay, key rail and tape rack: soft drop
 * shadow, lit hairline under it, top-lit gradient and either a thin top highlight (raised) or a
 * deep inner shadow (a well, [well] = true).
 */
fun Modifier.bezel(
    radius: Dp,
    top: Color = Deck.BezelTop,
    bottom: Color = Deck.BezelBottom,
    dropY: Dp = 9.dp,
    dropBlur: Dp = 16.dp,
    dropAlpha: Float = 0.28f,
    well: Boolean = false,
): Modifier = this.drawWithCache {
    val r = radius.toPx()
    val path = roundRectPath(Offset.Zero, size, r)
    val lowered = roundRectPath(Offset(0f, 1.dp.toPx()), size, r)
    val fill = Brush.verticalGradient(listOf(top, bottom))
    val hairline = 1.dp.toPx()
    onDrawBehind {
        softShadow(Offset.Zero, size, r, dropY.toPx(), dropBlur.toPx(), dropAlpha)
        ledge(path, lowered, 0.38f)
        drawPath(path, fill)
        clipPath(path) {
            if (well) {
                insetShade(Offset.Zero, size, 6.dp.toPx(), 0.8f, sides = 0.4f)
            } else {
                drawRect(Color.White.copy(alpha = 0.18f), Offset.Zero, Size(size.width, hairline))
            }
        }
    }
}

/** A raised silver plate for groups of settings, like the player's grey side panel. */
fun Modifier.insetPlate(radius: Dp = 12.dp): Modifier = this.drawWithCache {
    val r = radius.toPx()
    val path = roundRectPath(Offset.Zero, size, r)
    val fill = Brush.verticalGradient(listOf(Deck.PlateTop, Deck.PlateBottom))
    val edge = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.85f), Color.Black.copy(alpha = 0.25f)))
    onDrawBehind {
        softShadow(Offset.Zero, size, r, 4.dp.toPx(), 8.dp.toPx(), 0.30f)
        drawPath(path, fill)
        drawPath(path, edge, style = Stroke(1.dp.toPx()))
    }
}

/** Cream paper with faint ruled lines, like a cassette J-card. */
fun Modifier.paper(radius: Dp = 6.dp, ruled: Boolean = true): Modifier = this.drawWithCache {
    val r = radius.toPx()
    val shape = roundRectPath(Offset.Zero, size, r)
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
    val fill = Brush.verticalGradient(listOf(Deck.PaperTop, Deck.PaperBottom))
    onDrawBehind {
        softShadow(Offset.Zero, size, r, 5.dp.toPx(), 10.dp.toPx(), 0.35f)
        clipPath(shape) {
            drawRect(fill)
            if (ruled) {
                drawPath(lines, Deck.PaperLine, style = stroke)
                drawLine(Color(0x55D7372B), Offset(margin, 0f), Offset(margin, size.height), strokeWidth = stroke.width)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Printed legends
// ---------------------------------------------------------------------------------------------

/**
 * Silkscreen printed on the plastic. Light ink on the dark shell gets a dark edge above the
 * glyphs; dark ink on light plates gets a light edge below, so the print looks pressed in.
 * [logo] uses the wordmark face, otherwise IBM Plex Mono.
 */
@Composable
fun Engraved(
    text: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Deck.Silk,
    spacing: TextUnit = 0.14.em,
    logo: Boolean = false,
    weight: FontWeight = FontWeight.SemiBold,
    maxLines: Int = Int.MAX_VALUE,
) {
    val fonts = LocalDeckFonts.current
    val lightInk = color.luminance() > 0.45f
    val edge = if (LocalDeckAccessibility.current.highContrast) {
        null
    } else if (lightInk) {
        Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, -1.5f), 0f)
    } else {
        Shadow(Color.White.copy(alpha = 0.6f), Offset(0f, 1.5f), 0f)
    }
    Text(
        text = text,
        modifier = modifier,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Clip,
        softWrap = maxLines != 1,
        style = TextStyle(
            fontFamily = if (logo) fonts.logo else fonts.mono,
            fontWeight = if (logo) FontWeight.Normal else weight,
            fontSize = size,
            letterSpacing = spacing,
            lineHeight = size * 1.25f,
            shadow = edge,
        ),
    )
}

// ---------------------------------------------------------------------------------------------
// Hardware
// ---------------------------------------------------------------------------------------------

/** Slotted screw head, lit from the top left. */
@Composable
fun Screw(modifier: Modifier = Modifier, size: Dp = 12.dp, angle: Float = 35f) {
    Canvas(modifier.size(size)) { drawScrew(center, this.size.minDimension / 2f, angle) }
}

internal fun DrawScope.drawScrew(c: Offset, r: Float, angle: Float = 35f) {
    drawCircle(Color.Black.copy(alpha = 0.45f), r, Offset(c.x, c.y + r * 0.2f))
    drawCircle(
        Brush.radialGradient(
            0f to Color(0xFFF6F3EC),
            0.62f to Color(0xFFA6A196),
            1f to Color(0xFF6F6A60),
            center = Offset(c.x - r * 0.3f, c.y - r * 0.44f),
            radius = r * 1.9f,
        ),
        r,
        c,
    )
    rotate(angle, c) {
        val w = r * 0.27f
        drawLine(Color.White.copy(alpha = 0.4f), Offset(c.x - r * 0.66f, c.y + w), Offset(c.x + r * 0.66f, c.y + w), strokeWidth = w)
        drawLine(Color(0xFF4C473F), Offset(c.x - r * 0.66f, c.y), Offset(c.x + r * 0.66f, c.y), strokeWidth = w)
    }
}

/**
 * On/off blink in steps (an LED on a timer). Changes twice a period and only while [enabled];
 * read it in a draw or graphicsLayer lambda so nothing recomposes.
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

/** Darker shade of a lamp color for the edge of a lit jewel. */
private fun Color.deep(): Color = Color(red * 0.55f, green * 0.55f, blue * 0.55f, alpha)

/**
 * Jewel lamp in a ringed bezel. Off: a dark domed lens. On: white hot spot, colored lens and a
 * glow that fades in over 120 ms. [blinking] flashes it at 1 Hz in steps (REC only).
 */
@Composable
fun Lamp(
    lit: Boolean,
    modifier: Modifier = Modifier,
    litColor: Color = Deck.LampInput,
    diameter: Dp = 14.dp,
    blinking: Boolean = false,
) {
    val level = animateFloatAsState(if (lit) 1f else 0f, tween(120))
    val blink = rememberBlink(lit && blinking)
    val deep = remember(litColor) { litColor.deep() }
    Canvas(modifier.size(diameter)) {
        val on = level.value * (if (blinking && !blink.value) 0.15f else 1f)
        drawJewel(center, size.minDimension / 2f, litColor, deep, on)
    }
}

internal fun DrawScope.drawJewel(c: Offset, r: Float, color: Color, deep: Color, on: Float) {
    val ring = 1.dp.toPx()
    drawCircle(Color.White.copy(alpha = 0.45f), r + ring * 3f, c)
    drawCircle(Color.Black.copy(alpha = 0.40f), r + ring * 2f, c)
    if (on > 0f) {
        drawCircle(
            Brush.radialGradient(
                listOf(color.copy(alpha = 0.75f * on), color.copy(alpha = 0.25f * on), Color.Transparent),
                center = c,
                radius = r + 14.dp.toPx(),
            ),
            r + 14.dp.toPx(),
            c,
        )
    }
    val spot = Offset(c.x - r * 0.28f, c.y - r * 0.4f)
    drawCircle(
        Brush.radialGradient(0f to Color(0xFF8A8C91), 0.55f to Color(0xFF4A4C51), 1f to Color(0xFF2A2B2F), center = spot, radius = r * 1.5f),
        r,
        c,
    )
    if (on > 0f) {
        drawCircle(
            Brush.radialGradient(0f to Color.White, 0.38f to color, 1f to deep, center = spot, radius = r * 1.5f),
            r,
            c,
            alpha = on,
        )
    }
    drawCircle(Color.Black.copy(alpha = 0.35f), r, c, style = Stroke(ring))
}

// ---------------------------------------------------------------------------------------------
// Physical press
// ---------------------------------------------------------------------------------------------

/**
 * A physical press: the part travels down the instant a finger lands (60 ms), always reaches
 * the bottom even on a very quick tap, thunks with a haptic, and springs back with a small
 * overshoot. [travel] goes 0 (up) to 1 (down); read it in draw or graphicsLayer lambdas.
 */
@Composable
fun Modifier.physicalPress(
    travel: Animatable<Float, AnimationVector1D>,
    enabled: Boolean,
    label: String,
    onClick: () -> Unit,
    selected: Boolean? = null,
): Modifier {
    val haptics = rememberDeckHaptics()
    val scope = rememberCoroutineScope()
    val click by rememberUpdatedState(onClick)
    return this
        .semantics(mergeDescendants = true) {
            role = Role.Button
            contentDescription = label
            if (selected != null) this.selected = selected
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
                    val down = scope.launch { travel.animateTo(1f, tween(60, easing = LinearOutSlowInEasing)) }
                    val released = tryAwaitRelease()
                    down.join() // a quick tap still bottoms out, so it is seen and felt
                    if (released) haptics.release()
                    scope.launch { travel.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 1400f)) }
                },
                onTap = { click() },
            )
        }
}

/**
 * Chrome knob (back, search, settings, delete): a turned metal ring with a domed center.
 * Pressing scales it to 96 percent and tightens its shadow. 46 dp is the touch target.
 */
@Composable
fun RoundButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 46.dp,
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
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = 1f - 0.04f * travel.value
                    scaleX = s
                    scaleY = s
                },
        ) { drawChromeKnob(travel.value) }
        Box(
            Modifier.graphicsLayer {
                val s = 1f - 0.04f * travel.value
                scaleX = s
                scaleY = s
            },
        ) { content() }
    }
}

private val ChromeColors = listOf(Deck.ChromeA, Deck.ChromeB, Deck.ChromeC, Deck.ChromeD, Deck.ChromeA)

internal fun DrawScope.drawChromeKnob(pressed: Float) {
    val r = size.minDimension / 2f
    val c = center
    // Drop shadow tightens as the knob goes down.
    val dy = (3f - 2f * pressed) * density
    val blur = (6f - 4f * pressed) * density
    for (i in 4 downTo 1) {
        val grow = blur * i / 4f
        drawCircle(Color.Black.copy(alpha = 0.09f), r + grow / 2f, Offset(c.x, c.y + dy))
    }
    // Turned ring: the conic sweep starts at 210 degrees (CSS), which is 120 from 3 o'clock.
    rotate(120f, c) { drawCircle(Brush.sweepGradient(ChromeColors, c), r, c) }
    drawCircle(Color.Black.copy(alpha = 0.28f), r, c, style = Stroke(1.dp.toPx()))
    // Domed center.
    val dome = r * 35f / 46f
    drawCircle(
        Brush.radialGradient(
            listOf(Color.White, Color(0xFFBDB8AD)),
            center = Offset(c.x - dome * 0.3f, c.y - dome * 0.4f),
            radius = dome * 1.6f,
        ),
        dome,
        c,
    )
    drawCircle(Color.Black.copy(alpha = 0.18f), dome, c, style = Stroke(1.dp.toPx()))
}

/** Point on a circle, with 0 degrees pointing straight up and angles growing clockwise. */
internal fun polar(c: Offset, r: Float, degFromUp: Float): Offset {
    val a = (degFromUp - 90.0) * PI / 180.0
    return Offset(c.x + (cos(a) * r).toFloat(), c.y + (sin(a) * r).toFloat())
}
