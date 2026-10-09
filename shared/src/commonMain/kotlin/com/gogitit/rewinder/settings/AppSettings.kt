package com.gogitit.rewinder.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tiny persistent key-value store. Android: SharedPreferences. iOS: NSUserDefaults. */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
}

/** In-memory store for previews and tests. */
class MemoryStore : KeyValueStore {
    private val map = mutableMapOf<String, String>()
    override fun getString(key: String): String? = map[key]
    override fun putString(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }
}

/** Speech language for Whisper. */
object TranscriptionLanguages {
    const val FILIPINO = "tl"
    const val ENGLISH = "en"
    const val AUTO = "auto"
}

/** User choices, shared by both apps and persisted across launches. */
class AppSettings(
    private val store: KeyValueStore,
    defaultSummaryModel: String,
) {
    private val _summaryModel = MutableStateFlow(store.getString(KEY_SUMMARY) ?: defaultSummaryModel)
    val summaryModel: StateFlow<String> = _summaryModel.asStateFlow()

    private val _language = MutableStateFlow(store.getString(KEY_LANGUAGE) ?: TranscriptionLanguages.FILIPINO)
    val language: StateFlow<String> = _language.asStateFlow()

    fun setSummaryModel(id: String) {
        store.putString(KEY_SUMMARY, id)
        _summaryModel.value = id
    }

    fun setLanguage(id: String) {
        store.putString(KEY_LANGUAGE, id)
        _language.value = id
    }

    private companion object {
        const val KEY_SUMMARY = "summary_model"
        const val KEY_LANGUAGE = "transcription_language"
    }
}
