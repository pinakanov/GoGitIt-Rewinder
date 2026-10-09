package com.gogitit.rewinder.data

import com.gogitit.rewinder.pipeline.Summary
import com.gogitit.rewinder.settings.KeyValueStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

/**
 * Keeps tapes across app restarts in the platform key-value store (SharedPreferences /
 * NSUserDefaults, both private to the app and excluded from cloud backup on Android).
 * Only summaries are stored: title, gist, points, counts and times. Never audio or transcripts.
 */
class StoredRecentsRepository(
    private val store: KeyValueStore,
    private val maxKept: Int = 500,
) : RecentsRepository {
    private val lock = Mutex()
    private val items = MutableStateFlow(load())
    private var nextId = (items.value.maxOfOrNull { it.id } ?: 0L) + 1L

    override val recents: StateFlow<List<Recent>> = items.asStateFlow()

    override suspend fun add(summary: Summary, startedAtMillis: Long, endedAtMillis: Long): Recent = lock.withLock {
        val recent = Recent(
            id = nextId++,
            title = summary.title,
            gist = summary.gist,
            points = summary.points,
            redactedCount = summary.redactedCount,
            startedAtMillis = startedAtMillis,
            endedAtMillis = endedAtMillis,
        )
        items.update { (listOf(recent) + it).take(maxKept) }
        save()
        recent
    }

    override suspend fun delete(id: Long) = lock.withLock {
        items.update { list -> list.filterNot { it.id == id } }
        save()
    }

    private fun save() = store.putString(KEY, RecentsCodec.encode(items.value))

    private fun load(): List<Recent> = store.getString(KEY)?.let { RecentsCodec.decode(it) }.orEmpty()

    private companion object {
        const val KEY = "tapes_v1"
    }
}

/**
 * Plain-text format for saved tapes, so no serialization library is needed.
 * Records, fields and points are split by ASCII separator characters, which never occur in
 * speech summaries (they are stripped from the text before saving, just in case).
 */
object RecentsCodec {
    private const val RECORD = '\u001E'
    private const val FIELD = '\u001F'
    private const val POINT = '\u001D'
    private const val VERSION = "1"

    fun encode(list: List<Recent>): String = list.joinToString(RECORD.toString()) { r ->
        listOf(
            VERSION,
            r.id.toString(),
            clean(r.title),
            clean(r.gist),
            r.points.joinToString(POINT.toString()) { clean(it) },
            r.redactedCount.toString(),
            r.startedAtMillis.toString(),
            r.endedAtMillis.toString(),
        ).joinToString(FIELD.toString())
    }

    fun decode(text: String): List<Recent> {
        if (text.isEmpty()) return emptyList()
        return text.split(RECORD).mapNotNull { record ->
            val f = record.split(FIELD)
            if (f.size < 8 || f[0] != VERSION) return@mapNotNull null
            Recent(
                id = f[1].toLongOrNull() ?: return@mapNotNull null,
                title = f[2],
                gist = f[3],
                points = if (f[4].isEmpty()) emptyList() else f[4].split(POINT),
                redactedCount = f[5].toIntOrNull() ?: 0,
                startedAtMillis = f[6].toLongOrNull() ?: 0L,
                endedAtMillis = f[7].toLongOrNull() ?: 0L,
            )
        }
    }

    private fun clean(s: String) = s.filterNot { it == RECORD || it == FIELD || it == POINT }
}
