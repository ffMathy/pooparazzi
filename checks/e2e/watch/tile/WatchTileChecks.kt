// ail-derived-from: WatchTile@fb79e9e3616ab921
package com.ffmathy.pooparazzi.checks

import org.junit.Test

/** End-to-end check for WatchTile: the Tile, as the tile carousel shows it, on the real watch app. */
open class WatchTileChecks : Watch() {
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
        // The Tile is drawn once, when opened: the minute may have turned since.
        val drawn = clockMinutesBetween(peed, clock()).let { listOf(it, it - 1).filter { m -> m >= 0 } }
        val peeSince = drawn.firstOrNull { tileShows("Pee ${if (it == 0L) "just now" else "${it}m ago"}") }
            ?: throw AssertionError("no \"Pee …\" title for $drawn minutes on the Tile:\n${tileScreen()}")
        val feedSince = peeSince + clockMinutesBetween(fed, peed)
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
        // The title's time is the renderer's dynamic time text, filled in once it has drawn a frame.
        waitUntil("the Tile's title \"Asleep $start · \"") { tileShows("Asleep $start · ") }
        assertTileShows("Undo")
        assertTileShows("Woke up, end sleep, Asleep since $start")
        assertTileShows("Woke up")
        // Drawn by the same answer that started the sleep: in its own minute.
        assertTileShows("just now")
        pass(10_500)
        openTile()
        // Drawn once, when opened: the minute may have turned since.
        val minutes = clockMinutesBetween(start, clock())
        check(listOf(minutes, minutes - 1).any { it >= 0 && tileShows("Asleep ${asleepFor(it)}") }) { "no \"Asleep …\" title:\n${tileScreen()}" }
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
