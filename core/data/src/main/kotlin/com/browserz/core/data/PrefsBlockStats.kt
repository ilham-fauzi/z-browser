package com.browserz.core.data

import android.content.SharedPreferences
import com.browserz.core.domain.BlockStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Lifetime counter. Writes are batched: the value reaches disk at most every [FLUSH_EVERY] blocks. */
class PrefsBlockStats(private val prefs: SharedPreferences) : BlockStats {
    private val _total = MutableStateFlow(prefs.getLong(KEY, 0L))
    override val totalBlocked: StateFlow<Long> = _total.asStateFlow()
    private var unsaved = 0

    @Synchronized
    override fun add(count: Int) {
        if (count <= 0) return
        _total.value += count
        unsaved += count
        if (unsaved >= FLUSH_EVERY) flush()
    }

    /** Call when the app goes to the background so nothing is lost. */
    @Synchronized
    fun flush() {
        if (unsaved == 0) return
        unsaved = 0
        prefs.edit().putLong(KEY, _total.value).apply()
    }

    private companion object {
        const val KEY = "totalBlocked"
        const val FLUSH_EVERY = 25
    }
}
