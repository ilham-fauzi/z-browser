package com.browserz.core.domain

import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryDaysTest {
    private val zone = ZoneId.of("UTC")
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = ZonedDateTime.of(y, m, d, h, 0, 0, 0, zone).toInstant().toEpochMilli()
    private fun entry(url: String, t: Long) = HistoryEntry(url, url, t)

    @Test
    fun `given entries over several days when grouping then labels follow Chrome and order is newest first`() {
        val now = at(2026, 10, 17)
        val entries = listOf(entry("a", at(2026, 10, 17, 9)), entry("b", at(2026, 10, 16)), entry("c", at(2026, 10, 10)), entry("d", at(2025, 12, 31)))
        val days = HistoryDays.group(entries, now, zone, Locale.US)
        assertEquals(listOf("Today", "Yesterday", "Saturday, Oct 10", "Wednesday, Dec 31, 2025"), days.map { it.label })
    }

    @Test
    fun `given entries on one day when grouping then they share a header`() {
        val now = at(2026, 10, 17)
        val days = HistoryDays.group(listOf(entry("a", at(2026, 10, 17, 11)), entry("b", at(2026, 10, 17, 8))), now, zone, Locale.US)
        assertEquals(1, days.size)
        assertEquals(listOf("a", "b"), days[0].entries.map { it.url })
    }

    @Test
    fun `given a query when filtering then title and address match case-insensitively`() {
        val list = listOf(HistoryEntry("https://x.com", "Cats", 1), HistoryEntry("https://dogs.com", "Home", 2))
        assertEquals(listOf("Cats"), HistoryDays.filter(list, "CAT").map { it.title })
        assertEquals(listOf("Home"), HistoryDays.filter(list, "dogs").map { it.title })
        assertEquals(2, HistoryDays.filter(list, " ").size)
    }
}
