// ail-derived-from: DiaperWhileAsleep@5b32630b3e4b4b00
package com.ffmathy.pooparazzi.checks

import org.junit.Assert.assertTrue
import org.junit.Test

/** End-to-end check for DiaperWhileAsleep: a diaper saved during a sleep, and the sleep it ends, on the real phone app. */
open class DiaperWhileAsleepChecks : Phone() {
    @Test
    fun `the Diaper sheet says the sleep will end`() {
        freshInstall()
        sleepStarted20MinutesAgo()
        diaperSheet()
        assertShows("Woke up will be logged too")
    }

    @Test
    fun `saving the diaper ends the sleep`() {
        freshInstall()
        val start = sleepStarted20MinutesAgo()
        diaperSheet()
        tap("Pee")
        val at = sheetTime()
        saveTheDiaper()
        waitOutTheCooldown()
        assertShows("Start sleep")
        assertGone("Woke up, end sleep")
        assertShows("Awake")
        waitUntil("Awake since $at") { shows("Awake ${spokenMinutes(clockMinutesBetween(at, clock()))}, since $at") }
        waitForLastDiaper("pee", at)
        assertShows("Pee")
        val slept = clockMinutesBetween(start, at)
        assertTrue(todayCounts(), todayCounts().endsWith(", $slept minutes sleep"))
    }

    @Test
    fun `a diaper before the sleep began leaves it running`() {
        freshInstall()
        val start = sleepStarted20MinutesAgo()
        diaperSheet()
        tap("−15m")
        tap("−10m")
        assertGone("Woke up will be logged too")
        saveTheDiaper()
        waitOutTheCooldown()
        assertShows("Woke up, end sleep")
        waitUntil("the Lamp asleep since $start") {
            shows("Asleep, ${spokenMinutes(clockMinutesBetween(start, clock()))}, since $start")
        }
    }
}
