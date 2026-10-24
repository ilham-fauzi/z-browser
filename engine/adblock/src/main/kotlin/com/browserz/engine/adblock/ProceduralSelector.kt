package com.browserz.engine.adblock

/**
 * Turns a procedural hiding rule (uBlock / ABP extended syntax) into a small JSON program the page script runs:
 * `["base css", ["op", "arg"], ...]`. Supported operators:
 *
 * - `:has-text(text)` / `:-abp-contains(text)`: keep elements whose text contains the text (or matches `/regex/flags`)
 * - `:-abp-has(selector)` / `:has(selector)`: keep elements that contain a match for the selector, which may itself
 *   be procedural (`.card:has(span:has-text(Ad))`)
 * - `:upward(n)` / `:upward(selector)`: go to the n-th ancestor, or the nearest ancestor matching the selector
 * - `:remove()`: remove the elements instead of hiding them (last only)
 * - plain CSS after an operator: ` > child` / ` descendant` look inside, `.class` filters
 *
 * Anything else (`:xpath`, `:matches-css`, `:style`, procedural operators inside `:not()`...) makes [compile]
 * return null, so the rule is skipped instead of half-applied.
 */
internal object ProceduralSelector {

    private val SUPPORTED = mapOf(
        "has-text" to "text",
        "-abp-contains" to "text",
        "-abp-has" to "has",
        "upward" to "up",
        "remove" to "remove",
    )

    private val UNSUPPORTED = listOf(
        "xpath", "matches-css", "matches-css-before", "matches-css-after", "style", "min-text-length", "watch-attr",
        "others", "matches-path", "matches-attr", "matches-media", "matches-prop", "if", "if-not", "-abp-properties",
        "nth-ancestor", "contains", "spath",
    )

    private val ANY_OPERATOR = Regex(":(?:" + (SUPPORTED.keys + UNSUPPORTED).joinToString("|") { Regex.escape(it) } + ")\\(")

    /** Where a rule is split into steps; adds the native `:has(`, which only needs a step when its argument is procedural. */
    private val SPLIT_AT = Regex(":(?:" + (SUPPORTED.keys + UNSUPPORTED + "has").joinToString("|") { Regex.escape(it) } + ")\\(")

    fun isProcedural(selector: String): Boolean = ANY_OPERATOR.containsMatchIn(selector)

    fun compile(selector: String): String? = compile(selector, depth = 0)

    private fun compile(selector: String, depth: Int): String? {
        if (depth > 2) return null
        val parts = split(selector) ?: return null
        val base = parts.first().trim()
        if (base.isEmpty() || base.startsWith('^') || !plainCss(base)) return null // ^ = HTML filtering, not possible here
        val steps = ArrayList<String>()
        steps.add(json(base))
        var removed = false
        for (i in 1 until parts.size step 2) {
            if (removed) return null // :remove() must come last
            val name = parts[i]
            val (op, arg) = name.substringBefore('(') to name.substringAfter('(').dropLast(1).trim()
            val kind = if (op == "has") "has" else SUPPORTED[op] ?: return null
            when (kind) {
                "text" -> {
                    if (arg.isEmpty()) return null
                    steps.add(step("text", unquote(arg)))
                }
                "has" -> when {
                    arg.isEmpty() -> return null
                    plainCss(arg) -> steps.add(step("has", arg))
                    else -> steps.add("[\"hasp\"," + (compile(arg, depth + 1) ?: return null) + "]")
                }
                "up" -> {
                    val n = arg.toIntOrNull()
                    if (n != null && n !in 1..256) return null
                    if (n == null && (arg.isEmpty() || !plainCss(arg))) return null
                    steps.add(step("up", arg))
                }
                "remove" -> {
                    if (arg.isNotEmpty()) return null
                    steps.add("[\"remove\"]")
                    removed = true
                }
            }
            val tail = parts.getOrNull(i + 1).orEmpty()
            if (tail.isBlank()) continue
            if (removed || !plainCss(tail.trim())) return null
            val trimmed = tail.trimStart()
            steps.add(
                when {
                    trimmed.startsWith('+') || trimmed.startsWith('~') -> return null
                    trimmed.startsWith('>') -> step("down", ":scope $trimmed")
                    tail.first().isWhitespace() -> step("down", ":scope $trimmed")
                    else -> step("is", trimmed)
                },
            )
        }
        return steps.joinToString(",", "[", "]")
    }

    /**
     * Splits at the operators at the top level: [css, ":op(arg)" without the colon, css, ...].
     * Returns null for unbalanced brackets or quotes.
     */
    private fun split(selector: String): List<String>? {
        val result = ArrayList<String>()
        var depth = 0
        var bracket = 0
        var quote = 0.toChar()
        var start = 0
        var i = 0
        var opStart = -1
        while (i < selector.length) {
            val c = selector[i]
            when {
                quote != 0.toChar() -> if (c == '\\') i++ else if (c == quote) quote = 0.toChar()
                c == '\\' -> i++ // escaped character, e.g. \( in a regex argument
                // Quotes and brackets matter in CSS ([href="a)b"]); in an operator's text argument they are just text.
                opStart < 0 && (c == '"' || c == '\'') -> quote = c
                opStart < 0 && c == '[' -> bracket++
                opStart < 0 && c == ']' -> bracket--
                c == '(' -> depth++
                c == ')' -> {
                    depth--
                    if (depth < 0) return null
                    if (depth == 0 && opStart >= 0) {
                        result.add(selector.substring(opStart + 1, i + 1))
                        opStart = -1
                        start = i + 1
                    }
                }
                c == ':' && depth == 0 && bracket == 0 -> {
                    val match = SPLIT_AT.matchAt(selector, i)
                    if (match != null) {
                        result.add(selector.substring(start, i))
                        opStart = i
                    }
                }
            }
            i++
        }
        if (depth != 0 || bracket != 0 || quote != 0.toChar() || opStart >= 0) return null
        result.add(selector.substring(start))
        return result
    }

    private fun plainCss(css: String): Boolean = !ANY_OPERATOR.containsMatchIn(css) && !css.contains('{') && !css.contains('}')

    /** `"text"` and `'text'` arguments are written with their quotes in some lists. */
    private fun unquote(arg: String): String =
        if (arg.length >= 2 && (arg.first() == '"' || arg.first() == '\'') && arg.last() == arg.first()) arg.substring(1, arg.length - 1) else arg

    private fun step(op: String, arg: String) = "[\"$op\",${json(arg)}]"

    private fun json(text: String): String {
        val sb = StringBuilder(text.length + 2).append('"')
        for (c in text) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '<' -> sb.append("\\u003c") // never closes a <script> it is pasted into
                c.code < 0x20 || c == ' ' || c == ' ' -> sb.append(String.format("\\u%04x", c.code))
                else -> sb.append(c)
            }
        }
        return sb.append('"').toString()
    }
}
