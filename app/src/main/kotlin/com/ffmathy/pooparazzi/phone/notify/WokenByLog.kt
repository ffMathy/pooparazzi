package com.ffmathy.pooparazzi.phone.notify

import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.isRunning
import com.ffmathy.pooparazzi.core.model.isSleep

/** A sleep that a feed or diaper log ended ([sleep], now with its end), and that log ([cause]). */
data class WokenByLog(val sleep: Entry, val cause: Entry)

/**
 * Follows every baby's entries for the sleeps a feed or diaper ended (DESIGN_SPEC §4.4, "Awake on a log"), on this phone,
 * on the watch or on another family phone, so the phone can say so with the woke-up notification. A sleep counts once it
 * was seen running and then ends at the very time of a feed or diaper of the same baby; the two may arrive in either
 * order (a watch's sync batch), so the match is looked for on every change until it is found. Each sleep is reported
 * once, and only within [TimerNotifications.WOKE_UP_TIMEOUT_MILLIS] of its end: a sync that catches up hours later
 * says nothing.
 */
class WokenByLogWatcher {
    private val seenRunning = mutableSetOf<String>()
    private val reported = mutableSetOf<String>()

    /** The sleeps [entries] (every baby's, current) show ended by a log since the last call, at [now]. */
    fun next(entries: List<Entry>, now: Long): List<WokenByLog> {
        val sleeps = entries.filter { it.isSleep && !it.deleted }
        sleeps.filter { it.isRunning }.forEach { seenRunning += it.id }
        val woken = sleeps.mapNotNull { sleep ->
            val end = sleep.endMillis ?: return@mapNotNull null
            if (sleep.id !in seenRunning || sleep.id in reported) return@mapNotNull null
            if (now - end !in 0..TimerNotifications.WOKE_UP_TIMEOUT_MILLIS) return@mapNotNull null
            val cause = entries.firstOrNull { !it.isSleep && !it.deleted && it.babyId == sleep.babyId && it.startMillis == end }
                ?: return@mapNotNull null
            WokenByLog(sleep, cause)
        }
        woken.forEach { reported += it.sleep.id }
        // Sleeps that are gone or long over are forgotten, so the sets stay small.
        val live = sleeps.filter { it.isRunning || it.endMillis?.let { end -> now - end <= TimerNotifications.WOKE_UP_TIMEOUT_MILLIS } == true }
            .mapTo(mutableSetOf()) { it.id }
        seenRunning.retainAll(live)
        reported.retainAll(live)
        return woken
    }
}
