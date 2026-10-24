package com.browserz.core.domain

/**
 * A small, dependency-free JSON reader for import files. Objects become Map, arrays List, numbers Double,
 * strings String, true/false Boolean, null stays null. Throws [IllegalArgumentException] on malformed input.
 */
object MiniJson {
    private const val MAX_DEPTH = 64

    fun parse(text: String): Any? {
        val reader = Reader(text)
        val value = reader.value(0)
        reader.skipWhitespace()
        require(reader.atEnd()) { "Unexpected text after JSON" }
        return value
    }

    private class Reader(val s: String) {
        var i = 0

        fun atEnd() = i >= s.length

        fun skipWhitespace() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        fun value(depth: Int): Any? {
            require(depth <= MAX_DEPTH) { "JSON nested too deeply" }
            skipWhitespace()
            require(i < s.length) { "Unexpected end" }
            return when (val c = s[i]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw IllegalArgumentException("Unexpected character at $i")
            }
        }

        private fun literal(word: String, result: Any?): Any? {
            require(s.startsWith(word, i)) { "Bad literal at $i" }
            i += word.length
            return result
        }

        private fun num(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(start, i).toDoubleOrNull() ?: throw IllegalArgumentException("Bad number at $start")
        }

        private fun str(): String {
            i++ // opening quote
            val out = StringBuilder()
            while (true) {
                require(i < s.length) { "Unterminated string" }
                val c = s[i++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> {
                        require(i < s.length) { "Unterminated escape" }
                        when (val e = s[i++]) {
                            '"', '\\', '/' -> out.append(e)
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                require(i + 4 <= s.length) { "Bad unicode escape" }
                                out.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> throw IllegalArgumentException("Bad escape \\$e")
                        }
                    }
                    else -> out.append(c)
                }
            }
        }

        private fun arr(depth: Int): List<Any?> {
            i++
            val list = ArrayList<Any?>()
            skipWhitespace()
            if (i < s.length && s[i] == ']') { i++; return list }
            while (true) {
                list += value(depth + 1)
                skipWhitespace()
                require(i < s.length) { "Unterminated array" }
                when (s[i++]) {
                    ',' -> continue
                    ']' -> return list
                    else -> throw IllegalArgumentException("Expected , or ] at ${i - 1}")
                }
            }
        }

        private fun obj(depth: Int): Map<String, Any?> {
            i++
            val map = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (i < s.length && s[i] == '}') { i++; return map }
            while (true) {
                skipWhitespace()
                require(i < s.length && s[i] == '"') { "Expected key at $i" }
                val key = str()
                skipWhitespace()
                require(i < s.length && s[i] == ':') { "Expected : at $i" }
                i++
                map[key] = value(depth + 1)
                skipWhitespace()
                require(i < s.length) { "Unterminated object" }
                when (s[i++]) {
                    ',' -> continue
                    '}' -> return map
                    else -> throw IllegalArgumentException("Expected , or } at ${i - 1}")
                }
            }
        }
    }
}
