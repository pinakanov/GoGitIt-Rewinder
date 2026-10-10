package com.gogitit.rewinder.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val statements = listOf(
    "Off unless you turn it on" to
        "Rewinder only listens between Start and Stop, and shows that it is running on your lock screen.",
    "Five minutes, in memory" to
        "Audio lives in a rolling 5 minute buffer in memory. Older audio is overwritten continuously and everything is wiped on Stop.",
    "Phone audio is optional" to
        "If you turn on Microphone + phone audio, Rewinder also hears what is playing on this phone, the same way: memory only, last 5 minutes. Android shows its screen-capture prompt and iOS its Screen Broadcast sheet each time. Phone and video calls are never captured; the system keeps call audio private.",
    "No audio, no transcript" to
        "You cannot play back audio or read a transcript. Both are deleted as soon as a summary is written.",
    "Tapes stay on this phone" to
        "Each tape keeps only its title, recap and time. They are saved privately on this phone so they survive a restart, are never uploaded, and are gone when you delete them or the app.",
    "Paraphrased, not quoted" to
        "Summaries describe what was discussed. They do not keep original wording or say who said what.",
    "Sensitive details filtered" to
        "Passwords, PINs, card and account numbers, phone numbers, emails and ID numbers are removed before summarizing.",
    "On this phone only" to
        "Transcription (Whisper) and summarizing (Qwen) run on device. The models are downloaded once from Hugging Face; your audio and summaries are never uploaded.",
    "Not evidence" to
        "Summaries are AI generated paraphrases. They can be wrong or incomplete and cannot be used as a legal record.",
    "Your responsibility" to
        "You choose when and where to use Rewinder, and you are responsible for telling others where the law requires it.",
)

/** The owner's manual page about privacy. */
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().deckBody()) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            DeckTopBar("PRIVACY", onBack)
            Spacer(Modifier.height(16.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .paper(ruled = false)
                        .padding(horizontal = 20.dp, vertical = 22.dp),
                ) {
                    Text("OWNER'S MANUAL  ·  RW-5", color = Deck.InkSoft, fontSize = 11.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("How Rewinder handles sound", color = Deck.Ink, fontSize = 24.sp, fontWeight = FontWeight.Black, lineHeight = 28.sp)
                    statements.forEachIndexed { index, (title, body) ->
                        Spacer(Modifier.height(18.dp))
                        Row {
                            Text("${index + 1}.", color = Deck.StripeOrange, fontSize = 16.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(28.dp))
                            Column {
                                Text(title, color = Deck.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(3.dp))
                                Text(body, color = Deck.InkSoft, fontSize = 14.sp, lineHeight = 20.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
