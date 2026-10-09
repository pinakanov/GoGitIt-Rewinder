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

/** Language the recap is written in. */
object SummaryLanguages {
    /** Whatever the speakers mostly used: English, Filipino or Taglish. */
    const val AUTO = "auto"
    const val FILIPINO = "fil"
    const val ENGLISH = "en"
}

/** What Rewinder listens to. */
object InputSources {
    const val MIC = "mic"

    /** Microphone mixed with sound playing on the phone (videos, online classes, browser). */
    const val MIC_AND_DEVICE = "mic_device"
}

/** User choices, shared by both apps and persisted across launches. */
class AppSettings(
    private val store: KeyValueStore,
    defaultSummaryModel: String,
) {
    init {
        // v2: Qwen became the default summary model. Earlier builds defaulted to Apple's model
        // on iOS 26, so that saved choice is reset once; picking Apple again in Settings sticks.
        val version = store.getString(KEY_VERSION)?.toIntOrNull() ?: 1
        if (version < 2) {
            if (store.getString(KEY_SUMMARY) == "apple") store.putString(KEY_SUMMARY, null)
            store.putString(KEY_VERSION, "2")
        }
    }

    private val _summaryModel = MutableStateFlow(store.getString(KEY_SUMMARY) ?: defaultSummaryModel)
    val summaryModel: StateFlow<String> = _summaryModel.asStateFlow()

    private val _language = MutableStateFlow(store.getString(KEY_LANGUAGE) ?: TranscriptionLanguages.FILIPINO)
    val language: StateFlow<String> = _language.asStateFlow()

    private val _summaryLanguage = MutableStateFlow(store.getString(KEY_SUMMARY_LANGUAGE) ?: SummaryLanguages.AUTO)
    val summaryLanguage: StateFlow<String> = _summaryLanguage.asStateFlow()

    private val _inputSource = MutableStateFlow(store.getString(KEY_INPUT) ?: InputSources.MIC)
    val inputSource: StateFlow<String> = _inputSource.asStateFlow()

    fun setSummaryModel(id: String) {
        store.putString(KEY_SUMMARY, id)
        _summaryModel.value = id
    }

    fun setLanguage(id: String) {
        store.putString(KEY_LANGUAGE, id)
        _language.value = id
    }

    fun setSummaryLanguage(id: String) {
        store.putString(KEY_SUMMARY_LANGUAGE, id)
        _summaryLanguage.value = id
    }

    fun setInputSource(id: String) {
        store.putString(KEY_INPUT, id)
        _inputSource.value = id
    }

    private companion object {
        const val KEY_VERSION = "settings_version"
        const val KEY_SUMMARY = "summary_model"
        const val KEY_LANGUAGE = "transcription_language"
        const val KEY_SUMMARY_LANGUAGE = "summary_language"
        const val KEY_INPUT = "input_source"
    }
}
