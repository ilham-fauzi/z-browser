package com.browserz.feature.browser

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.MutableFloatState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Chrome-style swipe on the address bar: a clearly sideways drag moves to the next tab (drag left) or the previous
 * one (drag right). Watched before the text field sees the touch, so a tap still focuses the field and a vertical
 * drag is left alone; [offset] follows the finger so the bar can slide with it.
 */
internal fun Modifier.tabSwipe(enabled: Boolean, offset: MutableFloatState, onSwipe: (step: Int) -> Unit): Modifier =
    if (!enabled) this else pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        val trigger = SWIPE_TRIGGER.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var dx = 0f
            var dy = 0f
            var dragging = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (dragging) {
                        change.consume() // no click on the field at the end of a swipe
                        if (abs(dx) >= trigger) onSwipe(if (dx < 0) 1 else -1)
                    }
                    break
                }
                val delta = change.positionChange()
                dx += delta.x
                dy += delta.y
                if (!dragging) {
                    if (abs(dy) > slop && abs(dy) > abs(dx)) break
                    if (abs(dx) > slop && abs(dx) > abs(dy) * 1.5f) dragging = true
                }
                if (dragging) {
                    change.consume()
                    offset.floatValue = dx
                }
            }
            offset.floatValue = 0f
        }
    }

private val SWIPE_TRIGGER = 72.dp
