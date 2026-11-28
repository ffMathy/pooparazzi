// ail-derived-from: StartSleep@8b9230398387890e
package com.ffmathy.pooparazzi.checks

import org.junit.Test

class StartSleepShots : Phone() {

    // A clock time as the app writes it ("14:32", "2:32 PM" or "2:32 PM" with a narrow space),
    // moved by a number of minutes and written back the same way.
    private fun shiftClock(shown: String, minutes: Int): String {
        val match = Regex("""^(\d{1,2}):(\d{2})(\s*)([AaPp][Mm])?$""").matchEntire(shown.trim())
            ?: error("Can't read the clock time \"$shown\"")
        val (hourText, minuteText, space, meridiem) = match.destructured
        var hour = hourText.toInt()
        val minute = minuteText.toInt()
        val twelveHour = meridiem.isNotEmpty()
        if (twelveHour) {
            val pm = meridiem.equals("PM", ignoreCase = true)
            hour = (hour % 12) + if (pm) 12 else 0
        }
        val total = Math.floorMod(hour * 60 + minute + minutes, 24 * 60)
        val newHour = total / 60
        val newMinute = total % 60
        return if (twelveHour) {
            val h12 = if (newHour % 12 == 0) 12 else newHour % 12
            val pm = newHour >= 12
            val mark = when {
                meridiem[0].isUpperCase() -> if (pm) "PM" else "AM"
                else -> if (pm) "pm" else "am"
            }
            "$h12:${newMinute.toString().padStart(2, '0')}$space$mark"
        } else {
            val h = if (hourText.length == 2) newHour.toString().padStart(2, '0') else newHour.toString()
            "$h:${newMinute.toString().padStart(2, '0')}"
        }
    }

    @Test
    fun `starting a sleep`() {
        freshInstall()
        waitFor("Start sleep")

        tap("Start sleep")
        waitFor("Sleep started")
        val started = clock(0)

        assertShowsAt("{time}", 0)
        assertShows("Undo")
        assertShows("−15m")
        assertShows("−10m")
        assertShows("−5m")
        assertShows("+5m")
        assertCanTap("−15m")
        assertCanTap("−10m")
        assertCanTap("−5m")
        assertCannotTap("+5m")
        assertCanTap("Edit")
        assertCanTap("Done")

        waitFor("Woke up")
        assertShows("Woke up")

        waitUntil("the Lamp reads Asleep since the time now") {
            val since = showsPart("since $started") || showsPart("since ${shiftClock(started, -1)}")
            showsPart("Asleep") && since
        }
        if (clock(0) == started) {
            waitUntil("the Lamp reads just now") {
                showsPart("just now") || clock(0) != started
            }
        }

        pictureTheScreen("starting-a-sleep")
    }

    @Test
    fun `nudging the start earlier`() {
        freshInstall()
        val started = startSleepReceipt()

        tap("−15m")
        tap("−5m")

        val earlier = shiftClock(started, -20)
        waitUntil("the receipt's time reads $earlier, was $started") {
            showsPart(earlier) && showsPart("was $started")
        }
        assertShows("Sleep started")

        waitUntil("the Lamp reads Asleep since $earlier, in clock minutes") {
            val minutes = clockMinutesBetween(earlier, clock(0))
            showsPart("Asleep, ${spokenMinutes(minutes)}, since $earlier")
        }

        pictureTheScreen("nudging-the-start-earlier")
    }

    @Test
    fun `done keeps the sleep running`() {
        freshInstall()
        startSleepReceipt()

        tap("Done")
        waitUntilGone("Sleep started")
        assertGone("Sleep started")
        assertGone("Done")
        assertGone("Undo")

        waitFor("Woke up")
        assertShows("Woke up")
        assertGone("Start sleep")

        pictureTheScreen("done-keeps-the-sleep-running")
    }

    @Test
    fun `undoing a sleep start`() {
        freshInstall()
        startSleepReceipt()

        tap("Undo")
        waitUntilGone("Sleep started")
        assertGone("Sleep started")
        waitFor("Undone")
        assertShows("Undone")

        waitFor("Start sleep")
        assertShows("Start sleep")
        assertGone("Woke up")

        pass(2_000)
        waitUntilGone("Undone")
        assertGone("Undone")

        pictureTheScreen("undoing-a-sleep-start")
    }

    @Test
    fun `edit opens the sleep`() {
        freshInstall()
        val started = startSleepReceipt()

        tap("Edit")
        waitFor("Edit sleep")
        waitUntilGone("Sleep started")
        assertGone("Sleep started")
        assertShows("Edit sleep")

        assertShows("Fell asleep")
        waitUntil("Fell asleep reads $started") { showsPart(started) }
        assertShows("Still asleep")

        pictureTheScreen("edit-opens-the-sleep")
    }
}
