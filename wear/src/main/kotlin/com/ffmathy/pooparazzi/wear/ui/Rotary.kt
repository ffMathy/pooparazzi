package com.ffmathy.pooparazzi.wear.ui

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** How far the crown turns per step: a short turn of a crown, one detent of a rotating bezel. */
private val ROTARY_STEP = 18.dp

/**
 * Takes the crown (or rotating bezel) on a screen without a scrolling list, and turns its rotation into whole
 * steps: [onSteps] gets +n for clockwise (later), −n for anticlockwise (earlier). The screen asks for focus when it
 * appears, as the crown only reaches the focused node.
 */
@Composable
fun Modifier.rotarySteps(onSteps: (Int) -> Unit): Modifier {
    val focus = remember { FocusRequester() }
    val stepPx = with(LocalDensity.current) { ROTARY_STEP.toPx() }
    val current by rememberUpdatedState(onSteps)
    // Rotation not yet a whole step; a plain holder, nothing draws from it.
    val pending = remember { FloatArray(1) }
    LaunchedEffect(focus) { focus.requestFocus() }
    return this
        .onRotaryScrollEvent { event ->
            pending[0] += event.verticalScrollPixels
            val steps = (pending[0] / stepPx).toInt()
            if (steps != 0) {
                pending[0] -= steps * stepPx
                current(steps)
            }
            true
        }
        .focusRequester(focus)
        .focusable()
}
