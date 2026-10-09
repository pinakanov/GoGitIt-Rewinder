package com.gogitit.rewinder.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gogitit.rewinder.core.formatDuration
import com.gogitit.rewinder.data.Recent

/** Read-only recap, printed on the tape's J-card. No input box: Rewinder is not a chat. */
@Composable
fun SummaryScreen(recent: Recent, onBack: () -> Unit, onDelete: () -> Unit) {
    Box(Modifier.fillMaxSize().deckBody()) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            DeckTopBar("TAPE", onBack) {
                MetalIconButton(Icons.Default.Delete, "Delete this rewind", onDelete, tint = Deck.Rec)
            }
            Spacer(Modifier.height(16.dp))

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .shadow(6.dp, RoundedCornerShape(6.dp))
                        .paper(),
                ) {
                    Canvas(Modifier.fillMaxWidth().height(12.dp)) {
                        drawRect(Deck.StripeOrange, Offset.Zero, Size(size.width, size.height * 0.55f))
                        drawRect(Deck.StripeMustard, Offset(0f, size.height * 0.55f), Size(size.width, size.height * 0.45f))
                    }
                    Column(Modifier.padding(start = 40.dp, end = 18.dp, top = 18.dp, bottom = 22.dp)) {
                        Text(
                            "SIDE A  ·  ${recent.startedAtMillis.toClockLabel()} TO ${recent.endedAtMillis.toClockLabel()}" +
                                "  ·  ${formatDuration(recent.endedAtMillis - recent.startedAtMillis)}",
                            color = Deck.InkSoft,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        SelectionContainer {
                            Column {
                                Text(recent.title, color = Deck.Ink, fontSize = 26.sp, fontWeight = FontWeight.Black, lineHeight = 30.sp)
                                Spacer(Modifier.height(14.dp))
                                Text(recent.gist, color = Deck.Ink, fontSize = 17.sp, lineHeight = 25.sp)
                                Spacer(Modifier.height(10.dp))
                                recent.points.forEach { point ->
                                    Row(Modifier.padding(vertical = 5.dp)) {
                                        Text("•", color = Deck.StripeOrange, fontSize = 18.sp, fontWeight = FontWeight.Black)
                                        Spacer(Modifier.width(10.dp))
                                        Text(point, color = Deck.Ink, fontSize = 16.sp, lineHeight = 23.sp)
                                    }
                                }
                            }
                        }
                        if (recent.redactedCount > 0) {
                            Spacer(Modifier.height(18.dp))
                            Text(
                                text = if (recent.redactedCount == 1) "1 SENSITIVE DETAIL WITHHELD"
                                else "${recent.redactedCount} SENSITIVE DETAILS WITHHELD",
                                color = Deck.Rec,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp,
                                modifier = Modifier
                                    .rotate(-3f)
                                    .border(2.dp, Deck.Rec, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            Engraved(
                "AI SUMMARY. A PARAPHRASE, NOT A RECORD OF WHAT WAS SAID, AND NOT EVIDENCE.",
                10.sp,
                color = Deck.EngravedSoft,
                mono = true,
                weight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            )
        }
    }
}
