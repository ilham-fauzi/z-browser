package com.browserz.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookmarkEditTest {
    private val saved = listOf(Bookmark("https://a.com", "A", 1), Bookmark("https://b.com", "B", 2))

    @Test
    fun `given a new title and address when validating then both are returned trimmed`() {
        val r = BookmarkEdit.validate("  Fresh  ", "c.com", saved, "https://a.com")
        assertEquals(BookmarkEditResult.Valid("Fresh", "https://c.com"), r)
    }

    @Test
    fun `given a blank title when validating then the address is used`() {
        val r = BookmarkEdit.validate(" ", "https://a.com", saved, "https://a.com")
        assertEquals(BookmarkEditResult.Valid("https://a.com", "https://a.com"), r)
    }

    @Test
    fun `given another bookmark's address when validating then it is refused`() {
        assertTrue(BookmarkEdit.validate("x", "https://b.com", saved, "https://a.com") is BookmarkEditResult.Invalid)
    }

    @Test
    fun `given a non-web address when validating then it is refused`() {
        assertTrue(BookmarkEdit.validate("x", "javascript:alert(1)", saved, "https://a.com") is BookmarkEditResult.Invalid)
    }
}
