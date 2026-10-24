package com.browserz.core.domain

object TabGroups {
    const val MAX_NAME_LENGTH = 24
    const val COLOR_COUNT = 8

    /** Trimmed and shortened name, or null when nothing usable is left (which means "no group"). */
    fun normalize(name: String?): String? = name?.trim()?.take(MAX_NAME_LENGTH)?.trim()?.ifEmpty { null }

    /** Stable palette slot for a group name, so a group keeps its colour across restarts. */
    fun colorIndex(name: String): Int = Math.floorMod(name.lowercase().hashCode(), COLOR_COUNT)

    /** Group names in use, in tab order, without repeats. */
    fun names(groups: List<String?>): List<String> = groups.filterNotNull().distinct()
}
