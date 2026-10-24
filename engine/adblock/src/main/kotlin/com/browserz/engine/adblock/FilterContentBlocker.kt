package com.browserz.engine.adblock

import com.browserz.core.domain.BlockDecision
import com.browserz.core.domain.ContentBlocker
import com.browserz.core.domain.RequestType
import java.util.concurrent.atomic.AtomicReference

/** [ContentBlocker] backed by the engine currently held in [engineRef]; swapped atomically on updates. */
class FilterContentBlocker(private val engineRef: AtomicReference<FilterEngine>) : ContentBlocker {

    override fun check(url: String, sourceUrl: String, type: RequestType): BlockDecision {
        val engine = engineRef.get()
        val sourceHost = if (sourceUrl.isEmpty()) null else FilterEngine.hostOf(sourceUrl.lowercase()).ifEmpty { null }
        return try {
            if (engine.shouldBlock(url, sourceHost, type)) BlockDecision.Block else BlockDecision.Allow
        } catch (_: RuntimeException) {
            BlockDecision.Allow // a bad rule must never break page loading
        }
    }

    override fun cosmeticCss(host: String): String =
        try {
            engineRef.get().cosmeticCss(host)
        } catch (_: RuntimeException) {
            ""
        }

    override fun genericCss(): String = engineRef.get().genericCss()

    override fun siteCss(host: String, genericApplied: Boolean): String =
        try {
            engineRef.get().siteCss(host, genericApplied)
        } catch (_: RuntimeException) {
            ""
        }

    override fun genericSkipHosts(): Set<String> = engineRef.get().genericSkipHosts()

    override fun proceduralRules(host: String): String =
        try {
            engineRef.get().proceduralRules(host)
        } catch (_: RuntimeException) {
            ""
        }
}
