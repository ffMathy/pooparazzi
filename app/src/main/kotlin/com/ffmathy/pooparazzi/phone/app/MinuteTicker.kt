package com.ffmathy.pooparazzi.phone.app

import com.ffmathy.pooparazzi.core.data.Clock
import com.ffmathy.pooparazzi.core.data.FixedClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private const val MINUTE_MILLIS = 60_000L

/**
 * Keeps [now] at the clock's time, stepping just after every minute boundary (nothing ticks faster, DESIGN_SPEC §2.1).
 * A [FixedClock] (tests, screenshots) is not polled: callers refresh [now] after their own actions instead.
 */
internal fun CoroutineScope.launchMinuteTicker(clock: Clock, now: MutableStateFlow<Long>) {
    if (clock is FixedClock) return
    launch {
        while (true) {
            val t = clock.now()
            now.value = t
            delay(MINUTE_MILLIS - Math.floorMod(t, MINUTE_MILLIS) + 50L)
        }
    }
}
