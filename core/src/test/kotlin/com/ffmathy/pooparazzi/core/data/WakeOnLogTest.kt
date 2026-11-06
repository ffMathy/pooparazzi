package com.ffmathy.pooparazzi.core.data

import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.sync.FakeEntrySync
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A feed or diaper logged during a sleep ends it at the log's time (DESIGN_SPEC §4.4, "Awake on a log"). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WakeOnLogTest {
    private lateinit var db: PooparazziDatabase
    private lateinit var repo: RoomEntryRepository
    private val now = SampleData.NOW_1432
    private val minute = 60_000L

    @Before
    fun setUp() {
        db = PooparazziDatabase.inMemory(ApplicationProvider.getApplicationContext())
        repo = RoomEntryRepository(db, FixedClock(now), FakeEntrySync())
    }

    @After
    fun tearDown() = db.close()

    private fun test(block: suspend () -> Unit) = runBlocking { block() }

    @Test
    fun aFeedDuringASleepEndsItAtTheFeedsTime() = test {
        val sleep = repo.startSleep(now - 50 * minute).getOrThrow()
        val feed = repo.logFeed(FeedType.BOTTLE, now - 5 * minute)

        val result = repo.wakeFor(feed) as EndResult.Ended

        assertEquals(sleep.id, result.entry.id)
        assertEquals(now - 5 * minute, repo.get(sleep.id)?.endMillis)
        assertEquals(null, result.previous.endMillis)
    }

    @Test
    fun aDiaperDuringASleepEndsItToo() = test {
        repo.startSleep(now - 50 * minute).getOrThrow()
        val diaper = repo.logDiaper(now, pee = true, poop = false)

        assertTrue(repo.wakeFor(diaper) is EndResult.Ended)
    }

    @Test
    fun aLogBeforeTheSleepBeganLeavesItRunning() = test {
        val sleep = repo.startSleep(now - 10 * minute).getOrThrow()
        val feed = repo.logFeed(null, now - 15 * minute)

        assertNull(repo.wakeFor(feed))
        assertNull(repo.get(sleep.id)?.endMillis)
    }

    @Test
    fun withoutASleepNothingChanges() = test {
        val feed = repo.logFeed(FeedType.FOOD, now)

        assertNull(repo.wakeFor(feed))
    }

    @Test
    fun aSleepUnderAMinuteIsCancelled() = test {
        repo.startSleep(now - 30_000L).getOrThrow()
        val feed = repo.logFeed(null, now)

        assertTrue(repo.wakeFor(feed) is EndResult.Discarded)
    }
}
