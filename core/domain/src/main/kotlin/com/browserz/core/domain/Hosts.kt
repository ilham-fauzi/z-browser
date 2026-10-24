package com.browserz.core.domain

/** Small URL helpers shared by the filter engine and the pop-up policy. No Android APIs. */
object Hosts {
    private val TWO_LEVEL_SLD = setOf("co", "com", "net", "org", "gov", "ac", "edu", "or", "go", "web", "sch", "mil")

    /** Lower-case host of an http(s) URL, or "" when there is none. */
    fun hostOf(url: String): String {
        val afterScheme = url.indexOf("://").let { if (it >= 0) url.substring(it + 3) else return "" }
        val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        return authority.substringAfter('@').substringBefore(':').lowercase()
    }

    /** `a.b.example.co.id` -> `example.co.id` (a small built-in suffix list, not the full Public Suffix List). */
    fun registrableDomain(host: String): String {
        if (host.all { it.isDigit() || it == '.' } || host.contains(':')) return host
        val labels = host.split('.')
        if (labels.size <= 2) return host
        val last = labels.last()
        val second = labels[labels.size - 2]
        val take = if (last.length == 2 && second in TWO_LEVEL_SLD) 3 else 2
        return labels.takeLast(take).joinToString(".")
    }

    fun sameSite(hostA: String, hostB: String): Boolean =
        hostA.isNotEmpty() && hostB.isNotEmpty() && registrableDomain(hostA) == registrableDomain(hostB)
}
