package com.gogitit.rewinder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gogitit.rewinder.core.formatDuration
import com.gogitit.rewinder.data.Recent

/** Read-only recap. No input box: Rewinder is not a chat. */
@Composable
fun SummaryScreen(recent: Recent, onBack: () -> Unit, onDelete: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(RewinderColors.Background)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            CircleIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back", onBack)
            Spacer(Modifier.weight(1f))
            CircleIconButton(Icons.Default.Delete, "Delete this rewind", onDelete)
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(24.dp))
            SelectionContainer {
                Column {
                    Text(recent.title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${recent.startedAtMillis.toClockLabel()} to ${recent.endedAtMillis.toClockLabel()}" +
                            "  ·  ${formatDuration(recent.endedAtMillis - recent.startedAtMillis)}",
                        color = RewinderColors.TextSecondary,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(recent.gist, color = Color.White, fontSize = 19.sp, lineHeight = 27.sp)
                    Spacer(Modifier.height(12.dp))
                    recent.points.forEach { point ->
                        Row(Modifier.padding(vertical = 6.dp)) {
                            Text("•", color = RewinderColors.Accent, fontSize = 19.sp)
                            Spacer(Modifier.width(10.dp))
                            Text(point, color = Color.White.copy(alpha = 0.9f), fontSize = 17.sp, lineHeight = 24.sp)
                        }
                    }
                }
            }
            if (recent.redactedCount > 0) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = if (recent.redactedCount == 1) "1 sensitive detail withheld"
                    else "${recent.redactedCount} sensitive details withheld",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(RewinderColors.SurfaceHigh)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        Text(
            "AI summary. A paraphrase, not a record of what was said, and not evidence.",
            color = RewinderColors.TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        )
    }
}
