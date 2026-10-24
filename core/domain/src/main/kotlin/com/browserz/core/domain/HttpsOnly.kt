package com.browserz.core.domain

/** HTTPS-first browsing: plain http addresses on the public internet are tried over https instead. */
object HttpsOnly {
    /** Hosts that never get upgraded: this device, private networks and single-label (intranet) names. */
    fun isLocal(host: String): Boolean {
        val h = host.trim().lowercase().removePrefix("[").removeSuffix("]")
        if (h.isEmpty()) return true
        if (h == "localhost" || h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".internal") || h.endsWith(".lan")) return true
        if (h.contains(':')) return h == "::1" || h.startsWith("fe80:") || h.startsWith("fc") || h.startsWith("fd")
        val parts = h.split('.')
        if (parts.size == 4 && parts.all { p -> p.toIntOrNull()?.let { it in 0..255 } == true }) {
            val a = parts[0].toInt()
            val b = parts[1].toInt()
            return a == 10 || a == 127 || (a == 192 && b == 168) || (a == 172 && b in 16..31) || (a == 169 && b == 254) || a == 0
        }
        return !h.contains('.')
    }

    /** `https://…` for a public `http://…` address, or null when the address should stay as it is. */
    fun upgrade(url: String): String? {
        if (!url.startsWith("http://", ignoreCase = true)) return null
        if (isLocal(Hosts.hostOf(url))) return null
        return "https://" + url.substring("http://".length)
    }
}
