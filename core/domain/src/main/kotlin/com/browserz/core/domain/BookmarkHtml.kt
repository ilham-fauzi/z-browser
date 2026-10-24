package com.browserz.core.domain

/** Reads and writes the "Netscape bookmark file" format that Chrome, Firefox and Edge all import and export. */
object BookmarkHtml {
    private val token = Regex(
        "<H3[^>]*>(.*?)</H3>|<A\\s[^>]*?HREF\\s*=\\s*\"([^\"]*)\"[^>]*>(.*?)</A>|<DL[^>]*>|</DL>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val addDate = Regex("ADD_DATE\\s*=\\s*\"(\\d+)\"", RegexOption.IGNORE_CASE)

    /** Chrome and Firefox wrap everything in these; they are not folders the user made. */
    private val rootNames = setOf("bookmarks", "bookmarks bar", "bookmarks toolbar", "other bookmarks", "mobile bookmarks", "bookmarks menu")

    fun export(bookmarks: List<Bookmark>, folders: List<String>): String = buildString {
        appendLine("<!DOCTYPE NETSCAPE-Bookmark-file-1>")
        appendLine("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">")
        appendLine("<TITLE>Bookmarks</TITLE>")
        appendLine("<H1>Bookmarks</H1>")
        appendLine("<DL><p>")
        fun entry(b: Bookmark, indent: String) =
            appendLine("$indent<DT><A HREF=\"${escape(b.url)}\" ADD_DATE=\"${b.createdAt / 1000}\">${escape(b.title)}</A>")
        for (group in BookmarkFolders.group(bookmarks, folders)) {
            val folder = group.folder
            if (folder == null) {
                group.items.forEach { entry(it, "    ") }
            } else {
                appendLine("    <DT><H3>${escape(folder)}</H3>")
                appendLine("    <DL><p>")
                group.items.forEach { entry(it, "        ") }
                appendLine("    </DL><p>")
            }
        }
        appendLine("</DL><p>")
    }

    /** Only http(s) links are kept. Nested folders collapse into their top-level folder (we have one level). */
    fun parse(html: String): List<Bookmark> {
        val result = ArrayList<Bookmark>()
        val stack = ArrayList<String?>() // one entry per open <DL>; the name of the <H3> that introduced it
        var pending: String? = null
        for (m in token.findAll(html)) {
            val v = m.value
            when {
                v.startsWith("<H3", ignoreCase = true) -> pending = unescape(m.groupValues[1]).trim()
                v.startsWith("<A", ignoreCase = true) -> {
                    val url = unescape(m.groupValues[2]).trim()
                    if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) continue
                    val title = unescape(m.groupValues[3].replace(Regex("<[^>]*>"), "")).trim().ifBlank { url }
                    val at = addDate.find(v)?.groupValues?.get(1)?.toLongOrNull()?.times(1000) ?: 0L
                    val names = stack.filterNotNull()
                    val folder = (if (names.firstOrNull()?.lowercase() in rootNames) names.getOrNull(1) else names.firstOrNull())
                        ?.take(BookmarkFolders.MAX_NAME_LENGTH)?.ifBlank { null }
                    result += Bookmark(url, title, at, folder)
                }
                v.startsWith("</DL", ignoreCase = true) -> if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                else -> { stack.add(pending); pending = null }
            }
        }
        return result
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun unescape(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&#x27;", "'").replace("&amp;", "&")
}
