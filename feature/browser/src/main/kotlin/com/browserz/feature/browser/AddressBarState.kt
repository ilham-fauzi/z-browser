package com.browserz.feature.browser

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.browserz.core.domain.TabInfo

/** State of the address bar, shared by the toolbar and the suggestions panel. */
@Stable
class AddressBarState {
    var value by mutableStateOf(TextFieldValue(""))
    var focused by mutableStateOf(false)
    val focusRequester = FocusRequester()

    /** The text that was on screen when focus arrived; the tap that focuses the field re-reports it. */
    private var staleText: String? = null

    fun onFocusGained(tab: TabInfo?) {
        if (focused) return
        focused = true
        val url = tab?.url.orEmpty()
        staleText = displayAddress(url).takeIf { it != url }
        value = TextFieldValue(url, TextRange(0, url.length))
    }

    /** Applies an edit from the text field, ignoring the stale event produced by the focusing tap. */
    fun onEdit(edit: TextFieldValue) {
        if (!focused) return
        val stale = staleText
        staleText = null
        if (stale != null && edit.text == stale) return
        value = edit
    }

    fun onFocusLost() {
        focused = false
    }

    /** Safe to call before the field is composed (returns false then). */
    fun requestFocus(): Boolean = try {
        focusRequester.requestFocus()
        true
    } catch (_: IllegalStateException) {
        false
    }
}

/** `https://www.example.com/a/` -> `example.com/a` for display when the field is not being edited. */
fun displayAddress(url: String): String =
    if (url.startsWith("file://")) "Saved page" else url.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')

fun tabTitle(tab: TabInfo): String = when {
    tab.title.isNotBlank() -> tab.title
    tab.isBlank -> "New tab"
    else -> displayAddress(tab.url)
}
