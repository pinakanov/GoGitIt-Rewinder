package com.gogitit.rewinder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gogitit.rewinder.RewinderState
import com.gogitit.rewinder.core.formatDuration
import com.gogitit.rewinder.models.ModelsState
import com.gogitit.rewinder.pipeline.RewindStage

/**
 * Stopwatch-style home. Live clock where the stopwatch shows elapsed time,
 * Rewind in the "Lap" slot, Start/Stop in the "Stop" slot.
 */
@Composable
fun HomeScreen(
    state: RewinderState,
    onMenu: () -> Unit,
    onToggle: () -> Unit,
    onRewind: () -> Unit,
    onDismissError: () -> Unit,
    models: ModelsState? = null,
    modelsDescription: String = "",
    onDownloadModels: () -> Unit = {},
    onCancelDownload: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize().background(HomeGradient)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(Icons.Default.Menu, "Open recents", onMenu, glass = true)
                Spacer(Modifier.weight(1f))
                if (state.isRunning) ListeningChip()
            }

            state.error?.let { ErrorBanner(it, onDismissError) }

            Spacer(Modifier.weight(1f))
            LiveClock()
            Spacer(Modifier.height(36.dp))
            Text("Rewinder", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                text = statusLine(state),
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 15.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Waveform(
                levels = if (state.isRunning) state.levels else emptyList(),
                modifier = Modifier.fillMaxWidth(0.8f).height(44.dp),
                color = Color.White.copy(alpha = if (state.isRunning) 0.85f else 0.3f),
            )
            if (models != null && !models.ready) {
                Spacer(Modifier.height(20.dp))
                ModelCard(models, modelsDescription, onDownloadModels, onCancelDownload)
            }
            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StopwatchButton(
                    label = "Rewind",
                    background = RewinderColors.SurfaceHigh,
                    contentColor = Color.White,
                    onClick = onRewind,
                    enabled = state.canRewind,
                    progress = if (state.isRunning && !state.canRewind) {
                        state.unlockProgress
                    } else {
                        null
                    },
                )
                Text(
                    text = rewindCaption(state),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                StopwatchButton(
                    label = if (state.isRunning) "Stop" else "Start",
                    background = if (state.isRunning) RewinderColors.StopTint else RewinderColors.StartTint,
                    contentColor = if (state.isRunning) RewinderColors.StopText else RewinderColors.StartText,
                    onClick = onToggle,
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun LiveClock() {
    val now by rememberLocalNow()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${now.hour.pad2()}:${now.minute.pad2()}",
                color = Color.White,
                fontSize = 96.sp,
                fontWeight = FontWeight.Thin,
                letterSpacing = (-2).sp,
            )
            Text(
                text = ":${now.second.pad2()}",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 34.sp,
                fontWeight = FontWeight.Thin,
                modifier = Modifier.padding(bottom = 18.dp),
            )
        }
        Text(now.dateLabel(), color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
    }
}

@Composable
private fun ListeningChip() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(RewinderColors.StopText))
        Spacer(Modifier.width(8.dp))
        Text("Listening", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xCC3A1310))
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

private fun statusLine(state: RewinderState): String = when {
    !state.isRunning -> "Keeps a rolling 5 minute memory while on.\nOnly the summary is ever saved."
    state.stage == RewindStage.Transcribing -> "Transcribing what you just heard..."
    state.isSummarizing -> "Summarizing what you just heard..."
    state.canRewind -> "${formatDuration(state.bufferedMillis)} in memory. Rewind is ready."
    else -> "Listening. ${formatDuration(state.bufferedMillis)} in memory."
}

private fun rewindCaption(state: RewinderState): String = when {
    !state.isRunning -> ""
    state.isSummarizing -> {
        val doing = if (state.stage == RewindStage.Transcribing) "Transcribing" else "Summarizing"
        if (state.pendingRewinds > 1) "$doing\n+${state.pendingRewinds - 1} queued" else doing
    }
    state.canRewind -> "Ready"
    else -> "Unlocks in\n${formatDuration(state.millisUntilUnlock)}"
}

/** Shown until the on-device models are downloaded. */
@Composable
private fun ModelCard(
    models: ModelsState,
    description: String,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(16.dp),
    ) {
        Text("On-device AI not installed", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Download $description once, over Wi-Fi. After that, transcription and " +
                "summaries run fully offline on this phone.",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(12.dp))
        when {
            models.downloading -> {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.18f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(models.progress)
                            .height(6.dp)
                            .background(RewinderColors.Accent),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${models.current ?: "Models"}  ·  ${(models.progress * 100).toInt()}%  ·  " +
                            "${megabytes(models.downloadedBytes)} of ${megabytes(models.totalBytes)}",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Cancel",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onCancel)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            else -> {
                models.error?.let {
                    Text(it, color = RewinderColors.StopText, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    text = if (models.error != null || models.downloadedBytes > 0) "Resume download" else "Download",
                    color = Color.Black,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .clickable(onClick = onDownload)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
    }
}

private fun megabytes(bytes: Long): String {
    val mb = bytes / 1_000_000L
    return if (mb >= 1000) "${mb / 1000}.${(mb % 1000) / 100} GB" else "$mb MB"
}
