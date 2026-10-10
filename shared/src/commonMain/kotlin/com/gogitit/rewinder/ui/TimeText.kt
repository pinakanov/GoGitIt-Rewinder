package com.gogitit.rewinder.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
fun nowLocal(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

/** Ticks on each wall-clock second. */
@OptIn(ExperimentalTime::class)
@Composable
fun rememberLocalNow(): State<LocalDateTime> = produceState(initialValue = nowLocal()) {
    while (true) {
        value = nowLocal()
        delay(1000L - Clock.System.now().toEpochMilliseconds() % 1000L)
    }
}

fun Int.pad2(): String = toString().padStart(2, '0')

/** Epoch millis -> "9:53" in the device time zone. */
@OptIn(ExperimentalTime::class)
fun Long.toClockLabel(): String {
    val t = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${t.hour}:${t.minute.pad2()}"
}

/** "Friday, 9 October" */
fun LocalDateTime.dateLabel(): String {
    fun String.title() = lowercase().replaceFirstChar { it.uppercase() }
    return "${dayOfWeek.name.title()}, $dayOfMonth ${month.name.title()}"
}

/** "SAT, 10 OCT", for narrow LCDs. */
fun LocalDateTime.shortDateLabel(): String = "${dayOfWeek.name.take(3)}, $dayOfMonth ${month.name.take(3)}"

/** "9:50-9:53" for a tape's time range. */
fun clockRange(startMillis: Long, endMillis: Long): String = "${startMillis.toClockLabel()}-${endMillis.toClockLabel()}"
