package com.browserz.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BookmarkFoldersTest {
    private fun b(url: String, folder: String? = null) = Bookmark(url, url, 0, folder)

    @Test
    fun `given bookmarks and folders when grouping then folders come first alphabetically and loose ones last`() {
        val groups = BookmarkFolders.group(
            listOf(b("1", "Work"), b("2"), b("3", "Dev"), b("4", "Work")),
            listOf("Work", "Dev", "Empty"),
        )
        assertEquals(listOf("Dev", "Empty", "Work", null), groups.map { it.folder })
        assertEquals(listOf("3"), groups[0].items.map { it.url })
        assertEquals(emptyList(), groups[1].items)
        assertEquals(listOf("1", "4"), groups[2].items.map { it.url })
        assertEquals(listOf("2"), groups[3].items.map { it.url })
    }

    @Test
    fun `given a bookmark in an unknown folder when grouping then it is shown without a folder`() {
        val groups = BookmarkFolders.group(listOf(b("1", "Gone")), emptyList())
        assertEquals(listOf(null), groups.map { it.folder })
        assertEquals(listOf("1"), groups.single().items.map { it.url })
    }

    @Test
    fun `given folder names when validating then blank, long and duplicate names are refused`() {
        assertNull(BookmarkFolders.validateName("Reading", listOf("Work")))
        assertEquals("Enter a folder name", BookmarkFolders.validateName("   ", emptyList()))
        assertEquals("Use at most 40 characters", BookmarkFolders.validateName("x".repeat(41), emptyList()))
        assertEquals("A folder named \"work\" already exists", BookmarkFolders.validateName("work", listOf("Work")))
    }

    @Test
    fun `given a rename when validating then keeping the same name is allowed`() {
        assertNull(BookmarkFolders.validateName("Work", listOf("Work", "Dev"), current = "Work"))
        assertNull(BookmarkFolders.validateName("WORK", listOf("Work", "Dev"), current = "Work"))
        assertEquals("A folder named \"Dev\" already exists", BookmarkFolders.validateName("Dev", listOf("Work", "Dev"), current = "Work"))
    }
}
