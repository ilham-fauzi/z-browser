package com.browserz.core.domain

/** A tab that was closed and can be brought back. */
data class ClosedTab(val url: String, val title: String, val pinned: Boolean, val group: String?)

/** The "recently closed" list: newest first, a few entries, one per address. */
object ClosedTabs {
    const val MAX = 10

    fun push(list: List<ClosedTab>, tab: ClosedTab): List<ClosedTab> {
        if (tab.url.isBlank()) return list
        return (listOf(tab) + list.filterNot { it.url == tab.url }).take(MAX)
    }

    /** The newest closed tab and what is left. */
    fun pop(list: List<ClosedTab>): Pair<ClosedTab?, List<ClosedTab>> = list.firstOrNull() to list.drop(1)
}
