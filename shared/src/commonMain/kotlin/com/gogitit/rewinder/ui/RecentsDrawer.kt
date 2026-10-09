package com.gogitit.rewinder.ui

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
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gogitit.rewinder.data.Recent

/** The "chats" list: each rewind is one entry, newest first. */
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
            .background(RewinderColors.Background)
            .safeDrawingPadding()
            .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Rewinder", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            CircleIconButton(Icons.Default.Settings, "Privacy and settings", onSettings)
        }
        Spacer(Modifier.height(20.dp))

        if (searching) {
            SearchField(query, onValueChange = { query = it })
            Spacer(Modifier.height(16.dp))
        }

        Text("Recents", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))

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
                            "No rewinds yet. Start Rewinder, and once 3 minutes are in memory, tap Rewind."
                        } else {
                            "Nothing matches \"$query\"."
                        },
                        color = RewinderColors.TextSecondary,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        modifier = Modifier.padding(top = 12.dp, end = 12.dp),
                    )
                }
            }
            items(shown, key = { it.id }) { recent ->
                RecentRow(recent, onClick = { onSelect(recent.id) })
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(
                icon = Icons.Default.Search,
                contentDescription = "Search rewinds",
                onClick = {
                    searching = !searching
                    if (!searching) query = ""
                },
                diameter = 52.dp,
            )
            Spacer(Modifier.weight(1f))
            PillButton("Rewind", Icons.Default.Refresh, onRewind, enabled = canRewind)
        }
    }
}

@Composable
private fun RecentRow(recent: Recent, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                recent.title,
                color = Color.White,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                recent.gist,
                color = RewinderColors.TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(recent.startedAtMillis.toClockLabel(), color = RewinderColors.TextSecondary, fontSize = 12.sp)
            Text(recent.endedAtMillis.toClockLabel(), color = RewinderColors.TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
        cursorBrush = SolidColor(Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RewinderColors.SurfaceHigh)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text("Search rewinds", color = RewinderColors.TextSecondary, fontSize = 17.sp)
                }
                inner()
            }
        },
    )
}
