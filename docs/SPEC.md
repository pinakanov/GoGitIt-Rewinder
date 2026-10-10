# Rewinder: Product Spec

Status: Draft v0.1 (2026-10-09)
Owner: JC
Platforms: Android and iOS

## 1. One-liner

Rewinder is an ADHD support app that listens only while the user has it switched on, keeps only the last 5 minutes of sound, and on request turns that window into a short summary of what was just said, so the user can catch up on context they zoned out of.

It is the Apple Watch "audio intelligence" idea, reduced to one job and made available on any phone.

## 2. Core principles

1. Summary is the only output. No audio playback, no transcript view, no chat. The user never sees or hears raw material.
2. Opt-in and visible. Nothing records until the user presses Start, and a persistent lock screen widget shows that it is running.
3. Rolling memory. Audio older than 5 minutes is destroyed continuously.
4. Paraphrase, never quote. Original sentence composition is intentionally lost.
5. Sensitive details are filtered before a summary is written.

## 3. How it works

### 3.1 Pipeline

1. User taps Start. Rewinder begins capturing from the active audio source (phone mic, or a connected Bluetooth or wired mic).
2. Audio goes into a rolling in-memory buffer capped at 5 minutes. New audio pushes out the oldest audio.
3. User taps Log (see 3.2). The current buffer is handed to the on-device transcriber.
4. The transcript goes through the sensitive information classifier, then to the local LLM, which writes a summary and a short title.
5. The transcript and the audio slice used are wiped as soon as the summary is saved. Only the title, time range and summary text are kept as a Recent.

### 3.2 The Log action

- Log is the working name for the "Lap" equivalent. Alternatives to test: Rewind, Recap, Catch me up. Recommendation: **Rewind**, since it matches the app name and the mental model.
- Log is disabled until at least 3 minutes of audio is buffered. The button shows a progress state (for example a ring filling toward 3:00) so the user knows why it is inactive.
- Once enabled, Log summarizes whatever is in the buffer, from 3 up to 5 minutes.
- Decided: rewinds overlap. Every Rewind summarizes the whole buffer (up to the last 5 minutes), even if an earlier Rewind already covered part of it. The 3 minute minimum only applies after Start.
- Rewind stays available while earlier rewinds are still being summarized. Each tap captures that moment immediately; summaries are processed one at a time, in order.

### 3.3 Local LLM

- The model ships with the app and runs on device. No network call is needed for transcription or summarization.
- The LLM has no chat interface and accepts no user prompts. Its only input is the filtered transcript, its only output is a summary and title.
- Summary style: short, plain, ADHD friendly. Suggested shape: one line "what this was about", then 2 to 5 bullets of key points, decisions or things asked of the user.

### 3.4 Transcription behavior

- No speaker attribution. The transcriber does not label who said what.
- It does recognize that a conversation is happening and passes along conversational context (for example "a discussion between several people about lunch options") so the summary stays coherent.

## 4. Screens

### 4.1 Home (idle and running)

The RW-5 cassette-corder in a cobalt finish, following the design canvas ("Recorder (interactive)") and the skeuomorphic redesign brief. Colors are sampled from a blue EPOK cassette player: cobalt shell, cool grey keys and plates, orange foam behind the grille. A screenshot should look like a photo of a device: grain over every part, lit top edges, recessed panels, raised keys, glass reflections.

- Header: REWINDER wordmark (Archivo Black) with a screw beside it, "CASSETTE-CORDER RW-5 · AUTO MEMORY", and the REC jewel lamp (blinks at 1 Hz while recording). No Settings on Home; it opens from the Tapes shelf.
- LCD (dot-matrix, Doto): mode tag (STOP / REC with a blinking dot / REW), the tape counter (time in memory) over unlit "8" segments, clock and date, a 24-segment position bar (playhead solid, filled tape behind it, the 3:00 unlock segment outlined), and the status line (PRESS REC TO START, REWIND IN m:ss, REWIND READY, TRANSCRIBING, SUMMARIZING). Pixel grid and a hard diagonal glare on the glass.
- Tape bay: dark bezel with four screws, smoked recess, the C-5 cassette with its own screws, stripes and head opening. The tape packs show progress: the supply reel shrinks and the take-up reel grows as the 5 minute memory fills. Reels turn counter-clockwise while recording (a fuller reel turns slower, 1.0 to 1.9 s per turn), clockwise fast (0.32 s) while a rewind is summarized, and freeze where they are on stop. A sheen sweeps the glass every 5.5 s only while recording.
- VU meter: cream face, -20 to +3 scale with a red zone, needle swings with a little overshoot. Lamps: INPUT (amber, input above the noise floor), READY (green, Rewind available), AI (teal, on-device models installed).
- Speaker grille: punched holes with orange foam showing through. It absorbs spare height; on short phones the page scrolls instead.
- Piano keys in a dark rail: TAPES, REC (latches down with a glowing red dot while recording), REWIND (dim and still until 3:00, shows a countdown and an orange charging strip), STOP. Keys travel 5 dp in 60 ms, the lip shrinks from 8 to 3 dp, and they spring back with a small overshoot. Haptics: medium thunk on press, light tap on release, none on a disabled key.
- Nothing moves while idle. All continuous motion happens only while recording or rewinding.
- The summary model is chosen in Settings. Qwen is the default on both platforms.

### 4.2 Tapes shelf

Full screen, following the canvas ("Tapes shelf").

- Header: TAPES wordmark, "TAPE SHELF · AUTO MEMORY", and a DECK key that returns to the recorder.
- A dark rack with one slot per rewind, newest first: a cassette thumbnail ("A  C-N", stripe colors cycling orange, teal, crimson, olive, blue), the title in handwriting (Caveat), the time range (for example "9:50-9:53") and "length · SIDE A", and a play key. Tapping the slot or the key opens the read-only recap. No reply box.
- Five slots fill the rack; with more tapes it scrolls inside the bezel and a sixth slot peeks in.
- Empty: a cassette outline and "NO TAPES YET · PRESS REC".
- Under the rack: chrome knobs for SEARCH (a paper label strip to type in) and SETTINGS.
- Tapes are saved on the device (up to 500) and survive app restarts.

### 4.2b Accessibility

- Keys are buttons with spoken labels (Tapes, Record, Rewind, Stop); REC reports selected while latched. The LCD reads as one sentence ("Recording, 3 minutes 4 seconds in memory. Rewind ready."). The VU meter reads "Input level" in 10 percent steps. Screws, grille, glare, ghost digits and cassette art are hidden.
- Reduce Motion (iOS) or Remove animations (Android): reels and sheen stop, the needle moves linearly, key travel is 2 dp. The REC lamp still blinks.
- Increase Contrast or Reduce Transparency (iOS), high contrast (Android 14+): no grain or glass reflections, secondary print at full contrast.
- Printed legends keep their size as system text grows (they are part of the object); at large text sizes the LCD status also shows in a large plain strip under the LCD.

### 4.2a Settings additions

- Recap language: Auto, Filipino or English. Filipino writes the summary in Filipino.
- Listen to: Microphone, or Microphone + phone audio (videos, online classes, browser audio).
- Calls themselves cannot be captured on Android or iOS (system privacy rules). After a call ends the mic is reopened automatically.
- Android asks for screen-capture permission on each REC when phone audio is on. On iOS the user starts the "Rewinder Audio" broadcast from the system sheet once per session.

### 4.3 Summary view

- Title, time range, summary body.
- Persistent footer line: "AI summary. Not a record of what was said and not evidence."
- Actions: copy text, delete. No share of audio, because none exists.

### 4.4 Lock screen widget (non-disappearing)

Based on the sketch (left frame).

- Shown for as long as Rewinder is running. Cannot be swiped away while active, and disappears only when the user stops Rewinder.
- Contains: live clock, the text "Rewinder in progress", a live waveform, a **Stop** control and a **Log** control.
- Implementation notes:
  - iOS: Live Activity plus background audio mode and the recording indicator.
  - Android: foreground service with microphone type and an ongoing, non-dismissible notification with actions.
- The OS microphone indicator (orange dot on iOS, mic chip on Android) must remain visible. We do not try to hide it.

## 5. Privacy and safety

### 5.1 What the system guarantees

| Concern | Design |
| --- | --- |
| Audio access | The user cannot play back audio, and audio is never written to persistent storage. |
| Audio retention | In-memory ring buffer only, 5 minute cap, continuously overwritten. Wiped on Stop, app kill, or log completion for the used slice. |
| Always listening | No. Recording only runs between Start and Stop, with a lock screen widget as proof. |
| Transcript retention | Exists only during summarization, then deleted. Never shown. |
| Sensitive data | A classifier runs on the transcript before the LLM. Flagged spans are removed or replaced with a neutral placeholder, and the LLM is instructed never to restate them. |
| Wording | The summary is a paraphrase. Original sentence composition is not preserved. |
| Speaker identity | Not tracked. |
| Network | On-device processing. No audio or transcript leaves the phone. |

### 5.2 Sensitive information classifier (initial categories)

Passwords and PINs, payment card and bank details, government ID numbers, phone numbers, home addresses, email addresses, health details, and similar personal identifiers. Behavior: the summary says that something sensitive was discussed without saying what (for example "account details were mentioned"). Final category list is an open item.

### 5.3 Legal and responsibility statements

These need to be shown in onboarding (accepted once) and in app settings.

- Rewinder is a personal memory aid, not a recording or monitoring tool.
- The user decides when to start it and is responsible for how and where they use it, including any wrongdoing that results from their use.
- Summaries are AI generated paraphrases. They are not verbatim, not complete, and cannot be used as legal evidence.
- The user is responsible for informing other people where the law requires it.

### 5.4 Things I would flag before launch

- **Consent laws are separate from our disclaimer.** Many places require consent from everyone in a conversation, and the Philippines has the Anti-Wiretapping Act (RA 4200). A user disclaimer shifts responsibility to the user, but it does not remove the legal risk of the app itself, and store review will ask about this. Worth a short check with a lawyer before release. This is a flag, not legal advice.
- **Not stored does not mean not sensitive.** Even a few minutes of ambient audio contains other people's speech, so a "recording in progress" cue to others (for example an optional subtle chime or a visible screen state) may be worth considering.
- **Classifier misses will happen.** Position it as best effort, and keep the summary text in the user's device only so a miss does not leave the phone.
- **Mic access in the background** is tightly policed on both platforms. iOS and Android both need a clearly stated user-visible purpose, plus visible indicators. Plan for review friction.
- **Hearing or accessibility value.** The same flow could help people who zone out in class, meetings or noisy places, which is a nice framing for store listings.

## 6. Non-goals (v1)

- No chat, prompts or follow-up questions to the LLM.
- No audio playback or export, no raw transcript view.
- No speaker identification or diarization.
- No cloud sync of summaries.
- No always-on or background auto-start.

## 7. Suggested tech direction (to confirm)

- Cross platform UI: Flutter or React Native, with native modules for the foreground service and Live Activity.
- Transcription: on-device Whisper class model, or the OS speech APIs where they run offline.
- Summarizer: small quantized on-device LLM (for example a 1 to 3B parameter model) bundled or downloaded once on first launch.
- Storage: local encrypted database for titles, time ranges and summaries only.

## 8. Open questions

1. Final name for the Log button (Rewind recommended).
2. ~~Does a log clear the buffer, or advance a cut point?~~ Decided: neither, rewinds overlap.
3. Recents label: time range of the audio covered, or session start and end?
4. Does a Recent summary expire automatically (for example after 7 or 30 days)?
5. Is the model bundled in the app or downloaded on first launch (bundle size versus store limits)?
6. Which languages at launch (English and Filipino are the obvious pair)?
7. Final sensitive category list and placeholder wording.
8. Should there be a minimum summary length, and what does the app show when the buffer contains silence or noise only?
