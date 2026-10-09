package com.gogitit.rewinder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val statements = listOf(
    "Off unless you turn it on" to
        "Rewinder only listens between Start and Stop, and shows that it is running on your lock screen.",
    "Five minutes, in memory" to
        "Audio lives in a rolling 5 minute buffer in memory. Older audio is overwritten continuously and everything is wiped on Stop.",
    "No audio, no transcript" to
        "You cannot play back audio or read a transcript. Both are deleted as soon as a summary is written.",
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

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(RewinderColors.Background)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        CircleIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(24.dp))
            Text("Privacy", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            statements.forEach { (title, body) ->
                Spacer(Modifier.height(18.dp))
                Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(body, color = RewinderColors.TextSecondary, fontSize = 15.sp, lineHeight = 21.sp)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
