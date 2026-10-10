package com.gogitit.rewinder.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gogitit.rewinder.core.formatDuration
import com.gogitit.rewinder.data.Recent

/** Stripe pairs for cassette labels, cycling by tape number. */
private val StripePairs = listOf(
    Color(0xFFE5592B) to Color(0xFFF3A63D), // orange / amber
    Color(0xFF2E7F8C) to Color(0xFF7CC4C9), // teal / pale teal
    Color(0xFFC23B4E) to Color(0xFFE98B96), // crimson / pink
    Color(0xFF5F7A2E) to Color(0xFFC6B23C), // olive / mustard
    Color(0xFF3E55A6) to Color(0xFF8FA6E0), // blue / pale blue
)

/**
 * The tape shelf: every rewind is a cassette in a dark rack, newest first, with its number,
 * a stripe color, a handwritten title, its time range and length. Five slots fill the rack;
 * longer lists scroll inside the bezel with the next tape peeking. DECK goes back to the
 * recorder; search and Settings sit under the rack.
 */
@Composable
fun TapesScreen(
    recents: List<Recent>,
    onBack: () -> Unit,
    onSelect: (Long) -> Unit,
    onSettings: () -> Unit,
) {
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val shown = remember(recents, query) {
        if (query.isBlank()) {
            recents
        } else {
            recents.filter { r ->
                r.title.contains(query, ignoreCase = true) ||
                    r.gist.contains(query, ignoreCase = true) ||
                    r.points.any { it.contains(query, ignoreCase = true) }
            }
        }
    }

    Box(Modifier.fillMaxSize().deckBody()) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 6.dp),
        ) {
            PrintedScale {
                Row(Modifier.fillMaxWidth().height(54.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Engraved("TAPES", 33.sp, logo = true, spacing = 0.09.em, maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                        Engraved("TAPE SHELF · AUTO MEMORY", 9.5.sp, color = silkSoft(), spacing = 0.13.em, maxLines = 1)
                    }
                    Spacer(Modifier.width(10.dp))
                    DeckKey(
                        KeyGlyph.Back, "DECK",
                        onClick = onBack,
                        modifier = Modifier.width(84.dp),
                        height = 54.dp,
                        horizontal = true,
                        a11yLabel = "Back to deck",
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            if (searching) {
                SearchStrip(query, onValueChange = { query = it })
                Spacer(Modifier.height(10.dp))
            }

            PrintedScale {
                TapeRack(
                    tapes = shown,
                    shelfEmpty = recents.isEmpty(),
                    query = query,
                    onSelect = onSelect,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(12.dp))
            PrintedScale {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RoundButton(
                        onClick = {
                            searching = !searching
                            if (!searching) query = ""
                        },
                        label = if (searching) "Close search" else "Search tapes",
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Deck.KnobGlyph, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Engraved(if (searching) "CLOSE" else "SEARCH", 10.sp, color = silkSoft(), maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    Engraved("SETTINGS", 10.sp, color = silkSoft(), maxLines = 1)
                    Spacer(Modifier.width(10.dp))
                    RoundButton(onClick = onSettings, label = "Settings") {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = Deck.KnobGlyph, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TapeRack(
    tapes: List<Recent>,
    shelfEmpty: Boolean,
    query: String,
    onSelect: (Long) -> Unit,
    modifier: Modifier,
) {
    BoxWithConstraints(
        modifier
            .bezel(22.dp, dropY = 11.dp, dropBlur = 20.dp, dropAlpha = 0.3f, well = true)
            .padding(10.dp),
    ) {
        val gap = 9.dp
        // Five slots fill the rack; with more tapes, a sixth peeks in to show it scrolls.
        val slots = if (tapes.size > 5) 5.4f else 5f
        val rowHeight = ((maxHeight - gap * 4) / slots).coerceAtLeast(92.dp)
        if (tapes.isEmpty()) {
            EmptyShelf(shelfEmpty, query)
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                items(tapes, key = { it.id }) { tape -> TapeSlot(tape, rowHeight, onSelect) }
            }
        }
    }
}

@Composable
private fun TapeSlot(tape: Recent, height: Dp, onSelect: (Long) -> Unit) {
    val fonts = LocalDeckFonts.current
    val haptics = rememberDeckHaptics()
    val stripes = StripePairs[((tape.id - 1L).mod(StripePairs.size.toLong())).toInt()]
    val number = "C-${tape.id}"
    val range = clockRange(tape.startedAtMillis, tape.endedAtMillis)
    val length = formatDuration(tape.endedAtMillis - tape.startedAtMillis)
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .slotBody()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClickLabel = "Open recap") {
                haptics.tick()
                onSelect(tape.id)
            }
            .semantics(mergeDescendants = true) { contentDescription = "Tape $number, ${tape.title}, $range, $length" }
            .padding(start = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CassetteThumb(number, stripes, Modifier.size(128.dp, 80.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                tape.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = Deck.HandInk, fontFamily = fonts.hand, fontWeight = FontWeight.SemiBold, fontSize = 25.sp, lineHeight = 27.sp),
            )
            Spacer(Modifier.height(6.dp))
            Text(range, maxLines = 1, overflow = TextOverflow.Ellipsis, style = slotMeta(fonts))
            Spacer(Modifier.height(3.dp))
            Text("$length · SIDE A", maxLines = 1, overflow = TextOverflow.Ellipsis, style = slotMeta(fonts))
        }
    }
}

private fun slotMeta(fonts: DeckFonts) = TextStyle(
    color = Deck.SlotMeta,
    fontFamily = fonts.mono,
    fontWeight = FontWeight.Medium,
    fontSize = 10.5.sp,
    letterSpacing = 0.1.em,
)

/** A slot in the rack: dark molded plastic, lit top edge, shadow below. */
private fun Modifier.slotBody(): Modifier = this.drawWithCache {
    val r = 14.dp.toPx()
    val path = roundRectPath(Offset.Zero, size, r)
    val fill = Brush.verticalGradient(listOf(Deck.SlotTop, Deck.SlotBottom))
    val px = 1.dp.toPx()
    onDrawBehind {
        softShadow(Offset.Zero, size, r, 3.dp.toPx(), 6.dp.toPx(), 0.5f)
        drawRoundRect(Color.Black, Offset(0f, px), size, CornerRadius(r))
        drawPath(path, fill)
        clipPath(path) { drawRect(Color.White.copy(alpha = 0.1f), Offset.Zero, Size(size.width, px)) }
    }
}

private class ThumbLabel(val text: TextLayoutResult)

/** A 128 x 80 cassette: cream body, smoked window with two hubs, two stripes, head opening, glare. */
@Composable
private fun CassetteThumb(number: String, stripes: Pair<Color, Color>, modifier: Modifier) {
    val fonts = LocalDeckFonts.current
    val measurer = rememberTextMeasurer()
    val reflections = !LocalDeckAccessibility.current.highContrast
    Spacer(
        modifier.drawWithCache {
            val u = size.width / 128f
            val label = ThumbLabel(
                measurer.measure(
                    "A  $number",
                    TextStyle(
                        color = Deck.CassetteInk,
                        fontFamily = fonts.mono,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = (10f * u).toSp(),
                        letterSpacing = 0.08.em,
                    ),
                ),
            )
            onDrawBehind { drawThumb(u, label, stripes, reflections) }
        },
    )
}

private fun DrawScope.drawThumb(u: Float, label: ThumbLabel, stripes: Pair<Color, Color>, reflections: Boolean) {
    val body = roundRectPath(Offset.Zero, size, 7f * u)
    softShadow(Offset.Zero, size, 7f * u, 4f * u, 6f * u, 0.65f)
    drawPath(body, Brush.verticalGradient(listOf(Deck.PaperTop, Deck.PaperBottom)))
    clipPath(body) {
        drawText(label.text, topLeft = Offset(10f * u, 3f * u))

        val winTL = Offset(12f * u, 16f * u)
        val winSize = Size(104f * u, 26f * u)
        val window = roundRectPath(winTL, winSize, 13f * u)
        drawPath(window, Color(0xFF15110E))
        listOf(Offset(25f * u, 29f * u), Offset(103f * u, 29f * u)).forEach { c ->
            drawCircle(Deck.Hub, 8f * u, c)
            drawCircle(Deck.HubCore, 4f * u, c)
        }
        clipPath(window) {
            drawRect(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), startY = winTL.y, endY = winTL.y + 13f * u),
                winTL,
                Size(winSize.width, 13f * u),
            )
        }
        drawPath(window, Color.Black.copy(alpha = 0.5f), style = Stroke(u))

        drawRect(stripes.first, Offset(0f, 46f * u), Size(size.width, 9f * u))
        drawRect(stripes.second, Offset(0f, 55f * u), Size(size.width, 9f * u))

        val x0 = 18f * u
        val w = 92f * u
        val top = 65f * u
        val head = Path().apply {
            moveTo(x0 + w * 0.07f, top)
            lineTo(x0 + w * 0.93f, top)
            lineTo(x0 + w, size.height)
            lineTo(x0, size.height)
            close()
        }
        drawPath(head, Brush.verticalGradient(listOf(Color(0xFF3A3530), Color(0xFF211E1B)), startY = top, endY = size.height))

        drawRect(Color.White.copy(alpha = 0.8f), Offset.Zero, Size(size.width, u))
        if (reflections) {
            drawRect(
                cssLinear(
                    115f, Offset.Zero, size,
                    0f to Color.Transparent,
                    0.30f to Color.Transparent,
                    0.36f to Color.White.copy(alpha = 0.2f),
                    0.44f to Color.Transparent,
                    1f to Color.Transparent,
                ),
            )
        }
    }
}

/** An empty rack: one cassette outline and a printed hint. */
@Composable
private fun EmptyShelf(shelfEmpty: Boolean, query: String) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Canvas(Modifier.size(128.dp, 80.dp)) {
            val line = Color.White.copy(alpha = 0.35f)
            val stroke = Stroke(1.5.dp.toPx())
            val u = size.width / 128f
            drawRoundRect(line, Offset.Zero, size, CornerRadius(7f * u), style = stroke)
            drawRoundRect(line, Offset(12f * u, 16f * u), Size(104f * u, 26f * u), CornerRadius(13f * u), style = stroke)
            drawCircle(line, 8f * u, Offset(25f * u, 29f * u), style = stroke)
            drawCircle(line, 8f * u, Offset(103f * u, 29f * u), style = stroke)
        }
        Spacer(Modifier.height(16.dp))
        Engraved(
            if (shelfEmpty) "NO TAPES YET · PRESS REC" else "NO TAPE MATCHES",
            10.5.sp,
            color = silkSoft(),
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (shelfEmpty) "Once 3 minutes are on tape, press REWIND on the deck." else "\"$query\"",
            color = Deck.SilkSoft,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

/** A paper label strip to write a search on. */
@Composable
private fun SearchStrip(value: String, onValueChange: (String) -> Unit) {
    val fonts = LocalDeckFonts.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = Deck.Ink, fontFamily = fonts.mono, fontWeight = FontWeight.Medium, fontSize = 16.sp),
        cursorBrush = SolidColor(Deck.Ink),
        modifier = Modifier
            .fillMaxWidth()
            .paper(radius = 8.dp, ruled = false)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text("Search tapes", color = Deck.InkSoft, fontSize = 16.sp, style = TextStyle(fontFamily = fonts.mono, fontWeight = FontWeight.Medium))
                }
                inner()
            }
        },
    )
}
