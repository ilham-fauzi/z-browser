package com.browserz.core.domain

/** One folder with its bookmarks; [folder] = null is the group of bookmarks without a folder. */
data class BookmarkGroup(val folder: String?, val items: List<Bookmark>)

object BookmarkFolders {
    const val MAX_NAME_LENGTH = 40

    /** Null when [name] is fine; [current] is the folder being renamed (its own name does not count as taken). */
    fun validateName(name: String, existing: List<String>, current: String? = null): String? {
        val clean = name.trim()
        return when {
            clean.isEmpty() -> "Enter a folder name"
            clean.length > MAX_NAME_LENGTH -> "Use at most $MAX_NAME_LENGTH characters"
            existing.any { it.equals(clean, ignoreCase = true) && !it.equals(current, ignoreCase = true) } -> "A folder named \"$clean\" already exists"
            else -> null
        }
    }

    /**
     * Folders first (alphabetical, empty ones included), then the bookmarks without a folder. A bookmark whose
     * folder is not in [folders] (for example after a failed write) is shown without a folder rather than lost.
     */
    fun group(bookmarks: List<Bookmark>, folders: List<String>): List<BookmarkGroup> {
        val known = folders.toSet()
        val inFolders = folders.sortedBy { it.lowercase() }.map { name -> BookmarkGroup(name, bookmarks.filter { it.folder == name }) }
        val loose = bookmarks.filter { it.folder == null || it.folder !in known }
        return inFolders + BookmarkGroup(null, loose)
    }
}
