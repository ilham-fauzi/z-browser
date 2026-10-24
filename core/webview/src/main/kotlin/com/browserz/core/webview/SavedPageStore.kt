package com.browserz.core.webview

import android.content.Context
import android.content.SharedPreferences
import com.browserz.core.domain.SavedPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File

/** Web archives (.mht) in the app's private storage plus a small index of them. */
internal class SavedPageStore(context: Context, private val prefs: SharedPreferences) {
    val dir = File(context.filesDir, "saved")
    private val _pages = MutableStateFlow(read())
    val pages: StateFlow<List<SavedPage>> = _pages.asStateFlow()

    fun fileFor(id: String): File = File(dir, "$id.mht")

    /** True when [url] points at one of our archives; the only file: URLs the browser ever opens. */
    fun owns(url: String): Boolean = url.startsWith("file://") && url.contains("/saved/") && url.endsWith(".mht") && !url.contains("..")

    fun prepare(): Boolean = dir.isDirectory || dir.mkdirs()

    fun add(page: SavedPage) {
        _pages.value = listOf(page) + _pages.value.filterNot { it.id == page.id }
        write()
    }

    fun remove(id: String) {
        fileFor(id).delete()
        _pages.value = _pages.value.filterNot { it.id == id }
        write()
    }

    private fun write() {
        val array = JSONArray()
        _pages.value.forEach { array.put(JSONObject().put("id", it.id).put("title", it.title).put("url", it.url).put("at", it.savedAt)) }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private fun read(): List<SavedPage> = try {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").ifEmpty { return@mapNotNull null }
            if (!File(dir, "$id.mht").exists()) return@mapNotNull null
            SavedPage(id, o.optString("title"), o.optString("url"), o.optLong("at"))
        }
    } catch (_: JSONException) {
        emptyList()
    }

    private companion object {
        const val KEY = "savedPages"
    }
}
