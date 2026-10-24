package com.browserz.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class PermissionRulesTest {
    @Test
    fun `given several decisions when combining then block wins, all allow passes, else ask`() {
        val a = PermissionDecision.Allow
        val b = PermissionDecision.Block
        val q = PermissionDecision.Ask
        assertEquals(b, PermissionRules.combine(listOf(a, b, q)))
        assertEquals(a, PermissionRules.combine(listOf(a, a)))
        assertEquals(q, PermissionRules.combine(listOf(a, q)))
        assertEquals(b, PermissionRules.combine(emptyList()))
    }
}
