package com.ffmathy.xaternity.core.model

private const val MINUTE_MILLIS = 60_000L

/**
 * Whole minutes between two instants **as a wall clock shows them**: the difference of their minute numbers,
 * never negative. A pee at 13:44:30 read at 14:32:10 is 48 minutes ago, matching the "13:44" and "14:32" printed
 * beside it (raw milliseconds would floor to 47). Time-zone offsets are whole minutes, so the UTC minute equals
 * the local one.
 *
 * Use it for every **displayed** duration. Rules that measure real elapsed time (the 60 s mis-tap, the 5 s
 * dedupe) keep using raw milliseconds, and stored times are never rounded.
 */
fun clockMinutesBetween(fromMillis: Long, toMillis: Long): Long =
    (Math.floorDiv(toMillis, MINUTE_MILLIS) - Math.floorDiv(fromMillis, MINUTE_MILLIS)).coerceAtLeast(0L)

/** [clockMinutesBetween] in milliseconds (a whole number of minutes), ready for the duration formatter. */
fun clockDurationMillis(fromMillis: Long, toMillis: Long): Long = clockMinutesBetween(fromMillis, toMillis) * MINUTE_MILLIS
