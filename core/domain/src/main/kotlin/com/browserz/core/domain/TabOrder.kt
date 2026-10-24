package com.browserz.core.domain

/** Ordering rules for tabs: pinned first, then each group's members side by side, everything else in place. */
object TabOrder {
    fun <T> pinnedFirst(items: List<T>, isPinned: (T) -> Boolean): List<T> =
        items.filter(isPinned) + items.filterNot(isPinned)

    /**
     * Where a tab goes after its pin state changed: just after the last pinned tab, so a newly pinned tab
     * joins the end of the pinned group and an unpinned one leads the normal tabs. [others] excludes the tab.
     */
    fun <T> indexAfterPinChange(others: List<T>, isPinned: (T) -> Boolean): Int = others.count(isPinned)

    /**
     * Pinned tabs first; then the rest, where all members of a group sit together at the place of the group's
     * first member. Ungrouped tabs and the order inside each group stay as they were.
     */
    fun <T> arrange(items: List<T>, isPinned: (T) -> Boolean, groupOf: (T) -> String?): List<T> {
        val rest = items.filterNot(isPinned)
        val seen = HashSet<String>()
        val arranged = ArrayList<T>(rest.size)
        for (item in rest) {
            val group = groupOf(item)
            if (group == null) arranged += item else if (seen.add(group)) arranged += rest.filter { groupOf(it) == group }
        }
        return items.filter(isPinned) + arranged
    }
}
