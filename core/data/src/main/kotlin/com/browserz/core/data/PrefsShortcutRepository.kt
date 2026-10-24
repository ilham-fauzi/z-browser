package com.browserz.core.data

import android.content.SharedPreferences
import com.browserz.core.domain.NtpShortcuts
import com.browserz.core.domain.PinnedSite
import com.browserz.core.domain.ShortcutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class PrefsShortcutRepository(private val prefs: SharedPreferences) : ShortcutRepository {
    private val _pinned = MutableStateFlow(read())
    override val pinned: StateFlow<List<PinnedSite>> = _pinned.asStateFlow()

    @Synchronized
    override fun pin(url: String, title: String): Boolean {
        val others = _pinned.value.filterNot { it.url == url }
        if (others.size >= NtpShortcuts.MAX) return false
        _pinned.value = others + PinnedSite(url, title.ifBlank { url })
        write()
        return true
    }

    @Synchronized
    override fun unpin(url: String) {
        _pinned.value = _pinned.value.filterNot { it.url == url }
        write()
    }

    private fun write() {
        val array = JSONArray()
        _pinned.value.forEach { array.put(JSONObject().put("url", it.url).put("title", it.title)) }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private fun read(): List<PinnedSite> = try {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val url = o.optString("url").ifEmpty { return@mapNotNull null }
            PinnedSite(url, o.optString("title", url))
        }
    } catch (_: JSONException) {
        emptyList()
    }

    private companion object {
        const val KEY = "pinnedSites"
    }
}
