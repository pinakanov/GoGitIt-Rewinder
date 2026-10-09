package com.gogitit.rewinder.data

import com.gogitit.rewinder.pipeline.Summary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A saved rewind. This is the only thing Rewinder keeps. No audio, no transcript. */
data class Recent(
    val id: Long,
    val title: String,
    val gist: String,
    val points: List<String>,
    val redactedCount: Int,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
)

interface RecentsRepository {
    val recents: StateFlow<List<Recent>>
    suspend fun add(summary: Summary, startedAtMillis: Long, endedAtMillis: Long): Recent
    suspend fun delete(id: Long)
}

/**
 * Lives for the app process only. Swap for an encrypted local database
 * (Room KMP or SQLDelight) when persistence is decided (spec open question 4).
 */
class InMemoryRecentsRepository : RecentsRepository {
    private val items = MutableStateFlow<List<Recent>>(emptyList())
    private var nextId = 1L

    override val recents: StateFlow<List<Recent>> = items.asStateFlow()

    override suspend fun add(summary: Summary, startedAtMillis: Long, endedAtMillis: Long): Recent {
        val recent = Recent(
            id = nextId++,
            title = summary.title,
            gist = summary.gist,
            points = summary.points,
            redactedCount = summary.redactedCount,
            startedAtMillis = startedAtMillis,
            endedAtMillis = endedAtMillis,
        )
        items.update { listOf(recent) + it }
        return recent
    }

    override suspend fun delete(id: Long) {
        items.update { list -> list.filterNot { it.id == id } }
    }
}
