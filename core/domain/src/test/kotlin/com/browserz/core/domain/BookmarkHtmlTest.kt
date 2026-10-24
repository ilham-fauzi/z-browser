package com.browserz.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class BookmarkHtmlTest {
    @Test
    fun `given bookmarks and folders when exported and parsed then nothing is lost`() {
        val items = listOf(
            Bookmark("https://a.com/?x=1&y=2", "A & <B>", 5_000, null),
            Bookmark("https://b.com", "Bee", 7_000, "Work"),
        )
        val parsed = BookmarkHtml.parse(BookmarkHtml.export(items, listOf("Work", "Empty")))
        assertEquals(items.sortedBy { it.url }, parsed.sortedBy { it.url })
    }

    @Test
    fun `given a Chrome export when parsing then root folders are dropped and sub folders kept`() {
        val html = """
            <DL><p>
              <DT><H3 ADD_DATE="1" PERSONAL_TOOLBAR_FOLDER="true">Bookmarks bar</H3>
              <DL><p>
                <DT><A HREF="https://loose.com" ADD_DATE="10">Loose</A>
                <DT><H3>News</H3>
                <DL><p>
                  <DT><A HREF="https://n.com">N</A>
                  <DT><H3>Deep</H3>
                  <DL><p><DT><A HREF="https://d.com">D</A></DL><p>
                </DL><p>
              </DL><p>
              <DT><A HREF="javascript:alert(1)">Bad</A>
            </DL><p>
        """.trimIndent()
        val byUrl = BookmarkHtml.parse(html).associateBy { it.url }
        assertEquals(null, byUrl.getValue("https://loose.com").folder)
        assertEquals(10_000L, byUrl.getValue("https://loose.com").createdAt)
        assertEquals("News", byUrl.getValue("https://n.com").folder)
        assertEquals("News", byUrl.getValue("https://d.com").folder)
        assertEquals(3, byUrl.size)
    }
}
