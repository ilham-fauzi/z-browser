package com.browserz.core.data

import android.content.SharedPreferences
import com.browserz.core.domain.Bookmark
import com.browserz.core.domain.BookmarkEdit
import com.browserz.core.domain.BookmarkEditResult
import com.browserz.core.domain.BookmarkFolders
import com.browserz.core.domain.BookmarkRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class PrefsBookmarkRepository(private val prefs: SharedPreferences) : BookmarkRepository {

    private val _bookmarks = MutableStateFlow(readBookmarks())
    private val _folders = MutableStateFlow(readFolders())
    override val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()
    override val folders: StateFlow<List<String>> = _folders.asStateFlow()

    @Synchronized
    override fun toggle(url: String, title: String) {
        val existing = _bookmarks.value.any { it.url == url }
        _bookmarks.value = if (existing) {
            _bookmarks.value.filterNot { it.url == url }
        } else {
            listOf(Bookmark(url, title.ifBlank { url }, System.currentTimeMillis())) + _bookmarks.value
        }
        writeBookmarks()
    }

    @Synchronized
    override fun remove(url: String) {
        _bookmarks.value = _bookmarks.value.filterNot { it.url == url }
        writeBookmarks()
    }

    @Synchronized
    override fun edit(oldUrl: String, title: String, address: String): String? {
        if (_bookmarks.value.none { it.url == oldUrl }) return "That bookmark no longer exists"
        return when (val r = BookmarkEdit.validate(title, address, _bookmarks.value, oldUrl)) {
            is BookmarkEditResult.Invalid -> r.message
            is BookmarkEditResult.Valid -> {
                _bookmarks.value = _bookmarks.value.map { if (it.url == oldUrl) it.copy(title = r.title, url = r.url) else it }
                writeBookmarks()
                null
            }
        }
    }

    @Synchronized
    override fun move(url: String, folder: String?) {
        if (folder != null && folder !in _folders.value) return
        _bookmarks.value = _bookmarks.value.map { if (it.url == url) it.copy(folder = folder) else it }
        writeBookmarks()
    }

    @Synchronized
    override fun addFolder(name: String): String? {
        BookmarkFolders.validateName(name, _folders.value)?.let { return it }
        _folders.value = _folders.value + name.trim()
        writeFolders()
        return null
    }

    @Synchronized
    override fun renameFolder(old: String, new: String): String? {
        if (old !in _folders.value) return "That folder no longer exists"
        BookmarkFolders.validateName(new, _folders.value, current = old)?.let { return it }
        val clean = new.trim()
        _folders.value = _folders.value.map { if (it == old) clean else it }
        _bookmarks.value = _bookmarks.value.map { if (it.folder == old) it.copy(folder = clean) else it }
        writeFolders()
        writeBookmarks()
        return null
    }

    @Synchronized
    override fun deleteFolder(name: String) {
        _folders.value = _folders.value.filterNot { it == name }
        _bookmarks.value = _bookmarks.value.map { if (it.folder == name) it.copy(folder = null) else it }
        writeFolders()
        writeBookmarks()
    }

    @Synchronized
    override fun importAll(items: List<Bookmark>): Int {
        val known = _bookmarks.value.mapTo(HashSet()) { it.url }
        val fresh = items.filter { known.add(it.url) }
        if (fresh.isEmpty()) return 0
        val newFolders = fresh.mapNotNull { it.folder }.distinct().filter { it !in _folders.value }
        if (newFolders.isNotEmpty()) {
            _folders.value = _folders.value + newFolders
            writeFolders()
        }
        _bookmarks.value = _bookmarks.value + fresh
        writeBookmarks()
        return fresh.size
    }

    private fun writeBookmarks() {
        val array = JSONArray()
        _bookmarks.value.forEach {
            array.put(JSONObject().put("url", it.url).put("title", it.title).put("at", it.createdAt).put("folder", it.folder ?: ""))
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private fun writeFolders() {
        prefs.edit().putString(KEY_FOLDERS, JSONArray(_folders.value).toString()).apply()
    }

    private fun readBookmarks(): List<Bookmark> {
        val json = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val url = o.optString("url").ifEmpty { return@mapNotNull null }
                Bookmark(url, o.optString("title", url), o.optLong("at"), o.optString("folder").ifEmpty { null })
            }
        } catch (_: JSONException) {
            emptyList()
        }
    }

    private fun readFolders(): List<String> {
        val json = prefs.getString(KEY_FOLDERS, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { array.getString(it) }.filter { it.isNotBlank() }.distinct()
        } catch (_: JSONException) {
            emptyList()
        }
    }

    private companion object {
        const val KEY = "bookmarks"
        const val KEY_FOLDERS = "bookmarkFolders"
    }
}
