package com.ffmathy.pooparazzi.checks

import org.junit.Test

/** End-to-end check for WatchTile: the Tile, as the tile carousel shows it, on the real watch app. */
class WatchTileChecks : Watch() {
    /** "just now" or "1 minute ago", as the Tile speaks a time since. */
    private fun spokenAgo(minutes: Long) = if (minutes == 0L) "just now" else "${spokenWatchMinutes(minutes)} ago"

    @Test
    fun `a fresh tile`() {
        freshInstall()
        openTile()
        assertTileShows("Feed. No feeds yet")
        assertTileShows("No feeds yet")
        assertTileShows("Diaper. No diapers yet")
        assertTileShows("No diapers yet")
        assertTileShows("Start sleep")
        check(!tileShowsPart("Asleep") && !tileShowsPart("Last diaper")) { "a title on a fresh Tile:\n${tileScreen()}" }
    }

    @Test
    fun `the tile shows the last feed and diaper`() {
        freshInstall()
        val fed = logFood()
        closeLogged()
        val peed = logPee()
        closeLogged()
        openTile()
        val feedSince = clockMinutesBetween(fed, clock())
        val peeSince = clockMinutesBetween(peed, clock())
        assertTileShows("Pee ${if (peeSince == 0L) "just now" else "${peeSince}m ago"}")
        assertTileShows("Last diaper, pee, ${spokenAgo(peeSince)}")
        assertTileShows("Feed. Last feed ${spokenAgo(feedSince)}")
        assertTileShows("Diaper. Last diaper ${spokenAgo(peeSince)}")
        assertTileShows(shortSince(feedSince))
    }

    @Test
    fun `starting a sleep from the tile`() {
        freshInstall()
        openTile()
        tapTile("Start sleep")
        val start = recentClocks().first { tileShows("Asleep at $it. Tap the tile to undo") }
        assertTileShows("Asleep $start · ")
        assertTileShows("Undo")
        assertTileShows("Woke up, end sleep, Asleep since $start")
        assertTileShows("Woke up")
        assertTileShows("just now")
        pass(10_500)
        openTile()
        val minutes = clockMinutesBetween(start, clock())
        assertTileShows(if (minutes == 0L) "Asleep <1m" else "Asleep ${minutes}m")
        check(!tileShowsPart("Tap the tile to undo")) { "the confirmation is still on the Tile:\n${tileScreen()}" }
    }

    @Test
    fun `the tile's background opens logged with undo at once`() {
        freshInstall()
        openTile()
        tapTile("Start sleep")
        val start = recentClocks().first { tileShows("Asleep at $it. Tap the tile to undo") }
        tapTile(null)
        waitFor("Asleep at $start")
        assertCanTap("Undo")
        tap("Undo")
        waitFor("Undone")
        waitFor("Start sleep", 4_000)
    }

    @Test
    fun `the tile's rows open the app`() {
        freshInstall()
        openTile()
        tapTile("Feed. No feeds yet")
        shownTime("Feed · ")
        assertShows("Just the time")
        swipeToDismiss()
        waitFor("Feed. No feeds yet")
        openTile()
        tapTile("Diaper. No diapers yet")
        shownTime("Diaper · ")
        assertShows("Pee")
        assertShows("Poop")
    }
}
