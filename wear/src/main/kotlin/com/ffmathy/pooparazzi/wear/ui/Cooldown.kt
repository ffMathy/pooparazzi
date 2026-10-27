package com.ffmathy.pooparazzi.wear.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * An input cooldown (DESIGN_SPEC §5.9): targets ignore taps for a while each time the screen appears, fading in,
 * and are exposed as disabled until [ready]. A double tap can then never land on a new target.
 */
@Stable
class Cooldown internal constructor(startAlpha: Float, ready: Boolean) {
    internal val fade = Animatable(startAlpha)

    var ready: Boolean by mutableStateOf(ready)
        internal set

    /** The fade-in alpha for the targets (an inactive state, so exempt from the text-alpha rule, §9.1). */
    val alpha: Float get() = fade.value
}

/**
 * Remembers a cooldown of [millis] fading [fromAlpha] → 1 (pickers: 500 ms, 0.6 → 1; watch Undo: 1000 ms,
 * 0.4 → 1). [enabled] false starts it ready (e.g. Logged opened from the Tile). Previews start ready.
 */
@Composable
fun rememberCooldown(
    millis: Int = PICKER_COOLDOWN_MILLIS,
    fromAlpha: Float = 0.6f,
    enabled: Boolean = true,
    key: Any? = Unit,
): Cooldown {
    val skip = LocalInspectionMode.current || !enabled
    val cooldown = remember(key, skip) { Cooldown(startAlpha = if (skip) 1f else fromAlpha, ready = skip) }
    LaunchedEffect(cooldown) {
        if (cooldown.ready) return@LaunchedEffect
        cooldown.fade.animateTo(1f, tween(millis, easing = LinearEasing))
        cooldown.ready = true
    }
    return cooldown
}

/** Picker cooldown (DESIGN_SPEC §5.12). */
const val PICKER_COOLDOWN_MILLIS = 500
