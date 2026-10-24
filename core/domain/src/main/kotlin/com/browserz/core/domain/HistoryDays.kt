package com.browserz.core.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** History entries of one calendar day, headed the way Chrome does ("Today", "Yesterday", "Friday, Oct 16"). */
data class HistoryDay(val label: String, val entries: List<HistoryEntry>)

object HistoryDays {
    /** [entries] are expected newest first; the order is kept, entries are only grouped by their local date. */
    fun group(entries: List<HistoryEntry>, now: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): List<HistoryDay> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val format = DateTimeFormatter.ofPattern("EEEE, MMM d", locale)
        val yearFormat = DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", locale)
        fun label(day: LocalDate) = when (day) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> day.format(if (day.year == today.year) format else yearFormat)
        }
        return entries
            .groupBy { Instant.ofEpochMilli(it.visitedAt).atZone(zone).toLocalDate() }
            .map { (day, list) -> day to list }
            .sortedByDescending { it.first }
            .map { (day, list) -> HistoryDay(label(day), list) }
    }

    /** Case-insensitive match on title or address; a blank query keeps everything. */
    fun filter(entries: List<HistoryEntry>, query: String): List<HistoryEntry> {
        val q = query.trim()
        return if (q.isEmpty()) entries else entries.filter { it.title.contains(q, true) || it.url.contains(q, true) }
    }
}
