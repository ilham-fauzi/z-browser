package com.browserz.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MiniJsonTest {
    @Test
    fun `given nested JSON when parsing then maps lists and scalars come back`() {
        val v = MiniJson.parse("""{"a":[1,2.5,"x\nA",true,null],"b":{"c":-3e2}}""") as Map<*, *>
        assertEquals(listOf(1.0, 2.5, "x\nA", true, null), v["a"])
        assertEquals(-300.0, (v["b"] as Map<*, *>)["c"])
    }

    @Test
    fun `given malformed or too deep JSON when parsing then it throws`() {
        assertFailsWith<IllegalArgumentException> { MiniJson.parse("{\"a\":[1,}") }
        assertFailsWith<IllegalArgumentException> { MiniJson.parse("[1] x") }
        assertFailsWith<IllegalArgumentException> { MiniJson.parse("[".repeat(200)) }
    }
}
