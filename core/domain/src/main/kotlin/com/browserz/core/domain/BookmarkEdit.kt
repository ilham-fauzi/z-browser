package com.browserz.core.domain

/** Result of checking what the user typed in the "Edit bookmark" dialog. */
sealed interface BookmarkEditResult {
    data class Valid(val title: String, val url: String) : BookmarkEditResult
    data class Invalid(val message: String) : BookmarkEditResult
}

object BookmarkEdit {
    const val MAX_TITLE_LENGTH = 120

    /**
     * A blank title falls back to the address; the address must be a web address (a bare domain gets https) and
     * must not belong to another bookmark. [currentUrl] is the bookmark being edited, so keeping its address is fine.
     */
    fun validate(title: String, address: String, existing: List<Bookmark>, currentUrl: String): BookmarkEditResult {
        val url = NtpShortcuts.normalizeUrl(address) ?: return BookmarkEditResult.Invalid("Enter a valid web address")
        if (url != currentUrl && existing.any { it.url == url }) return BookmarkEditResult.Invalid("That address is already bookmarked")
        return BookmarkEditResult.Valid(title.trim().take(MAX_TITLE_LENGTH).ifBlank { url }, url)
    }
}
