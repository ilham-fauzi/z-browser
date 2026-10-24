package com.browserz.core.data

import android.content.SharedPreferences
import com.browserz.core.domain.HistoryEntry
import com.browserz.core.domain.HistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Newest first, one entry per URL, capped so the stored JSON stays small. */
class PrefsHistoryRepository(private val prefs: SharedPreferences) : HistoryRepository {

    private val _entries = MutableStateFlow(read())
    override val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    @Synchronized
    override fun record(url: String, title: String) {
        val entry = HistoryEntry(url, title.ifBlank { url }, System.currentTimeMillis())
        _entries.value = (listOf(entry) + _entries.value.filterNot { it.url == url }).take(MAX_ENTRIES)
        write()
    }

    @Synchronized
    override fun remove(url: String) {
        _entries.value = _entries.value.filterNot { it.url == url }
        write()
    }

    @Synchronized
    override fun clear() {
        _entries.value = emptyList()
        write()
    }

    private fun write() {
        val array = JSONArray()
        _entries.value.forEach {
            array.put(JSONObject().put("url", it.url).put("title", it.title).put("at", it.visitedAt))
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private fun read(): List<HistoryEntry> {
        val json = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val url = o.optString("url").ifEmpty { return@mapNotNull null }
                HistoryEntry(url, o.optString("title", url), o.optLong("at"))
            }
        } catch (_: JSONException) {
            emptyList()
        }
    }

    private companion object {
        const val KEY = "history"
        const val MAX_ENTRIES = 500
    }
}
