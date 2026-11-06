package com.ffmathy.pooparazzi.core.data

import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.isRunning
import com.ffmathy.pooparazzi.core.model.isSleep
import kotlinx.coroutines.flow.first

/**
 * The running sleep in these entries that a feed or diaper logged at [at] wakes the baby from (DESIGN_SPEC §4.4,
 * "Awake on a log"): one that began before [at]. A log nudged back to before the sleep began leaves it running.
 */
fun List<Entry>.sleepWokenBy(at: Long): Entry? = firstOrNull { it.isSleep && it.isRunning && it.startMillis < at }

/**
 * A feed or diaper logged while the baby sleeps means the baby is awake: ends the current baby's running sleep at
 * [logged]'s time, as if Woke up had been tapped then (DESIGN_SPEC §4.4). Returns [EndResult.Ended], or
 * [EndResult.Discarded] for a sleep that began under a minute before ("Sleep cancelled"); null when no sleep of
 * [logged]'s baby ran before it (or it ended meanwhile, e.g. on the other device).
 */
suspend fun EntryRepository.wakeFor(logged: Entry): EndResult? {
    if (logged.isSleep || logged.deleted) return null
    val sleep = observeEntries().first().sleepWokenBy(logged.startMillis)?.takeIf { it.babyId == logged.babyId } ?: return null
    return endSleep(sleep.id, logged.startMillis).takeUnless { it is EndResult.NotRunning }
}
