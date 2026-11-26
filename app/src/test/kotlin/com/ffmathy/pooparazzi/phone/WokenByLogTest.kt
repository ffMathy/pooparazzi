package com.ffmathy.pooparazzi.phone

import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.phone.notify.WokenByLogWatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which sleeps a feed or diaper ended, for the woke-up notification (DESIGN_SPEC §4.4). */
class WokenByLogTest {
    private val start = SampleData.at(2, 5)
    private val end = SampleData.NOW_0312
    private val running = Entry.sleep(start, null, id = "sleep")
    private val ended = running.copy(endMillis = end)
    private val feed = Entry.feed(FeedType.FOOD, end, id = "feed")

    @Test
    fun aSleepEndedAtAFeedsTimeIsReportedOnce() {
        val watcher = WokenByLogWatcher()
        assertTrue(watcher.next(listOf(running), end).isEmpty())
        val woken = watcher.next(listOf(ended, feed), end)
        assertEquals(listOf("sleep" to "feed"), woken.map { it.sleep.id to it.cause.id })
        assertTrue("once", watcher.next(listOf(ended, feed), end + 60_000L).isEmpty())
    }

    @Test
    fun theEndMayArriveBeforeTheFeed() {
        val watcher = WokenByLogWatcher()
        watcher.next(listOf(running), end)
        assertTrue(watcher.next(listOf(ended), end).isEmpty())
        assertEquals(1, watcher.next(listOf(ended, feed), end).size)
    }

    @Test
    fun wokeUpItselfSaysNothingHere() {
        val watcher = WokenByLogWatcher()
        watcher.next(listOf(running), end)
        assertTrue(watcher.next(listOf(ended, feed.copy(startMillis = end - 60_000L)), end).isEmpty())
    }

    @Test
    fun aSleepNeverSeenRunningOrLongOverIsNotReported() {
        assertTrue(WokenByLogWatcher().next(listOf(ended, feed), end).isEmpty())
        val watcher = WokenByLogWatcher()
        watcher.next(listOf(running), end)
        assertTrue(watcher.next(listOf(ended, feed), end + 31 * 60_000L).isEmpty())
    }

    @Test
    fun anotherBabysLogDoesNotCount() {
        val watcher = WokenByLogWatcher()
        watcher.next(listOf(running), end)
        assertTrue(watcher.next(listOf(ended, feed.copy(babyId = SampleData.TWIN_ID)), end).isEmpty())
    }
}
