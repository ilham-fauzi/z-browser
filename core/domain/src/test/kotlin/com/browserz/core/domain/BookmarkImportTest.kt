package com.browserz.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookmarkImportTest {
    private fun found(r: BookmarkImportResult) = (r as BookmarkImportResult.Found)

    @Test
    fun `given a Chrome Bookmarks file when importing then roots are dropped and folders kept`() {
        val json = """
            {"roots":{"bookmark_bar":{"type":"folder","name":"Bookmarks bar","children":[
              {"type":"url","name":"Loose","url":"https://loose.com","date_added":"13350000000000000"},
              {"type":"folder","name":"News","children":[
                {"type":"url","name":"N","url":"https://n.com"},
                {"type":"folder","name":"Deep","children":[{"type":"url","name":"D","url":"https://d.com"}]}]}]},
              "other":{"type":"folder","name":"Other","children":[{"type":"url","name":"Js","url":"javascript:1"}]},
              "synced":{"type":"folder","name":"Mobile","children":[]}},"version":1}
        """.trimIndent()
        val r = found(BookmarkImport.parse(json))
        assertEquals("Chrome / Edge bookmarks file", r.source)
        val byUrl = r.bookmarks.associateBy { it.url }
        assertEquals(null, byUrl.getValue("https://loose.com").folder)
        assertEquals("News", byUrl.getValue("https://n.com").folder)
        assertEquals("News", byUrl.getValue("https://d.com").folder)
        assertEquals(3, byUrl.size)
        assertEquals(13350000000000000L / 1000 - 11_644_473_600_000L, byUrl.getValue("https://loose.com").createdAt)
    }

    @Test
    fun `given a Firefox backup when importing then menu toolbar roots are dropped and folders kept`() {
        val json = """
            {"guid":"root________","title":"","children":[
              {"guid":"menu________","title":"Bookmarks Menu","children":[
                {"guid":"a","title":"Fox","typeCode":1,"uri":"https://fox.org","dateAdded":1700000000000000},
                {"guid":"f","title":"Recipes","typeCode":2,"children":[{"guid":"b","title":"Soup","typeCode":1,"uri":"https://soup.com"}]}]},
              {"guid":"toolbar_____","title":"Toolbar","children":[{"guid":"c","title":"Mail","typeCode":1,"uri":"https://mail.com"},{"guid":"s","typeCode":3}]},
              {"guid":"unfiled_____","title":"Other","children":[{"guid":"p","title":"Places","typeCode":1,"uri":"place:sort=8"}]}]}
        """.trimIndent()
        val r = found(BookmarkImport.parse(json))
        assertEquals("Firefox backup", r.source)
        val byUrl = r.bookmarks.associateBy { it.url }
        assertEquals(null, byUrl.getValue("https://fox.org").folder)
        assertEquals(1_700_000_000_000L, byUrl.getValue("https://fox.org").createdAt)
        assertEquals("Recipes", byUrl.getValue("https://soup.com").folder)
        assertEquals(null, byUrl.getValue("https://mail.com").folder)
        assertEquals(3, byUrl.size)
    }

    @Test
    fun `given an HTML export when importing then the HTML reader is used`() {
        val html = "<!DOCTYPE NETSCAPE-Bookmark-file-1><DL><p><DT><A HREF=\"https://a.com\">A</A></DL>"
        assertEquals(listOf("https://a.com"), found(BookmarkImport.parse(html)).bookmarks.map { it.url })
    }

    @Test
    fun `given unrelated or broken files when importing then it says so instead of failing`() {
        assertTrue(BookmarkImport.parse("hello world") is BookmarkImportResult.Unrecognized)
        assertTrue(BookmarkImport.parse("{\"a\":") is BookmarkImportResult.Unrecognized)
        assertTrue(BookmarkImport.parse("{\"a\":1}") is BookmarkImportResult.Unrecognized)
        assertTrue(BookmarkImport.parse("<DL></DL>") is BookmarkImportResult.Unrecognized)
    }
}
