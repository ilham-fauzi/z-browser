package com.browserz.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolbarPlacementTest {
    @Test
    fun `given automatic placement when compact then bottom, otherwise top`() {
        assertEquals(true, toolbarAtBottom(BarPlacement.Auto, LayoutClass.Compact))
        assertEquals(false, toolbarAtBottom(BarPlacement.Auto, LayoutClass.Medium))
        assertEquals(false, toolbarAtBottom(BarPlacement.Auto, LayoutClass.Expanded))
    }

    @Test
    fun `given an explicit placement when any window size then it wins`() {
        LayoutClass.values().forEach {
            assertEquals(false, toolbarAtBottom(BarPlacement.Top, it))
            assertEquals(true, toolbarAtBottom(BarPlacement.Bottom, it))
        }
    }
}
