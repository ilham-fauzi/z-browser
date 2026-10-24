package com.browserz.core.data

import android.content.SharedPreferences
import com.browserz.core.domain.DefaultSearchEngines
import com.browserz.core.domain.SearchEngine
import com.browserz.core.domain.SearchEngineRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Built-in presets plus user-added engines (stored as JSON), and the chosen default. */
class PrefsSearchEngineRepository(private val prefs: SharedPreferences) : SearchEngineRepository {

    private val custom = MutableStateFlow(readCustom())
    private val _engines = MutableStateFlow(DefaultSearchEngines.presets + custom.value)
    private val _default = MutableStateFlow(resolveDefault(prefs.getLong(DEFAULT_ID, 1L)))

    override val engines: StateFlow<List<SearchEngine>> = _engines.asStateFlow()
    override val defaultEngine: StateFlow<SearchEngine> = _default.asStateFlow()

    @Synchronized
    override fun setDefault(id: Long) {
        val engine = _engines.value.firstOrNull { it.id == id } ?: return
        _default.value = engine
        prefs.edit().putLong(DEFAULT_ID, id).apply()
    }

    @Synchronized
    override fun addCustom(name: String, urlTemplate: String, keyword: String?): String? {
        val cleanName = name.trim()
        val template = urlTemplate.trim()
        val key = keyword?.trim()?.takeIf { it.isNotEmpty() }
        if (cleanName.isEmpty()) return "Enter a name"
        DefaultSearchEngines.validateTemplate(template)?.let { return it }
        if (key != null && key.any { it.isWhitespace() }) return "The shortcut cannot contain spaces"
        if (key != null && _engines.value.any { it.keyword.equals(key, ignoreCase = true) }) {
            return "The shortcut $key is already used"
        }
        val id = maxOf(System.currentTimeMillis(), (_engines.value.maxOfOrNull { it.id } ?: 0L) + 1)
        custom.value = custom.value + SearchEngine(id, cleanName, template, key, isBuiltIn = false)
        publish()
        return null
    }

    @Synchronized
    override fun remove(id: Long) {
        if (custom.value.none { it.id == id }) return // presets cannot be removed
        custom.value = custom.value.filterNot { it.id == id }
        publish()
        if (_default.value.id == id) setDefault(DefaultSearchEngines.presets.first().id)
    }

    private fun publish() {
        _engines.value = DefaultSearchEngines.presets + custom.value
        prefs.edit().putString(CUSTOM, JSONArray().also { array ->
            custom.value.forEach { e ->
                array.put(
                    JSONObject().put("id", e.id).put("name", e.name).put("url", e.urlTemplate)
                        .put("keyword", e.keyword ?: ""),
                )
            }
        }.toString()).apply()
    }

    private fun resolveDefault(id: Long): SearchEngine =
        _engines.value.firstOrNull { it.id == id } ?: DefaultSearchEngines.presets.first()

    private fun readCustom(): List<SearchEngine> {
        val json = prefs.getString(CUSTOM, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val url = o.optString("url")
                if (DefaultSearchEngines.validateTemplate(url) != null) return@mapNotNull null
                SearchEngine(o.optLong("id"), o.optString("name"), url, o.optString("keyword").ifEmpty { null }, false)
            }
        } catch (_: JSONException) {
            emptyList()
        }
    }

    private companion object {
        const val CUSTOM = "customEngines"
        const val DEFAULT_ID = "defaultEngineId"
    }
}
