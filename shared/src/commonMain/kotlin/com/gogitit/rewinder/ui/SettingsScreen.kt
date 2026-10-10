package com.gogitit.rewinder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gogitit.rewinder.ai.AiControls
import com.gogitit.rewinder.models.ModelsState

/**
 * Settings, shared by both apps, laid out like the back of the deck: silver plates on the cobalt
 * shell. The summary model
 * selector (the only place to change it), transcription language, installed models, privacy.
 */
@Composable
fun SettingsScreen(
    ai: AiControls,
    models: ModelsState?,
    modelsDescription: String,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onPrivacy: () -> Unit,
    onBack: () -> Unit,
) {
    val selectedModel by ai.settings.summaryModel.collectAsState()
    val language by ai.settings.language.collectAsState()
    val summaryLanguage by ai.settings.summaryLanguage.collectAsState()
    val input by ai.settings.inputSource.collectAsState()
    // Re-read file status whenever downloads or the model choice change.
    val options = remember(selectedModel, models) { ai.summaryOptions() }
    val entries = remember(selectedModel, models) { ai.modelEntries() }

    Box(Modifier.fillMaxSize().deckBody()) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            DeckTopBar("SETTINGS", onBack)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Section("SUMMARY MODEL") {
                    options.forEachIndexed { i, option ->
                        if (i > 0) PlateDivider()
                        OptionRow(
                            label = option.label,
                            detail = option.detail,
                            selected = option.id == selectedModel,
                            enabled = option.enabled,
                            onClick = { ai.selectSummaryModel(option.id) },
                        )
                    }
                }
                if (models != null && !models.ready) {
                    Spacer(Modifier.height(12.dp))
                    ModelDownloadCard(models, modelsDescription, onDownload, onCancelDownload)
                }

                Section("RECAP LANGUAGE") {
                    ai.summaryLanguageOptions.forEachIndexed { i, option ->
                        if (i > 0) PlateDivider()
                        OptionRow(
                            label = option.label,
                            detail = option.detail,
                            selected = option.id == summaryLanguage,
                            onClick = { ai.selectSummaryLanguage(option.id) },
                        )
                    }
                }

                if (ai.supportsDeviceAudio) {
                    Section("LISTEN TO") {
                        ai.inputOptions.forEachIndexed { i, option ->
                            if (i > 0) PlateDivider()
                            OptionRow(
                                label = option.label,
                                detail = option.detail,
                                selected = option.id == input,
                                onClick = { ai.selectInput(option.id) },
                            )
                        }
                    }
                }

                Section("SPEECH LANGUAGE") {
                    ai.languageOptions.forEachIndexed { i, option ->
                        if (i > 0) PlateDivider()
                        OptionRow(
                            label = option.label,
                            detail = option.detail,
                            selected = option.id == language,
                            onClick = { ai.selectLanguage(option.id) },
                        )
                    }
                }

                Section("ON-DEVICE MODELS") {
                    entries.forEachIndexed { i, entry ->
                        if (i > 0) PlateDivider()
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Lamp(lit = entry.installed, litColor = Deck.LampReady, diameter = 12.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.file.label, color = Deck.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = buildString {
                                        append(if (entry.installed) "Installed" else "Not downloaded")
                                        append(" · ")
                                        append(AiControls.gigabytes(entry.file.approxBytes))
                                        if (entry.needed) append(" · in use")
                                    },
                                    color = Deck.InkSoft,
                                    fontSize = 13.sp,
                                )
                            }
                            if (entry.installed) {
                                SmallKey("REMOVE", { ai.removeModel(entry.file) }, Modifier.width(92.dp))
                            }
                        }
                    }
                }

                Section("PRIVACY") {
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = onPrivacy).padding(horizontal = 16.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("How Rewinder handles audio", color = Deck.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("Memory only, summaries only, on device", color = Deck.InkSoft, fontSize = 13.sp)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Deck.InkSoft, modifier = Modifier.size(22.dp))
                    }
                }

                Spacer(Modifier.height(22.dp))
                Engraved(
                    "RW-5 · WHISPER LARGE-V3-TURBO · QWEN3.5 (UNSLOTH) · LLAMA.CPP",
                    9.5.sp,
                    color = silkSoft(),
                    spacing = 0.08.em,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(22.dp))
    Engraved(title, 11.sp, color = silkSoft(), maxLines = 1, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .insetPlate(12.dp)
            .padding(vertical = 3.dp),
        content = content,
    )
}
