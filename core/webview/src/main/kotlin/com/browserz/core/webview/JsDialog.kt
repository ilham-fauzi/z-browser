package com.browserz.core.webview

import android.webkit.JsPromptResult
import android.webkit.JsResult
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A page called alert(), confirm() or prompt(), or asked "leave site?" (beforeunload). The app shows a dialog and
 * calls [answer] exactly once; later calls are ignored. [canSuppress] offers "don't let this page show more
 * dialogs", Chrome's way out of pages that loop alerts.
 */
class JsDialog internal constructor(
    val kind: Kind,
    val host: String,
    val message: String,
    val defaultText: String?,
    val canSuppress: Boolean,
    private val result: JsResult,
    private val onAnswered: (suppress: Boolean) -> Unit,
) {
    enum class Kind { Alert, Confirm, Prompt, LeavePage }

    private val answered = AtomicBoolean(false)

    /** [confirmed] = OK / Leave; [text] is what was typed into a prompt; [suppress] blocks further dialogs from the page. */
    fun answer(confirmed: Boolean, text: String? = null, suppress: Boolean = false) {
        if (!answered.compareAndSet(false, true)) return
        when {
            !confirmed -> result.cancel()
            result is JsPromptResult -> result.confirm(text.orEmpty())
            else -> result.confirm()
        }
        onAnswered(suppress)
    }
}
