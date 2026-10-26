package com.ffmathy.xaternity.core.ui.time

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.ffmathy.xaternity.core.data.Clock
import com.ffmathy.xaternity.core.data.FixedClock
import kotlinx.coroutines.delay

/**
 * "Now" for status UI, refreshed just after every minute boundary (nothing ticks faster: DESIGN_SPEC §2.1).
 * Feed it to `summarize()` so the hero numeral and the Lamp's ember step once per minute.
 * A [FixedClock] is read once and never ticks (previews, screenshots).
 */
@Composable
fun rememberMinuteNow(clock: Clock): Long {
    var now by remember(clock) { mutableLongStateOf(clock.now()) }
    LaunchedEffect(clock) {
        if (clock is FixedClock) return@LaunchedEffect
        while (true) {
            val current = clock.now()
            now = current
            delay(MINUTE_MILLIS - Math.floorMod(current, MINUTE_MILLIS) + 50L)
        }
    }
    return now
}

/** True when the user turned animations off (animator duration scale 0): the ember then jumps and has no tail. */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

private const val MINUTE_MILLIS = 60_000L
