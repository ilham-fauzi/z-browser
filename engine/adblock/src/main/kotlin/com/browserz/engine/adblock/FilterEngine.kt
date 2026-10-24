package com.browserz.engine.adblock

import com.browserz.core.domain.Hosts
import com.browserz.core.domain.RequestType
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.regex.Pattern

/**
 * Adblock-Plus style filter engine. Pure Kotlin (no Android APIs) so it is unit-testable on the JVM.
 *
 * Supported: `||host^`, `|` anchors, `*` and `^`, `@@` exceptions, `$third-party`, resource types,
 * `$domain=`, element-hiding rules (`##selector`, `domain##selector`) with their exceptions (`#@#`,
 * `@@||host^$generichide` / `$elemhide`), and domain-specific procedural rules (`:has-text()`,
 * `:-abp-contains()`, `:-abp-has()`, `:upward()`, `:remove()`; see [ProceduralSelector]). Rules using
 * options the WebView cannot honour (redirect, csp, removeparam, ...) are skipped, never half-applied.
 *
 * Instances are immutable after [parse], so one instance can be shared by every WebView IO thread.
 */
class FilterEngine private constructor(
    private val blocking: RuleSet,
    private val exceptions: RuleSet,
    private val genericSelectors: List<String>,
    private val domainSelectors: Map<String, List<String>>,
    /** Selectors that must not be hidden on a domain (`domain#@#selector`). */
    private val domainExceptions: Map<String, Set<String>>,
    /** Domain -> compiled procedural rules (JSON programs for the page script). */
    private val domainProcedural: Map<String, List<String>>,
    /** `@@||host^$generichide`: no generic hiding on these sites. */
    private val genericHideHosts: Set<String>,
    /** `@@||host^$elemhide`: no hiding at all on these sites. */
    private val elemHideHosts: Set<String>,
    val networkRuleCount: Int,
    val cosmeticRuleCount: Int,
) {
    private val genericCssText: String = buildString { for (sel in genericSelectors) append(sel).append(HIDE) }

    fun shouldBlock(url: String, sourceHost: String?, type: RequestType): Boolean {
        if (type == RequestType.Document) return false
        val lower = url.lowercase()
        val host = hostOf(lower)
        if (host.isEmpty()) return false
        val source = sourceHost?.lowercase()?.takeIf { it.isNotEmpty() }
        val request = Request(
            url = lower,
            host = host,
            sourceHost = source,
            thirdParty = source != null && registrableDomain(host) != registrableDomain(source),
            typeBit = typeBit(type),
        )
        if (!blocking.matches(request)) return false
        return !exceptions.matches(request)
    }

    /** All hiding CSS for [host]: the generic part (minus this site's exceptions) and its own rules. */
    fun cosmeticCss(host: String): String = siteCss(host, genericApplied = false)

    /**
     * Hiding CSS for [host]. With [genericApplied] the generic part was already injected for this page (only
     * done on sites outside [genericSkipHosts]), so just the site's own rules are returned.
     */
    fun siteCss(host: String, genericApplied: Boolean): String {
        val h = host.lowercase()
        val hostSuffixes = suffixes(h)
        if (hostSuffixes.any { it in elemHideHosts }) return ""
        val excepted = exceptionsFor(hostSuffixes)
        val css = StringBuilder()
        if (!genericApplied && hostSuffixes.none { it in genericHideHosts }) {
            if (excepted.isEmpty()) css.append(genericCssText) else genericSelectors.forEach { if (it !in excepted) css.append(it).append(HIDE) }
        }
        for (suffix in hostSuffixes) {
            domainSelectors[suffix]?.forEach { if (it !in excepted) css.append(it).append(HIDE) }
        }
        return css.toString()
    }

    fun genericCss(): String = genericCssText

    /** Sites where the generic CSS must not be injected blindly: they except some of it, or all hiding. */
    fun genericSkipHosts(): Set<String> = domainExceptions.keys + genericHideHosts + elemHideHosts

    /** Procedural rules for [host] as a JSON array of programs; empty when there are none. */
    fun proceduralRules(host: String): String {
        val hostSuffixes = suffixes(host.lowercase())
        if (hostSuffixes.any { it in elemHideHosts }) return ""
        val excepted = exceptionsFor(hostSuffixes)
        val programs = hostSuffixes.flatMap { suffix -> domainProcedural[suffix].orEmpty().filter { it !in excepted } }
        return if (programs.isEmpty()) "" else programs.joinToString(",", "[", "]")
    }

    /**
     * Writes the built engine in a compact binary form, so the next launch can load it with [readFrom] instead of
     * parsing every list again. [fingerprint] identifies the inputs; [readFrom] rejects a file made from others.
     */
    fun writeTo(out: DataOutputStream, fingerprint: Long) {
        out.writeInt(CACHE_MAGIC)
        out.writeInt(CACHE_VERSION)
        out.writeLong(fingerprint)
        out.writeInt(networkRuleCount)
        out.writeInt(cosmeticRuleCount)
        blocking.writeTo(out)
        exceptions.writeTo(out)
        writeStrings(out, genericSelectors)
        writeMap(out, domainSelectors)
        writeMap(out, domainExceptions)
        writeMap(out, domainProcedural)
        writeStrings(out, genericHideHosts)
        writeStrings(out, elemHideHosts)
    }

    private fun exceptionsFor(hostSuffixes: List<String>): Set<String> {
        var result: MutableSet<String>? = null
        for (suffix in hostSuffixes) {
            val set = domainExceptions[suffix] ?: continue
            (result ?: HashSet<String>().also { result = it }).addAll(set)
        }
        return result ?: emptySet()
    }

    private class Request(
        val url: String,
        val host: String,
        val sourceHost: String?,
        val thirdParty: Boolean,
        val typeBit: Int,
    )

    private class NetRule(
        val body: String,
        val hostAnchored: Boolean,
        val startAnchored: Boolean,
        val endAnchored: Boolean,
        val typeMask: Int,
        val partyMode: Int, // 0 any, 1 third-party only, 2 first-party only
        val includeDomains: List<String>,
        val excludeDomains: List<String>,
    ) {
        @Volatile private var compiled: Pattern? = null

        fun writeTo(out: DataOutputStream) {
            writeString(out, body)
            out.writeByte((if (hostAnchored) 1 else 0) or (if (startAnchored) 2 else 0) or (if (endAnchored) 4 else 0))
            out.writeInt(typeMask)
            out.writeByte(partyMode)
            writeStrings(out, includeDomains)
            writeStrings(out, excludeDomains)
        }

        /** Compiled on first use; most of the ~100k rules in a full list are never reached. */
        val pattern: Pattern
            get() = compiled ?: Pattern.compile(toRegex()).also { compiled = it }

        fun optionsMatch(r: Request): Boolean {
            if (typeMask and r.typeBit == 0) return false
            if (partyMode == 1 && !r.thirdParty) return false
            if (partyMode == 2 && r.thirdParty) return false
            if (includeDomains.isNotEmpty() || excludeDomains.isNotEmpty()) {
                val src = r.sourceHost ?: return includeDomains.isEmpty()
                if (excludeDomains.any { domainMatches(src, it) }) return false
                if (includeDomains.isNotEmpty() && includeDomains.none { domainMatches(src, it) }) return false
            }
            return true
        }

        private fun toRegex(): String {
            val sb = StringBuilder()
            when {
                hostAnchored -> sb.append("^(?:[a-z][a-z0-9+.\\-]*:)?//(?:[^/?#]*\\.)?")
                startAnchored -> sb.append('^')
            }
            for (c in body) {
                when {
                    c == '*' -> sb.append(".*")
                    c == '^' -> sb.append("(?:[^a-z0-9_\\-.%]|$)")
                    c.isLetterOrDigit() && c.code < 128 -> sb.append(c)
                    c.code > 127 -> sb.append(Pattern.quote(c.toString()))
                    else -> sb.append('\\').append(c)
                }
            }
            if (endAnchored) sb.append('$')
            return sb.toString()
        }
    }

    private class RuleSet(
        /** `||host^` rules with no options: the bulk of a real list, stored as bare strings. */
        private val plainHosts: Set<String>,
        /** `||host^$third-party` rules with no other options. */
        private val thirdPartyHosts: Set<String>,
        private val pureHost: Map<String, List<NetRule>>,
        private val tokenIndex: Map<String, List<NetRule>>,
        private val untokenized: List<NetRule>,
    ) {
        fun writeTo(out: DataOutputStream) {
            writeStrings(out, plainHosts)
            writeStrings(out, thirdPartyHosts)
            val rules = ArrayList<NetRule>()
            pureHost.values.forEach(rules::addAll)
            tokenIndex.values.forEach(rules::addAll)
            rules.addAll(untokenized)
            out.writeInt(rules.size)
            for (rule in rules) rule.writeTo(out)
        }

        fun matches(r: Request): Boolean {
            for (suffix in suffixes(r.host)) {
                if (suffix in plainHosts) return true
                if (r.thirdParty && suffix in thirdPartyHosts) return true
                val rules = pureHost[suffix] ?: continue
                if (rules.any { it.optionsMatch(r) }) return true
            }
            if (tokenIndex.isNotEmpty()) {
                for (token in tokensOf(r.url)) {
                    val rules = tokenIndex[token] ?: continue
                    for (rule in rules) {
                        if (rule.optionsMatch(r) && rule.pattern.matcher(r.url).find()) return true
                    }
                }
            }
            for (rule in untokenized) {
                if (rule.optionsMatch(r) && rule.pattern.matcher(r.url).find()) return true
            }
            return false
        }
    }

    private class RuleSetBuilder {
        val plainHosts = HashSet<String>()
        val thirdPartyHosts = HashSet<String>()
        val pureHost = HashMap<String, ArrayList<NetRule>>()
        val tokenIndex = HashMap<String, ArrayList<NetRule>>()
        val untokenized = ArrayList<NetRule>()

        fun add(rule: NetRule) {
            val host = pureHostOf(rule)
            if (host != null) {
                val noOptions = rule.typeMask == ALL_TYPES && rule.includeDomains.isEmpty() && rule.excludeDomains.isEmpty()
                when {
                    noOptions && rule.partyMode == 0 -> plainHosts.add(host.intern())
                    noOptions && rule.partyMode == 1 -> thirdPartyHosts.add(host.intern())
                    else -> pureHost.getOrPut(host) { ArrayList(1) }.add(rule)
                }
                return
            }
            val token = bestToken(rule)
            if (token != null) {
                tokenIndex.getOrPut(token) { ArrayList(1) }.add(rule)
            } else if (rule.body.length >= MIN_UNTOKENIZED_LENGTH) {
                untokenized.add(rule)
            }
        }

        fun build() = RuleSet(plainHosts, thirdPartyHosts, pureHost, tokenIndex, untokenized)

        fun readFrom(input: DataInputStream) {
            readStrings(input) { plainHosts.add(it) }
            readStrings(input) { thirdPartyHosts.add(it) }
            repeat(checkedCount(input)) {
                val body = readString(input)
                val flags = input.readByte().toInt()
                val typeMask = input.readInt()
                val party = input.readByte().toInt()
                val include = ArrayList<String>().also { list -> readStrings(input) { list.add(it) } }
                val exclude = ArrayList<String>().also { list -> readStrings(input) { list.add(it) } }
                add(NetRule(body, flags and 1 != 0, flags and 2 != 0, flags and 4 != 0, typeMask, party, include, exclude))
            }
        }

        private fun pureHostOf(rule: NetRule): String? {
            if (!rule.hostAnchored || rule.endAnchored || !rule.body.endsWith("^")) return null
            val host = rule.body.dropLast(1)
            return host.takeIf { it.isNotEmpty() && HOST_ONLY.matches(it) }
        }

        /** The longest run of letters/digits that a URL token must equal for the rule to match. */
        private fun bestToken(rule: NetRule): String? {
            val body = rule.body
            var best: String? = null
            var i = 0
            while (i < body.length) {
                if (!isTokenChar(body[i])) { i++; continue }
                var j = i
                while (j < body.length && isTokenChar(body[j])) j++
                val leftOk = if (i == 0) rule.hostAnchored || rule.startAnchored else body[i - 1] != '*'
                val rightOk = if (j == body.length) rule.endAnchored else body[j] != '*'
                if (leftOk && rightOk && j - i >= MIN_TOKEN && (best == null || j - i > best.length)) {
                    best = body.substring(i, j)
                }
                i = j
            }
            return best
        }
    }

    class Builder {
        private val blocking = RuleSetBuilder()
        private val exceptions = RuleSetBuilder()
        private val generic = LinkedHashSet<String>()
        private val genericExceptions = HashSet<String>()
        private val perDomain = HashMap<String, LinkedHashSet<String>>()
        private val perDomainExceptions = HashMap<String, HashSet<String>>()
        private val perDomainProcedural = HashMap<String, LinkedHashSet<String>>()
        private val genericHideHosts = HashSet<String>()
        private val elemHideHosts = HashSet<String>()
        private var network = 0
        private var cosmetic = 0

        /**
         * Fast path for the bulk of domain lists: a line that is exactly `||host^` becomes a bare host entry
         * without building a rule object, parsing options or compiling anything.
         */
        private fun addPlainHost(line: String): Boolean {
            val n = line.length
            if (n < 5 || line[0] != '|' || line[1] != '|' || line[n - 1] != '^') return false
            var dot = false
            for (i in 2 until n - 1) {
                val c = line[i]
                when {
                    c == '.' -> dot = true
                    c in 'a'..'z' || c in '0'..'9' || c == '-' -> Unit
                    c in 'A'..'Z' -> Unit
                    else -> return false
                }
            }
            if (!dot || line[2] == '.' || line[n - 2] == '.') return false
            blocking.plainHosts.add(line.substring(2, n - 1).lowercase())
            network++
            return true
        }

        fun addLine(raw: String) {
            if (addPlainHost(raw)) return
            val line = raw.trim()
            if (line.isEmpty() || line[0] == '!' || line[0] == '[') return
            val marker = COSMETIC_MARKER.find(line)
            if (marker != null) {
                val kind = marker.value
                // Scriptlets (#$#, #%#) and snippet rules are not supported.
                if (kind == "##" || kind == "#?#") addCosmetic(line.substring(0, marker.range.first), line.substring(marker.range.last + 1), exception = false)
                if (kind == "#@#" || kind == "#@?#") addCosmetic(line.substring(0, marker.range.first), line.substring(marker.range.last + 1), exception = true)
                return
            }
            addNetwork(line)
        }

        private fun addCosmetic(domains: String, selector: String, exception: Boolean) {
            val sel = selector.trim()
            if (sel.isEmpty() || sel.length > 300) return
            val procedural = ProceduralSelector.isProcedural(sel)
            val key = if (procedural) ProceduralSelector.compile(sel) ?: return else sel.takeIf { isSupportedSelector(it) } ?: return
            if (domains.isEmpty()) {
                // Generic procedural rules would run on every page; they are left out for speed.
                if (procedural) return
                if (exception) genericExceptions.add(key) else if (generic.size < MAX_GENERIC && generic.add(key)) cosmetic++
                return
            }
            val parts = domains.lowercase().split(',')
            if (parts.any { it.startsWith("~") || it.isEmpty() || it.contains('*') }) return
            for (d in parts) {
                val target = when {
                    exception -> perDomainExceptions.getOrPut(d) { HashSet() }
                    procedural -> perDomainProcedural.getOrPut(d) { LinkedHashSet() }
                    else -> perDomain.getOrPut(d) { LinkedHashSet() }
                }
                if (target.add(key) && !exception) cosmetic++
            }
        }

        /** `@@||host^$generichide` and `$elemhide` switch hiding off for a site; only the plain host form is understood. */
        private fun addHidingSwitch(body: String, options: List<String>): Boolean {
            val generic = options.any { it == "generichide" || it == "ghide" }
            val all = options.any { it == "elemhide" || it == "ehide" }
            if (!generic && !all) return false
            if (!body.startsWith("||") || !body.endsWith("^")) return true
            val host = body.substring(2, body.length - 1).lowercase()
            if (!HOST_ONLY.matches(host) || host.isEmpty()) return true
            (if (all) elemHideHosts else genericHideHosts).add(host)
            return true
        }

        private fun addNetwork(raw: String) {
            var line = raw
            val isException = line.startsWith("@@")
            if (isException) line = line.substring(2)

            var options = ""
            val dollar = line.lastIndexOf('$')
            if (dollar >= 0 && OPTION_TAIL.matches(line.substring(dollar + 1))) {
                options = line.substring(dollar + 1)
                line = line.substring(0, dollar)
            }
            if (line.length > 2 && line.startsWith("/") && line.endsWith("/")) return // regex rules unsupported
            if (isException && options.isNotEmpty() && addHidingSwitch(line, options.lowercase().split(','))) return

            var typeMask = ALL_TYPES
            var party = 0
            var include: MutableList<String>? = null
            var exclude: MutableList<String>? = null
            if (options.isNotEmpty()) {
                var positive = 0
                var negative = 0
                for (opt in options.lowercase().split(',')) {
                    val neg = opt.startsWith("~")
                    val name = opt.removePrefix("~")
                    when {
                        name == "third-party" || name == "3p" -> party = if (neg) 2 else 1
                        name == "first-party" || name == "1p" -> party = if (neg) 1 else 2
                        name.startsWith("domain=") -> {
                            for (d in name.removePrefix("domain=").split('|')) {
                                if (d.isEmpty()) continue
                                if (d.startsWith("~")) {
                                    (exclude ?: ArrayList<String>(2).also { exclude = it }).add(d.substring(1))
                                } else {
                                    (include ?: ArrayList<String>(2).also { include = it }).add(d)
                                }
                            }
                        }
                        name == "important" -> Unit
                        else -> {
                            val bit = TYPE_OPTIONS[name] ?: return // unsupported option: skip the whole rule
                            if (neg) negative = negative or bit else positive = positive or bit
                        }
                    }
                }
                if (positive != 0) typeMask = positive
                if (negative != 0) typeMask = typeMask and negative.inv()
            }

            var body = line.lowercase()
            val hostAnchored = body.startsWith("||")
            if (hostAnchored) body = body.substring(2)
            val startAnchored = !hostAnchored && body.startsWith("|")
            if (startAnchored) body = body.substring(1)
            val endAnchored = body.endsWith("|")
            if (endAnchored) body = body.dropLast(1)
            if (body.isEmpty() || body == "*") return
            if (!hostAnchored && !startAnchored && body.length < 3) return

            val rule = NetRule(body, hostAnchored, startAnchored, endAnchored, typeMask, party, include ?: emptyList(), exclude ?: emptyList())
            (if (isException) exceptions else blocking).add(rule)
            network++
        }

        fun build(): FilterEngine {
            val genericList = generic.filter { it !in genericExceptions }
            return FilterEngine(
                blocking = blocking.build(),
                exceptions = exceptions.build(),
                genericSelectors = genericList,
                domainSelectors = perDomain.mapValues { it.value.toList() },
                domainExceptions = perDomainExceptions,
                domainProcedural = perDomainProcedural.mapValues { it.value.toList() },
                genericHideHosts = genericHideHosts,
                elemHideHosts = elemHideHosts,
                networkRuleCount = network,
                cosmeticRuleCount = cosmetic - (generic.size - genericList.size),
            )
        }

        private fun isSupportedSelector(sel: String): Boolean {
            if (sel.isEmpty() || sel.length > 300) return false
            if (sel.startsWith("+js(") || sel.contains('{') || sel.contains('}')) return false
            if (UNSUPPORTED_SELECTOR.containsMatchIn(sel)) return false
            return sel.count { it == '[' } == sel.count { it == ']' } &&
                sel.count { it == '(' } == sel.count { it == ')' } &&
                sel.count { it == '"' } % 2 == 0 && sel.count { it == '\'' } % 2 == 0
        }
    }

    companion object {
        private const val HIDE = "{display:none!important}"
        private const val MIN_TOKEN = 3
        private const val MIN_UNTOKENIZED_LENGTH = 6
        private const val MAX_GENERIC = 3000

        private const val SCRIPT = 1
        private const val IMAGE = 2
        private const val STYLESHEET = 4
        private const val XHR = 8
        private const val SUBFRAME = 16
        private const val MEDIA = 32
        private const val OTHER = 64
        private const val POPUP = 128 // a navigation (new window or redirect), only matched by $popup / $document rules and bare host rules
        private const val ALL_TYPES = 127

        private val TYPE_OPTIONS = mapOf(
            "script" to SCRIPT, "image" to IMAGE, "stylesheet" to STYLESHEET, "css" to STYLESHEET,
            "xmlhttprequest" to XHR, "xhr" to XHR, "subdocument" to SUBFRAME, "frame" to SUBFRAME,
            "media" to MEDIA, "font" to OTHER, "object" to OTHER, "ping" to OTHER, "other" to OTHER,
            "popup" to POPUP, "document" to POPUP, "doc" to POPUP, "all" to (ALL_TYPES or POPUP),
        )

        private val OPTION_TAIL = Regex("^[a-zA-Z0-9~_=,|.\\-]+$")
        private val HOST_ONLY = Regex("^[a-z0-9.\\-]+$")
        private val UNSUPPORTED_SELECTOR = Regex(":-abp-|:has-text|:xpath|:matches-css|:style\\(|:remove\\(|:upward|:min-text|:watch-attr|:others")

        /** The first cosmetic separator in a line: `##`, `#@#`, `#?#`, `#@?#`, or a scriptlet one (`#$#`, `#%#`, `#@$#`). */
        private val COSMETIC_MARKER = Regex("#@?[?$%]?#")

        fun parse(lines: Sequence<String>): FilterEngine {
            val builder = Builder()
            for (line in lines) builder.addLine(line)
            return builder.build()
        }

        fun parse(text: String): FilterEngine = parse(text.lineSequence())

        /** Loads an engine written by [writeTo]; null when the file is from another version or other inputs. */
        fun readFrom(input: DataInputStream, fingerprint: Long): FilterEngine? {
            if (input.readInt() != CACHE_MAGIC || input.readInt() != CACHE_VERSION || input.readLong() != fingerprint) return null
            val network = input.readInt()
            val cosmetic = input.readInt()
            val blocking = RuleSetBuilder().also { it.readFrom(input) }
            val exceptions = RuleSetBuilder().also { it.readFrom(input) }
            val generic = ArrayList<String>().also { list -> readStrings(input) { list.add(it) } }
            return FilterEngine(
                blocking = blocking.build(),
                exceptions = exceptions.build(),
                genericSelectors = generic,
                domainSelectors = readMap(input).mapValues { it.value.toList() },
                domainExceptions = readMap(input),
                domainProcedural = readMap(input).mapValues { it.value.toList() },
                genericHideHosts = HashSet<String>().also { set -> readStrings(input) { set.add(it) } },
                elemHideHosts = HashSet<String>().also { set -> readStrings(input) { set.add(it) } },
                networkRuleCount = network,
                cosmeticRuleCount = cosmetic,
            )
        }

        private const val CACHE_MAGIC = 0x425A4645 // "BZFE"
        private const val CACHE_VERSION = 1
        private const val MAX_COUNT = 5_000_000

        private fun writeString(out: DataOutputStream, text: String) {
            val bytes = text.toByteArray(Charsets.UTF_8)
            out.writeInt(bytes.size)
            out.write(bytes)
        }

        private fun readString(input: DataInputStream): String {
            val size = checkedCount(input)
            val bytes = ByteArray(size)
            input.readFully(bytes)
            return String(bytes, Charsets.UTF_8)
        }

        private fun writeStrings(out: DataOutputStream, items: Collection<String>) {
            out.writeInt(items.size)
            for (item in items) writeString(out, item)
        }

        private inline fun readStrings(input: DataInputStream, each: (String) -> Unit) {
            repeat(checkedCount(input)) { each(readString(input)) }
        }

        private fun writeMap(out: DataOutputStream, map: Map<String, Collection<String>>) {
            out.writeInt(map.size)
            for ((key, values) in map) {
                writeString(out, key)
                writeStrings(out, values)
            }
        }

        private fun readMap(input: DataInputStream): HashMap<String, HashSet<String>> {
            val map = HashMap<String, HashSet<String>>()
            repeat(checkedCount(input)) {
                val key = readString(input)
                map[key] = LinkedHashSet<String>().also { set -> readStrings(input) { set.add(it) } }
            }
            return map
        }

        /** A damaged file must fail with an IOException, not allocate gigabytes. */
        private fun checkedCount(input: DataInputStream): Int {
            val n = input.readInt()
            if (n < 0 || n > MAX_COUNT) throw IOException("corrupt filter cache")
            return n
        }

        private fun typeBit(type: RequestType) = when (type) {
            RequestType.Script -> SCRIPT
            RequestType.Image -> IMAGE
            RequestType.Stylesheet -> STYLESHEET
            RequestType.Xhr -> XHR
            RequestType.SubFrame -> SUBFRAME
            RequestType.Media -> MEDIA
            RequestType.Document, RequestType.Other -> OTHER
            RequestType.Popup -> POPUP
        }

        private fun isTokenChar(c: Char) = (c in 'a'..'z') || (c in '0'..'9') || c == '%'

        private fun tokensOf(url: String): List<String> {
            val tokens = LinkedHashSet<String>()
            var i = 0
            while (i < url.length && tokens.size < 256) {
                if (!isTokenChar(url[i])) { i++; continue }
                var j = i
                while (j < url.length && isTokenChar(url[j])) j++
                if (j - i >= MIN_TOKEN) tokens.add(url.substring(i, j))
                i = j
            }
            return tokens.toList()
        }

        internal fun hostOf(url: String): String = Hosts.hostOf(url)

        /** `a.b.example.com` -> a.b.example.com, b.example.com, example.com, com */
        internal fun suffixes(host: String): List<String> {
            val result = ArrayList<String>(4)
            var h = host
            while (true) {
                result.add(h)
                val dot = h.indexOf('.')
                if (dot < 0) break
                h = h.substring(dot + 1)
            }
            return result
        }

        internal fun registrableDomain(host: String): String = Hosts.registrableDomain(host)

        internal fun domainMatches(host: String, domain: String) = host == domain || host.endsWith(".$domain")
    }
}
