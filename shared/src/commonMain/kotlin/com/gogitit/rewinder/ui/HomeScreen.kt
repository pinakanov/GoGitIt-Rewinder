package com.gogitit.rewinder.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gogitit.rewinder.RewinderState
import com.gogitit.rewinder.core.RewindPolicy
import com.gogitit.rewinder.core.formatDuration
import com.gogitit.rewinder.models.ModelsState
import com.gogitit.rewinder.pipeline.RewindStage

/**
 * Home is the RW-5 cassette-corder: wordmark and REC lamp, the LCD, the tape bay, the VU meter
 * with its lamps, the speaker grille (which absorbs spare height) and the piano keys in the
 * thumb zone. Everything is driven by [RewinderState]; nothing here changes recording logic.
 */
@Composable
fun HomeScreen(
    state: RewinderState,
    onTapes: () -> Unit,
    onToggle: () -> Unit,
    onRewind: () -> Unit,
    onDismissError: () -> Unit,
    models: ModelsState? = null,
    modelsDescription: String = "",
    onDownloadModels: () -> Unit = {},
    onCancelDownload: () -> Unit = {},
) {
    val running = state.isRunning
    val motion = when {
        state.isSummarizing -> ReelMotion.Rewind
        running -> ReelMotion.Forward
        else -> ReelMotion.Stopped
    }
    // Whole seconds, so the counter, tape bar and packs change once a second.
    val bufferedSeconds = state.bufferedMillis / 1000L
    val position = bufferedSeconds * 1000f / RewindPolicy.WINDOW_MILLIS
    val counter = formatDuration(bufferedSeconds * 1000L)
    val status = lcdStatus(state)
    val mode = when {
        state.isSummarizing -> "REW"
        running -> "REC"
        else -> "STOP"
    }
    val largeText = LocalDeckAccessibility.current.largeText

    Box(Modifier.fillMaxSize().deckBody()) {
        PrintedScale {
            Column(
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 6.dp),
            ) {
                DeckHeader(recording = running)
                Spacer(Modifier.height(12.dp))

                // Scrolls on short phones; on tall ones the grille takes the spare height.
                BoxWithConstraints(Modifier.weight(1f)) {
                    val viewport = maxHeight
                    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        FillLast(minLast = 60.dp, viewport = viewport) {
                            DeckLcd(
                                mode = mode,
                                recording = running,
                                counter = counter,
                                position = position,
                                status = status,
                                source = if (running) state.inputLabel else null,
                                description = lcdDescription(state, bufferedSeconds, status),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (largeText) {
                                Spacer(Modifier.height(8.dp))
                                LargeStatus(status)
                            }
                            Spacer(Modifier.height(12.dp))
                            CassetteBay(position = position, motion = motion, sheen = running, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(12.dp))
                            MeterRow(
                                level = if (running) state.level else 0f,
                                input = running && (state.inputDb ?: -120) > -60,
                                ready = state.canRewind,
                                ai = models?.ready ?: true,
                            )
                            state.error?.let {
                                Spacer(Modifier.height(12.dp))
                                Sticker(it, onDismiss = onDismissError)
                            }
                            if (running) {
                                state.inputWarning?.let {
                                    Spacer(Modifier.height(12.dp))
                                    Sticker(it, onDismiss = null)
                                }
                            }
                            if (models != null && !models.ready) {
                                Spacer(Modifier.height(12.dp))
                                ModelDownloadCard(models, modelsDescription, onDownloadModels, onCancelDownload)
                            }
                            Spacer(Modifier.height(12.dp))
                            // Last child: takes whatever height is left (at least 60 dp).
                            Column {
                                SpeakerGrille(Modifier.fillMaxWidth().weight(1f))
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                    }
                }

                TransportRail {
                    DeckKey(KeyGlyph.Tapes, "TAPES", onClick = onTapes, modifier = Modifier.weight(1f), a11yLabel = "Tapes")
                    DeckKey(
                        KeyGlyph.Rec, "REC",
                        onClick = { if (!running) onToggle() },
                        modifier = Modifier.weight(1f),
                        latched = running,
                        a11yLabel = if (running) "Recording" else "Record",
                    )
                    val unlockSeconds = if (running && !state.canRewind) (state.millisUntilUnlock + 999) / 1000 else null
                    DeckKey(
                        KeyGlyph.Rewind,
                        if (unlockSeconds != null) formatDuration(unlockSeconds * 1000L) else "REWIND",
                        onClick = onRewind,
                        modifier = Modifier.weight(1f),
                        enabled = state.canRewind,
                        progress = unlockSeconds?.let { 1f - it * 1000f / RewindPolicy.MIN_REWIND_MILLIS },
                        a11yLabel = if (unlockSeconds != null) "Rewind, available in ${spokenDuration(unlockSeconds)}" else "Rewind",
                    )
                    DeckKey(
                        KeyGlyph.Stop, "STOP",
                        onClick = { if (running) onToggle() },
                        modifier = Modifier.weight(1f),
                        a11yLabel = "Stop",
                    )
                }
            }
        }
    }
}

/**
 * Lays its children out top to bottom at their natural heights and gives the last child the
 * rest of [viewport] (never less than [minLast]), so the grille grows on tall screens and the
 * page scrolls on short ones.
 */
@Composable
private fun FillLast(minLast: Dp, viewport: Dp, content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        val width = constraints.maxWidth
        val fixed = measurables.dropLast(1).map { it.measure(Constraints(minWidth = width, maxWidth = width)) }
        val used = fixed.sumOf { it.height }
        val lastHeight = maxOf(minLast.roundToPx(), viewport.roundToPx() - used)
        val last = measurables.lastOrNull()?.measure(Constraints.fixed(width, lastHeight))
        layout(width, used + (last?.height ?: 0)) {
            var y = 0
            fixed.forEach {
                it.place(0, y)
                y += it.height
            }
            last?.place(0, y)
        }
    }
}

/** Wordmark with a screw beside it, the model line, and the REC lamp. */
@Composable
private fun DeckHeader(recording: Boolean) {
    Row(Modifier.fillMaxWidth().height(54.dp), verticalAlignment = Alignment.CenterVertically) {
        Screw(size = 11.dp)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Engraved("REWINDER", 33.sp, logo = true, spacing = 0.09.em, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Engraved("CASSETTE-CORDER RW-5 · AUTO MEMORY", 9.5.sp, color = silkSoft(), spacing = 0.13.em, maxLines = 1)
        }
        Spacer(Modifier.width(12.dp))
        Column(
            Modifier.semantics(mergeDescendants = true) { stateDescription = if (recording) "lit" else "off" },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Lamp(lit = recording, litColor = Deck.RecGlow, diameter = 14.dp, blinking = true)
            Spacer(Modifier.height(6.dp))
            Engraved("REC", 9.5.sp, spacing = 0.1.em, maxLines = 1)
        }
    }
}

/** The LCD status, large and plain, for large accessibility text sizes. */
@Composable
private fun LargeStatus(status: String) {
    Text(
        status,
        color = Deck.Ink,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Deck.Label)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/** A yellow warning sticker slapped on the body. */
@Composable
private fun Sticker(message: String, onDismiss: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .shadow(3.dp, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFF6D44A))
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HazardStripe(Modifier.width(14.dp).fillMaxHeight())
        Spacer(Modifier.width(10.dp))
        Text(
            message,
            color = Deck.Ink,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
        )
        if (onDismiss != null) {
            RoundButton(onClick = onDismiss, diameter = 44.dp, label = "Dismiss") {
                Icon(Icons.Default.Close, contentDescription = null, tint = Deck.KnobGlyph, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun HazardStripe(modifier: Modifier) {
    Canvas(modifier.background(Deck.Ink)) {
        val step = 10.dp.toPx()
        var y = -size.width
        while (y < size.height) {
            drawLine(Color(0xFFF6D44A), Offset(0f, y + size.width), Offset(size.width, y), strokeWidth = 4.dp.toPx())
            y += step
        }
    }
}

private fun lcdStatus(state: RewinderState): String = when {
    !state.isRunning -> "PRESS REC TO START"
    state.isSummarizing -> {
        val doing = if (state.stage == RewindStage.Transcribing) "TRANSCRIBING" else "SUMMARIZING"
        if (state.pendingRewinds > 1) "$doing +${state.pendingRewinds - 1}" else doing
    }
    state.canRewind -> "REWIND READY"
    else -> "REWIND IN ${formatDuration(state.millisUntilUnlock)}"
}

/** The LCD read as one sentence, for VoiceOver and TalkBack. */
private fun lcdDescription(state: RewinderState, bufferedSeconds: Long, status: String): String {
    val mode = when {
        state.isSummarizing -> "Rewinding"
        state.isRunning -> "Recording"
        else -> "Stopped"
    }
    val memory = "${spokenDuration(bufferedSeconds)} in memory"
    val detail = status.lowercase().replaceFirstChar { it.uppercase() }
    return "$mode, $memory. $detail."
}

private fun spokenDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    val minutes = if (m == 1L) "1 minute" else "$m minutes"
    val secs = if (s == 1L) "1 second" else "$s seconds"
    return if (m == 0L) secs else "$minutes $secs"
}
