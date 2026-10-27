package com.ffmathy.pooparazzi.phone.ui.theme

import com.ffmathy.pooparazzi.core.settings.Appearance
import java.time.LocalTime

/**
 * The Appearance rule (DESIGN_SPEC §8.1). Pure Kotlin so it is unit-tested without Android.
 *
 * Auto is Night when **any** of these holds:
 * - the system dark theme is on;
 * - the local time lies in the night window (19:00–07:00 by default; used because the app does not ask for location,
 *   so there is no sunset/sunrise);
 * - a sleep is running and "Dim for naps too" is on (default).
 *
 * Night and Day are fixed choices.
 */
object NightRule {
    /** Default night window start (DESIGN_SPEC §3.3.5). */
    val DEFAULT_NIGHT_START: LocalTime = LocalTime.of(19, 0)

    /** Default night window end. */
    val DEFAULT_NIGHT_END: LocalTime = LocalTime.of(7, 0)

    fun isNight(
        appearance: Appearance,
        systemDark: Boolean,
        localTime: LocalTime,
        sleepRunning: Boolean,
        dimForNaps: Boolean = true,
        nightStart: LocalTime = DEFAULT_NIGHT_START,
        nightEnd: LocalTime = DEFAULT_NIGHT_END,
    ): Boolean = when (appearance) {
        Appearance.NIGHT -> true
        Appearance.DAY -> false
        Appearance.AUTO -> systemDark || inWindow(localTime, nightStart, nightEnd) || (dimForNaps && sleepRunning)
    }

    /** True when [time] lies in [start, end); the window may wrap midnight (19:00–07:00). */
    fun inWindow(time: LocalTime, start: LocalTime, end: LocalTime): Boolean = when {
        start == end -> false
        start < end -> time >= start && time < end
        else -> time >= start || time < end
    }
}
