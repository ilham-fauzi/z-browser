package com.browserz.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuildSuggestionsTest {
    private val engine = DefaultSearchEngines.presets.first()
    private val bookmarks = listOf(Bookmark("https://kotlinlang.org", "Kotlin", 1))
    private val history = listOf(
        HistoryEntry("https://kotlinlang.org", "Kotlin", 2),
        HistoryEntry("https://news.example.com/kotlin-2", "Kotlin 2 released", 3),
    )

    @Test
    fun `given a query when suggesting then search comes first and duplicates collapse`() {
        val result = BuildSuggestions()("kotlin", bookmarks, history, engine)
        assertTrue(result.first() is Suggestion.Search)
        assertEquals(2, result.filterIsInstance<Suggestion.Page>().size)
        assertTrue((result[1] as Suggestion.Page).isBookmark)
    }

    @Test
    fun `given an empty query when suggesting then nothing is shown`() {
        assertEquals(emptyList(), BuildSuggestions()("  ", bookmarks, history, engine))
    }
}
