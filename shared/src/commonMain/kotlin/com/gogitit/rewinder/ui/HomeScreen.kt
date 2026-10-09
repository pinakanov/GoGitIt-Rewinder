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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
    onSettings: () -> Unit,
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
    val fill = state.bufferedMillis.toFloat() / RewindPolicy.WINDOW_MILLIS

    Box(Modifier.fillMaxSize().deckBody()) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            NamePlate(recording = state.isRunning, onSettings = onSettings)
            Spacer(Modifier.height(12.dp))

            // Content scrolls on short phones; on tall ones the speaker grille fills the gap.
            BoxWithConstraints(Modifier.weight(1f)) {
                val viewport = maxHeight
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Column(Modifier.heightIn(min = viewport)) {
                        DeckLcd(state)
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
                                level = if (state.isRunning) meterLevel(state.levels) else 0f,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                            Spacer(Modifier.width(12.dp))
                            LampPanel(state, models, Modifier.fillMaxHeight())
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

            TransportKeys(state, onMenu = onMenu, onToggle = onToggle, onRewind = onRewind)
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun NamePlate(recording: Boolean, onSettings: () -> Unit) {
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
        Spacer(Modifier.width(14.dp))
        RoundButton(onClick = onSettings, diameter = 42.dp) {
            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Deck.Engraved, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun DeckLcd(state: RewinderState) {
    val now by rememberLocalNow()
    val blink = rememberInfiniteTransition()
    val blinkAlpha by blink.animateFloat(1f, 0.15f, infiniteRepeatable(tween(600), RepeatMode.Reverse))
    val mode = when {
        state.isSummarizing -> "REW"
        state.isRunning -> "REC"
        else -> "STOP"
    }
    LcdPanel(Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                LcdText(mode, 13.sp, Modifier.alpha(if (state.isRunning) blinkAlpha else 1f).padding(bottom = 6.dp))
                Spacer(Modifier.width(10.dp))
                LcdText(formatDuration(state.bufferedMillis), 34.sp, weight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    LcdText("${now.hour.pad2()}:${now.minute.pad2()}", 16.sp)
                    LcdText(now.dateLabel().uppercase(), 9.sp, weight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(4.dp))
            TapeSegments(state.bufferedMillis, Modifier.fillMaxWidth().height(8.dp))
            Spacer(Modifier.height(5.dp))
            Row {
                LcdText(lcdStatus(state), 11.sp, Modifier.weight(1f))
                if (state.isRunning) {
                    LcdText(state.inputDb?.let { "$it dB" } ?: "MIC...", 11.sp, weight = FontWeight.Medium)
                }
            }
        }
    }
}

/** 5:00 of tape as 30 LCD segments, with a notch at the 3:00 rewind point. */
@Composable
private fun TapeSegments(bufferedMillis: Long, modifier: Modifier) {
    Canvas(modifier) {
        val count = 30
        val gap = 2.dp.toPx()
        val segW = (size.width - gap * (count - 1)) / count
        val filled = (bufferedMillis.toFloat() / RewindPolicy.WINDOW_MILLIS * count).toInt()
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
private fun LampPanel(state: RewinderState, models: ModelsState?, modifier: Modifier) {
    Column(
        modifier.insetPlate(10.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        LampRow("INPUT", state.isRunning && (state.inputDb ?: -120) > -60)
        LampRow("READY", state.canRewind)
        LampRow("AI", models?.ready ?: true, litColor = Color(0xFF7BD389))
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
private fun TransportKeys(state: RewinderState, onMenu: () -> Unit, onToggle: () -> Unit, onRewind: () -> Unit) {
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
            onClick = { if (!state.isRunning) onToggle() },
            modifier = Modifier.weight(1f),
            glyphColor = Deck.Rec,
            latched = state.isRunning,
        )
        DeckKey(
            KeyGlyph.Rewind,
            if (state.isRunning && !state.canRewind) formatDuration(state.millisUntilUnlock) else "REWIND",
            onClick = onRewind,
            modifier = Modifier.weight(1.2f),
            enabled = state.canRewind,
            progress = if (state.isRunning && !state.canRewind) state.unlockProgress else null,
        )
        DeckKey(
            KeyGlyph.Stop, "STOP",
            onClick = { if (state.isRunning) onToggle() },
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
