// ail-derived-from: DeleteEntry@6798d36525013bb4
package com.ffmathy.pooparazzi.checks

import java.time.LocalTime
import org.junit.Test

/** End-to-end check for DeleteEntry: Delete, the "… deleted" receipt and Undo, on History and on Home, on the real phone app. */
open class DeleteEntryChecks : Phone() {
    @Test
    fun `deleting from History, and Undo`() {
        freshInstall()
        openHistory()
        addPastSleep(daysAgo(1), LocalTime.of(13, 0), daysAgo(1), LocalTime.of(14, 0))
        tap("Previous day")
        waitFor("Sleep · 1h 00m")
        tap("Sleep · 1h 00m")
        waitFor("Edit sleep")
        pass(500)
        tapTop("Delete")
        waitFor("Sleep deleted")
        assertGone("Edit sleep")
        assertGone("Sleep · 1h 00m")
        tap("Undo")
        waitFor("Sleep · 1h 00m")
    }

    /** A feed logged 15 minutes ago, deleted from the Lamp's edit sheet; returns its time. */
    private fun deleteTheLastFeed(): String {
        val fed = feedJustTheTime(listOf("−15m"))
        tap("Last fed")
        waitFor("Edit feed")
        pass(500)
        tapTop("Delete")
        waitFor("Feed deleted")
        pass(600)
        return fed
    }

    @Test
    fun `deleting from Home, and Undo`() {
        freshInstall()
        val fed = deleteTheLastFeed()
        assertCanTap("Undo")
        assertCanTap("Done")
        for (gone in listOf("−15m", "−5m", "+5m", "Edit")) assertGone(gone)
        assertShows("Last fed, No feeds yet")
        tap("Undo")
        waitFor("Undone")
        waitUntil("the Lamp counting from the feed again") { shows("Last feed, ${spokenSince(fed)}, at $fed") }
    }

    @Test
    fun `Done keeps the delete`() {
        freshInstall()
        deleteTheLastFeed()
        tap("Done")
        waitUntilGone("Feed deleted")
        pass(800)
        assertShows("Last fed, No feeds yet")
    }
}
