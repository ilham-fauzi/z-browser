package com.browserz.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowWidthSizeClass

enum class LayoutClass { Compact, Medium, Expanded }

/** Where the toolbar goes. Auto = bottom in compact windows, top otherwise. */
enum class BarPlacement { Auto, Top, Bottom }

val LocalLayoutClass = compositionLocalOf { LayoutClass.Compact }

/** Width class of the current window (not the device), so split-screen and foldables adapt too. */
@Composable
fun currentLayoutClass(): LayoutClass =
    when (currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass) {
        WindowWidthSizeClass.COMPACT -> LayoutClass.Compact
        WindowWidthSizeClass.MEDIUM -> LayoutClass.Medium
        else -> LayoutClass.Expanded
    }

private val BAR_ENTER = expandVertically(tween(200)) + fadeIn(tween(200))
private val BAR_EXIT = shrinkVertically(tween(200)) + fadeOut(tween(120))

/** Whether the toolbar sits at the bottom for [placement] in a window of class [layout]. */
fun toolbarAtBottom(placement: BarPlacement, layout: LayoutClass): Boolean = when (placement) {
    BarPlacement.Auto -> layout == LayoutClass.Compact
    BarPlacement.Top -> false
    BarPlacement.Bottom -> true
}

/**
 * Compact: page plus toolbar (tabs via a switcher; side tabs are not offered, there is no room).
 * Medium and expanded: tabs as a strip above the page or as a list at the side, plus the toolbar.
 * Expanded: an optional side panel (library) next to the page.
 * The toolbar goes on top or bottom per [toolbarPlacement]. The [page] slot keeps its place in the
 * tree whatever the layout, so the WebView is never recreated.
 */
@Composable
fun AdaptiveShell(
    tabStrip: @Composable () -> Unit,
    tabSidebar: @Composable () -> Unit,
    toolbar: @Composable () -> Unit,
    sidePanel: (@Composable () -> Unit)?,
    page: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    toolbarPlacement: BarPlacement = BarPlacement.Auto,
    tabsOnSide: Boolean = false,
    toolbarVisible: Boolean = true,
) {
    val layout = currentLayoutClass()
    val bottom = toolbarAtBottom(toolbarPlacement, layout)
    val showStrip = layout != LayoutClass.Compact && !tabsOnSide
    val showSidebar = layout != LayoutClass.Compact && tabsOnSide
    CompositionLocalProvider(LocalLayoutClass provides layout) {
        Column(modifier.fillMaxSize().safeDrawingPadding()) {
            if (showStrip) tabStrip()
            if (!bottom) {
                AnimatedVisibility(toolbarVisible, enter = BAR_ENTER, exit = BAR_EXIT) {
                    Column {
                        toolbar()
                        HorizontalDivider()
                    }
                }
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (showSidebar) {
                    Box(Modifier.width(220.dp).fillMaxHeight()) { tabSidebar() }
                    VerticalDivider()
                }
                if (layout == LayoutClass.Expanded && sidePanel != null) {
                    Box(Modifier.width(320.dp).fillMaxHeight()) { sidePanel() }
                    VerticalDivider()
                }
                Box(Modifier.weight(1f).fillMaxSize()) { page() }
            }
            if (bottom) {
                AnimatedVisibility(toolbarVisible, enter = BAR_ENTER, exit = BAR_EXIT) {
                    Column {
                        HorizontalDivider()
                        toolbar()
                    }
                }
            }
        }
    }
}
