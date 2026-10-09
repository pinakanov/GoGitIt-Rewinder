package com.gogitit.rewinder.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gogitit.rewinder.models.ModelsState

/** A selector row on a plate: a lamp lights next to the chosen option, like a source selector. */
@Composable
fun OptionRow(
    label: String,
    detail: String?,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    trailingArrow: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Lamp(lit = selected, diameter = 14.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                color = Deck.Ink.copy(alpha = if (enabled) 1f else 0.4f),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (detail != null) {
                Spacer(Modifier.height(2.dp))
                Text(detail, color = Deck.InkSoft.copy(alpha = if (enabled) 1f else 0.6f), fontSize = 13.sp, lineHeight = 17.sp)
            }
        }
        if (trailingArrow) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Deck.InkSoft, modifier = Modifier.size(22.dp))
        }
    }
}

/** Groove between rows on a plate. */
@Composable
fun PlateDivider() {
    Column(Modifier.padding(horizontal = 14.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x33000000)))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x66FFFFFF)))
    }
}

/** Download prompt and progress for the on-device models, as a plate with an LCD bar. */
@Composable
fun ModelDownloadCard(
    models: ModelsState,
    description: String,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .insetPlate(12.dp)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Lamp(lit = models.downloading, diameter = 12.dp)
            Spacer(Modifier.width(8.dp))
            Engraved("ON-DEVICE AI NOT INSTALLED", 12.sp, mono = true, spacing = 1.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Download $description once, over Wi-Fi. After that, transcription and " +
                "summaries run fully offline on this phone.",
            color = Deck.InkSoft,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(12.dp))
        if (models.downloading) {
            LcdPanel(Modifier.fillMaxWidth()) {
                Column {
                    LcdText("${(models.current ?: "MODELS").uppercase()}  ${(models.progress * 100).toInt()}%", 11.sp)
                    Spacer(Modifier.height(5.dp))
                    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                        val count = 30
                        val gap = 2.dp.toPx()
                        val segW = (size.width - gap * (count - 1)) / count
                        val filled = (models.progress * count).toInt()
                        for (i in 0 until count) {
                            drawRect(
                                if (i < filled) Deck.LcdInk else Deck.LcdGhost,
                                Offset(i * (segW + gap), 0f),
                                Size(segW, size.height),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            SmallKey("CANCEL", onCancel, Modifier.fillMaxWidth(0.45f))
        } else {
            models.error?.let {
                Text(it, color = Deck.Rec, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
            }
            SmallKey(
                if (models.error != null || models.downloadedBytes > 0) "RESUME" else "DOWNLOAD",
                onDownload,
                Modifier.fillMaxWidth(0.55f),
            )
        }
    }
}
