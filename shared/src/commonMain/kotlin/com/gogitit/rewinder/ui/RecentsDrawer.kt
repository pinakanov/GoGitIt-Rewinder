package com.gogitit.rewinder.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gogitit.rewinder.data.Recent

/** The tape shelf: each rewind is a cassette spine with a handwritten-style label, newest first. */
@Composable
fun RecentsDrawer(
    recents: List<Recent>,
    canRewind: Boolean,
    onSelect: (Long) -> Unit,
    onSettings: () -> Unit,
    onRewind: () -> Unit,
) {
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Deck.BodyMid, Deck.BodyShade)))
            .safeDrawingPadding()
            .padding(start = 18.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Engraved("TAPES", 26.sp, weight = FontWeight.Black, spacing = 3.sp)
                Engraved(
                    "${recents.size} REWIND${if (recents.size == 1) "" else "S"} ON THE SHELF",
                    10.sp,
                    color = Deck.EngravedSoft,
                    mono = true,
                    spacing = 1.sp,
                )
            }
            MetalIconButton(Icons.Default.Settings, "Settings", onSettings)
        }
        Spacer(Modifier.height(16.dp))

        if (searching) {
            SearchField(query, onValueChange = { query = it })
            Spacer(Modifier.height(14.dp))
        }

        val shown = if (query.isBlank()) recents else recents.filter { r ->
            r.title.contains(query, ignoreCase = true) ||
                r.gist.contains(query, ignoreCase = true) ||
                r.points.any { it.contains(query, ignoreCase = true) }
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            if (shown.isEmpty()) {
                item {
                    Text(
                        text = if (recents.isEmpty()) {
                            "The shelf is empty. Press REC, and once 3 minutes are on tape, press REWIND."
                        } else {
                            "No tape matches \"$query\"."
                        },
                        color = Deck.Engraved,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        modifier = Modifier.padding(top = 12.dp, end = 12.dp),
                    )
                }
            }
            items(shown, key = { it.id }) { recent ->
                TapeSpine(recent, onClick = { onSelect(recent.id) })
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MetalIconButton(
                icon = Icons.Default.Search,
                contentDescription = "Search rewinds",
                onClick = {
                    searching = !searching
                    if (!searching) query = ""
                },
                diameter = 50.dp,
            )
            Spacer(Modifier.weight(1f))
            Box(Modifier.width(150.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black).padding(5.dp)) {
                DeckKey(KeyGlyph.Rewind, "REWIND", onClick = onRewind, enabled = canRewind, height = 70.dp, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** One cassette seen edge-on: dark shell, cream label with a stripe, title and time range. */
@Composable
private fun TapeSpine(recent: Recent, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .shadow(4.dp, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(Deck.Shell)
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(3.dp))
                .background(Deck.Label),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.width(10.dp).height(52.dp)) {
                drawRect(Deck.StripeOrange, Offset.Zero, Size(size.width / 2f, size.height))
                drawRect(Deck.StripeMustard, Offset(size.width / 2f, 0f), Size(size.width / 2f, size.height))
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(
                    recent.title,
                    color = Deck.Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    recent.gist,
                    color = Deck.InkSoft,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(Modifier.padding(end = 10.dp), horizontalAlignment = Alignment.End) {
                Text(recent.startedAtMillis.toClockLabel(), color = Deck.InkSoft, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text(recent.endedAtMillis.toClockLabel(), color = Deck.InkSoft, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
        // the shell's little window notch
        Box(Modifier.padding(start = 7.dp).width(6.dp).height(30.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF1B1918)))
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = Deck.Ink, fontSize = 17.sp),
        cursorBrush = SolidColor(Deck.Ink),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Deck.Label)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text("Search tapes", color = Deck.InkSoft, fontSize = 17.sp)
                }
                inner()
            }
        },
    )
}
