package com.ffmathy.pooparazzi.core.domain

import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.clockDurationMillis

/** What the Lamp shows (DESIGN_SPEC §2.3, V1_ADDITIONS §2.1). A running feed takes priority over a running sleep. */
sealed interface LampState {
    /** Whole minutes for the hero numeral and the ember (0 when nothing applies). */
    val elapsedMinutes: Long

    /** Idle: time since [lastFeed] started ([sinceMillis]); both null before the first feed. */
    data class FeedIdle(val lastFeed: Entry?, val sinceMillis: Long?) : LampState {
        override val elapsedMinutes: Long get() = (sinceMillis ?: 0L) / 60_000L
    }

    /** A sleep runs: "Asleep 1h 07m · since 02:05", SLEEP orbit. */
    data class SleepRunning(val sleep: Entry, val elapsedMillis: Long) : LampState {
        override val elapsedMinutes: Long get() = elapsedMillis / 60_000L
    }

    /** A breastfeed runs: "Breastfeeding 18m · since 14:32", FEED orbit. */
    data class FeedRunning(val feed: Entry, val elapsedMillis: Long) : LampState {
        override val elapsedMinutes: Long get() = elapsedMillis / 60_000L
    }
}

/**
 * The Lamp state for [summary]: running feed > running sleep > idle. Elapsed times are whole clock minutes
 * ([clockDurationMillis]), so the numeral and the ember agree with "since 02:05" and step on the minute.
 */
fun lampState(summary: Summary): LampState {
    summary.runningFeed?.let { return LampState.FeedRunning(it, clockDurationMillis(it.startMillis, summary.now)) }
    summary.runningSleep?.let { return LampState.SleepRunning(it, clockDurationMillis(it.startMillis, summary.now)) }
    return LampState.FeedIdle(summary.lastFeed, summary.sinceLastFeedMillis)
}

/** Ember angle in degrees clockwise from 12 o'clock: (elapsed minutes mod 60) × 6 (DESIGN_SPEC §2.2). */
fun emberAngleDegrees(elapsedMinutes: Long): Float = ((elapsedMinutes.coerceAtLeast(0) % 60) * 6).toFloat()
