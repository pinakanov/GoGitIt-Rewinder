package com.gogitit.rewinder.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gogitit.rewinder.RewinderState
import com.gogitit.rewinder.core.RewindPolicy
import com.gogitit.rewinder.core.formatDuration
import com.gogitit.rewinder.models.ModelsState
import com.gogitit.rewinder.pipeline.RewindStage

/**
 * Home is a portable cassette recorder: engraved name plate, an LCD with the tape counter and
 * clock, the cassette behind a smoked window (reels turn while recording and rewind fast while
 * a summary is being made), a VU meter, and four piano keys.
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
    val reelSpeed = when {
        state.isSummarizing -> -720f
        state.isRunning -> 90f
        else -> 0f
    }
    // Quantized to whole seconds so the cassette only redraws its tape packs once a second.
    val bufferedSeconds = state.bufferedMillis / 1000L
    val fill = bufferedSeconds * 1000f / RewindPolicy.WINDOW_MILLIS

    Box(Modifier.fillMaxSize().deckBody()) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            NamePlate(recording = state.isRunning)
            Spacer(Modifier.height(12.dp))

            // Content scrolls on short phones; on tall ones the speaker grille fills the gap.
            BoxWithConstraints(Modifier.weight(1f)) {
                val viewport = maxHeight
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Column(Modifier.heightIn(min = viewport)) {
                        DeckLcd(
                            running = state.isRunning,
                            mode = when {
                                state.isSummarizing -> "REW"
                                state.isRunning -> "REC"
                                else -> "STOP"
                            },
                            bufferedSeconds = bufferedSeconds,
                            status = lcdStatus(state),
                            db = if (state.isRunning) (state.inputDb ?: -120) / 2 * 2 else null,
                            source = if (state.isRunning) state.inputLabel else null,
                        )
                        Spacer(Modifier.height(14.dp))
                        CassetteWindow(
                            reelSpeedDegPerSec = reelSpeed,
                            fill = fill,
                            label = "REWINDER  C-5",
                            modifier = Modifier.fillMaxWidth().aspectRatio(1.62f),
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth().height(92.dp), verticalAlignment = Alignment.CenterVertically) {
                            VuMeter(
                                level = if (state.isRunning) state.level else 0f,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                            Spacer(Modifier.width(12.dp))
                            LampPanel(
                                input = state.isRunning && (state.inputDb ?: -120) > -60,
                                ready = state.canRewind,
                                ai = models?.ready ?: true,
                                modifier = Modifier.fillMaxHeight(),
                            )
                        }
                        state.error?.let {
                            Spacer(Modifier.height(12.dp))
                            Sticker(it, onDismiss = onDismissError)
                        }
                        if (state.isRunning) {
                            state.inputWarning?.let {
                                Spacer(Modifier.height(12.dp))
                                Sticker(it, onDismiss = null)
                            }
                        }
                        if (models != null && !models.ready) {
                            Spacer(Modifier.height(14.dp))
                            ModelDownloadCard(models, modelsDescription, onDownloadModels, onCancelDownload)
                        }
                        Spacer(Modifier.height(12.dp))
                        Spacer(Modifier.weight(1f))
                        SpeakerGrille(Modifier.fillMaxWidth().height(64.dp))
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }

            TransportKeys(
                running = state.isRunning,
                canRewind = state.canRewind,
                unlockSeconds = if (state.isRunning && !state.canRewind) (state.millisUntilUnlock + 999) / 1000 else null,
                onMenu = onMenu,
                onToggle = onToggle,
                onRewind = onRewind,
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun NamePlate(recording: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Screw()
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Engraved("REWINDER", 30.sp, weight = FontWeight.Black, spacing = 3.sp)
            Engraved("CASSETTE-CORDER  RW-5  ·  AUTO MEMORY", 9.sp, weight = FontWeight.Bold, color = Deck.EngravedSoft, spacing = 1.sp, mono = true)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RecLamp(on = recording)
            Engraved("REC", 8.sp, color = Deck.EngravedSoft, mono = true)
        }
        Spacer(Modifier.width(10.dp))
        Screw(angle = 70f)
    }
}

@Composable
private fun DeckLcd(
    running: Boolean,
    mode: String,
    bufferedSeconds: Long,
    status: String,
    db: Int?,
    source: String?,
) {
    val now by rememberLocalNow()
    val blink = rememberBlink(running)
    LcdPanel(Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                // Blink is applied in the layer, so the LCD does not recompose twice a second.
                LcdText(mode, 13.sp, Modifier.graphicsLayer { alpha = if (blink.value) 1f else 0.15f }.padding(bottom = 6.dp))
                Spacer(Modifier.width(10.dp))
                LcdText(formatDuration(bufferedSeconds * 1000L), 34.sp, weight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    LcdText("${now.hour.pad2()}:${now.minute.pad2()}", 16.sp)
                    LcdText(now.dateLabel().uppercase(), 9.sp, weight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(4.dp))
            TapeSegments(bufferedSeconds, Modifier.fillMaxWidth().height(8.dp))
            Spacer(Modifier.height(5.dp))
            Row {
                LcdText(status, 11.sp, Modifier.weight(1f))
                if (db != null) LcdText("$db dB", 11.sp, weight = FontWeight.Medium)
            }
            if (source != null) {
                Spacer(Modifier.height(2.dp))
                LcdText(source.uppercase(), 9.sp, weight = FontWeight.Medium)
            }
        }
    }
}

/** 5:00 of tape as 30 LCD segments, with a notch at the 3:00 rewind point. */
@Composable
private fun TapeSegments(bufferedSeconds: Long, modifier: Modifier) {
    Canvas(modifier) {
        val count = 30
        val gap = 2.dp.toPx()
        val segW = (size.width - gap * (count - 1)) / count
        val filled = (bufferedSeconds * 1000f / RewindPolicy.WINDOW_MILLIS * count).toInt()
        val notch = (RewindPolicy.MIN_REWIND_MILLIS.toFloat() / RewindPolicy.WINDOW_MILLIS * count).toInt()
        for (i in 0 until count) {
            val x = i * (segW + gap)
            val on = i < filled
            drawRoundRect(
                if (on) Deck.LcdInk else Deck.LcdGhost,
                Offset(x, if (i == notch) 0f else size.height * 0.2f),
                Size(segW, if (i == notch) size.height else size.height * 0.6f),
                CornerRadius(1f),
            )
        }
    }
}

@Composable
private fun LampPanel(input: Boolean, ready: Boolean, ai: Boolean, modifier: Modifier) {
    Column(
        modifier.insetPlate(10.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        LampRow("INPUT", input)
        LampRow("READY", ready)
        LampRow("AI", ai, litColor = Color(0xFF7BD389))
    }
}

@Composable
private fun LampRow(label: String, lit: Boolean, litColor: Color = Deck.Amber) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Lamp(lit, litColor = litColor, diameter = 11.dp)
        Spacer(Modifier.width(7.dp))
        Engraved(label, 9.sp, color = Deck.EngravedSoft, mono = true, spacing = 1.sp)
    }
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
            .padding(start = 0.dp, end = 4.dp),
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
            RoundButton(onClick = onDismiss, diameter = 34.dp) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Deck.Engraved, modifier = Modifier.size(16.dp))
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

/** Piano keys sitting in a dark well: TAPES, REC (latches), REWIND, STOP. */
@Composable
private fun TransportKeys(
    running: Boolean,
    canRewind: Boolean,
    unlockSeconds: Long?,
    onMenu: () -> Unit,
    onToggle: () -> Unit,
    onRewind: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Deck.Bezel)
            .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        DeckKey(KeyGlyph.Tapes, "TAPES", onClick = onMenu, modifier = Modifier.weight(1f))
        DeckKey(
            KeyGlyph.Rec, "REC",
            onClick = { if (!running) onToggle() },
            modifier = Modifier.weight(1f),
            glyphColor = Deck.Rec,
            latched = running,
        )
        DeckKey(
            KeyGlyph.Rewind,
            if (unlockSeconds != null) formatDuration(unlockSeconds * 1000L) else "REWIND",
            onClick = onRewind,
            modifier = Modifier.weight(1.2f),
            enabled = canRewind,
            progress = unlockSeconds?.let { 1f - it * 1000f / RewindPolicy.MIN_REWIND_MILLIS },
        )
        DeckKey(
            KeyGlyph.Stop, "STOP",
            onClick = { if (running) onToggle() },
            modifier = Modifier.weight(1f),
        )
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
