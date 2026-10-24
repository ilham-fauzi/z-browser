package com.browserz.core.domain

/** What an import file turned out to be, and the bookmarks in it. */
sealed interface BookmarkImportResult {
    data class Found(val source: String, val bookmarks: List<Bookmark>) : BookmarkImportResult
    data class Unrecognized(val message: String) : BookmarkImportResult
}

/**
 * Reads the bookmark files other browsers can give you: the HTML export (Chrome, Firefox, Edge, Safari, Samsung),
 * the Firefox JSON backup and the `Bookmarks` file of a Chrome or Edge profile. Only http(s) links are kept; a
 * folder tree is flattened to its top-level folders, as Browser-Z has one level.
 */
object BookmarkImport {
    private const val CHROME_EPOCH_OFFSET_MS = 11_644_473_600_000L
    private val chromeRoots = setOf("bookmark_bar", "other", "synced")
    private val firefoxRoots = setOf("menu________", "toolbar_____", "unfiled_____", "mobile______")

    fun parse(text: String): BookmarkImportResult {
        val trimmed = text.trimStart('﻿', ' ', '\n', '\r', '\t')
        if (trimmed.startsWith("{")) return parseJson(trimmed)
        if (trimmed.contains("<DL", ignoreCase = true) || trimmed.contains("NETSCAPE-Bookmark", ignoreCase = true)) {
            val list = BookmarkHtml.parse(trimmed)
            return if (list.isEmpty()) BookmarkImportResult.Unrecognized("No web bookmarks found in this file")
            else BookmarkImportResult.Found("HTML bookmarks file", list)
        }
        return BookmarkImportResult.Unrecognized("This is not a bookmarks file Browser-Z understands")
    }

    private fun parseJson(text: String): BookmarkImportResult {
        val root = try {
            MiniJson.parse(text) as? Map<*, *>
        } catch (_: IllegalArgumentException) {
            null
        } ?: return BookmarkImportResult.Unrecognized("This file is not valid JSON")
        val chrome = root["roots"] as? Map<*, *>
        val list: List<Bookmark>
        val source: String
        when {
            chrome != null -> {
                source = "Chrome / Edge bookmarks file"
                list = chrome.entries.filter { it.key in chromeRoots }.flatMap { (_, node) -> walkChrome(node as? Map<*, *>, null, true) }
            }
            root["children"] is List<*> -> {
                source = "Firefox backup"
                list = walkFirefox(root, null, true)
            }
            else -> return BookmarkImportResult.Unrecognized("This JSON file has no bookmarks in a known layout")
        }
        return if (list.isEmpty()) BookmarkImportResult.Unrecognized("No web bookmarks found in this file")
        else BookmarkImportResult.Found(source, list)
    }

    private fun walkChrome(node: Map<*, *>?, folder: String?, isRoot: Boolean): List<Bookmark> {
        if (node == null) return emptyList()
        val children = (node["children"] as? List<*>).orEmpty()
        return when (node["type"]) {
            "url" -> listOfNotNull(makeBookmark(node["url"] as? String, node["name"] as? String, chromeTime(node["date_added"]), folder))
            else -> {
                val name = (node["name"] as? String)?.trim().orEmpty()
                val next = if (isRoot) null else folder ?: name.take(BookmarkFolders.MAX_NAME_LENGTH).ifBlank { null }
                children.flatMap { walkChrome(it as? Map<*, *>, next, false) }
            }
        }
    }

    private fun walkFirefox(node: Map<*, *>, folder: String?, isRoot: Boolean): List<Bookmark> {
        val children = (node["children"] as? List<*>).orEmpty()
        val uri = node["uri"] as? String
        if (uri != null) return listOfNotNull(makeBookmark(uri, node["title"] as? String, firefoxTime(node["dateAdded"]), folder))
        val guid = node["guid"] as? String
        val title = (node["title"] as? String)?.trim().orEmpty()
        val isTopLevel = isRoot || guid in firefoxRoots
        val next = if (isTopLevel) null else folder ?: title.take(BookmarkFolders.MAX_NAME_LENGTH).ifBlank { null }
        return children.flatMap { (it as? Map<*, *>)?.let { child -> walkFirefox(child, next, false) }.orEmpty() }
    }

    private fun makeBookmark(url: String?, title: String?, createdAt: Long, folder: String?): Bookmark? {
        val clean = url?.trim() ?: return null
        if (!LinkTargets.isWeb(clean)) return null
        return Bookmark(clean, title?.trim().orEmpty().ifBlank { clean }, createdAt, folder)
    }

    /** Chrome stores microseconds since 1601-01-01 as a string. */
    private fun chromeTime(value: Any?): Long {
        val micros = (value as? String)?.toLongOrNull() ?: (value as? Double)?.toLong() ?: return 0L
        return (micros / 1000 - CHROME_EPOCH_OFFSET_MS).coerceAtLeast(0L)
    }

    /** Firefox stores microseconds since 1970. */
    private fun firefoxTime(value: Any?): Long = ((value as? Double)?.toLong() ?: 0L) / 1000
}
