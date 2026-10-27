package com.ffmathy.xaternity.wear.tile

import androidx.test.core.app.ApplicationProvider
import com.ffmathy.xaternity.core.data.FixedClock
import com.ffmathy.xaternity.core.data.RoomEntryRepository
import com.ffmathy.xaternity.core.data.XaternityDatabase
import com.ffmathy.xaternity.core.model.DiaperKind
import com.ffmathy.xaternity.core.model.Entry
import com.ffmathy.xaternity.core.model.EntryKind
import com.ffmathy.xaternity.core.model.isRunning
import com.ffmathy.xaternity.core.sample.SampleData
import com.ffmathy.xaternity.wear.state.GuardKind
import com.ffmathy.xaternity.wear.state.XHaptic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The tile's one-tap actions against the real repository (DESIGN_SPEC §3.6, §4.4, §10.7; V1_ADDITIONS §2.2):
 * idempotent writes, the 5 s dedupe, the 2 s toggle guard on every surface, and the 10 s confirmation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TileActionsTest {
    private lateinit var db: XaternityDatabase
    private lateinit var clock: FixedClock
    private lateinit var repo: RoomEntryRepository
    private lateinit var memory: InMemoryTileMemory
    private lateinit var actions: TileActions
    private var token = 0L

    @Before
    fun setUp() {
        db = XaternityDatabase.inMemory(ApplicationProvider.getApplicationContext())
        clock = FixedClock(SampleData.NOW_1432)
        repo = RoomEntryRepository(db, clock)
        memory = InMemoryTileMemory()
        actions = TileActions(repo, memory, SampleData.ZONE)
    }

    @After
    fun tearDown() = db.close()

    private fun test(block: suspend () -> Unit) = runBlocking { block() }

    /** A tap on a freshly built layout (each layout gets a new token). */
    private suspend fun tap(action: TileAction): XHaptic? = actions.handle(action.clickId(++token), clock.now())

    private suspend fun stored(): List<Entry> = repo.observeEntries().first()

    private suspend fun diapers() = stored().filter { it.kind == EntryKind.DIAPER }

    @Test
    fun peeLogsAPeeDiaperWithoutDetailsAndConfirmsForTenSeconds() = test {
        assertEquals(XHaptic.CONFIRM, tap(TileAction.LOG_PEE))
        val diaper = diapers().single()
        assertTrue(diaper.pee)
        assertFalse(diaper.poop)
        assertNull(diaper.poopColor)
        assertEquals(SampleData.NOW_1432, diaper.startMillis)
        val feedback = checkNotNull(memory.feedback)
        assertEquals(TileFeedbackKind.DIAPER, feedback.kind)
        assertEquals(diaper.id, feedback.entryId)
        assertEquals(DiaperKind.PEE, feedback.diaperKind)
        assertEquals(SampleData.NOW_1432 + TILE_FEEDBACK_MILLIS, feedback.untilMillis)
    }

    @Test
    fun bothLogsPeeAndPoopWithoutColourOrTexture() = test {
        tap(TileAction.LOG_BOTH)
        val diaper = diapers().single()
        assertTrue(diaper.pee && diaper.poop)
        assertNull(diaper.poopColor)
        assertNull(diaper.poopTexture)
        assertEquals(DiaperKind.BOTH, memory.feedback?.diaperKind)
    }

    @Test
    fun aReplayedClickIdNeverLogsTwice() = test {
        val id = TileAction.LOG_POOP.clickId(7)
        assertEquals(XHaptic.CONFIRM, actions.handle(id, clock.now()))
        clock.advanceBy(60_000)
        // The host sends the same lastClickableId again with a later refresh: nothing happens.
        assertNull(actions.handle(id, clock.now()))
        assertEquals(1, diapers().size)
    }

    @Test
    fun aSecondTapWithinFiveSecondsIsTheSameDiaper() = test {
        tap(TileAction.LOG_PEE)
        clock.advanceBy(3_000)
        assertEquals(XHaptic.REJECT, tap(TileAction.LOG_PEE))
        assertEquals(1, diapers().size)
        clock.advanceBy(10_000)
        assertEquals(XHaptic.CONFIRM, tap(TileAction.LOG_PEE))
        assertEquals(2, diapers().size)
    }

    @Test
    fun refreshesAndOtherClickablesDoNothing() = test {
        assertNull(actions.handle("", clock.now()))
        assertNull(actions.handle(null, clock.now()))
        assertNull(actions.handle("open_feed", clock.now()))
        assertTrue(stored().isEmpty())
    }

    @Test
    fun sleepToggleStartsThenGuardsThenEnds() = test {
        assertEquals(XHaptic.TOGGLE_ON, tap(TileAction.SLEEP_TOGGLE))
        val sleep = stored().single { it.kind == EntryKind.SLEEP }
        assertTrue(sleep.isRunning)

        // A second tap within 2 s: "Already asleep" for 2 s, nothing changes.
        clock.advanceBy(1_500)
        assertEquals(XHaptic.REJECT, tap(TileAction.SLEEP_TOGGLE))
        assertEquals(GuardKind.ALREADY_ASLEEP, memory.guard?.kind)
        assertEquals(clock.now() + TILE_GUARD_MILLIS, memory.guard?.untilMillis)
        assertTrue(stored().single { it.kind == EntryKind.SLEEP }.isRunning)

        // Later: Woke up, confirmed with Undo for 10 s.
        clock.advanceBy(20 * 60_000L)
        assertEquals(XHaptic.TOGGLE_OFF, tap(TileAction.SLEEP_TOGGLE))
        val ended = stored().single { it.kind == EntryKind.SLEEP }
        assertEquals(clock.now(), ended.endMillis)
        assertEquals(TileFeedbackKind.WOKE_UP, memory.feedback?.kind)
        assertEquals(ended.id, memory.feedback?.entryId)
        assertNull(memory.guard)
    }

    @Test
    fun aSleepUnderAMinuteIsCancelled() = test {
        tap(TileAction.SLEEP_TOGGLE)
        clock.advanceBy(30_000)
        assertEquals(XHaptic.TOGGLE_OFF, tap(TileAction.SLEEP_TOGGLE))
        assertTrue(stored().none { it.kind == EntryKind.SLEEP })
        assertEquals(TileFeedbackKind.SLEEP_CANCELLED, memory.feedback?.kind)
        assertFalse(checkNotNull(memory.feedback).undoable)
    }

    @Test
    fun theGuardCoversToggleFromOtherSurfaces() = test {
        // The app's EdgeButton (or the phone, through sync) started a sleep a second ago.
        repo.startSleep(clock.now()).getOrThrow()
        clock.advanceBy(1_000)
        assertEquals(XHaptic.REJECT, tap(TileAction.SLEEP_TOGGLE))
        assertEquals(GuardKind.ALREADY_ASLEEP, memory.guard?.kind)
        assertTrue(stored().single().isRunning)
    }

    @Test
    fun aLogClearsAShowingGuard() = test {
        memory.guard = TileGuard(GuardKind.ALREADY_AWAKE, clock.now() + TILE_GUARD_MILLIS)
        tap(TileAction.LOG_POOP)
        assertNull(memory.guard)
    }

    @Test
    fun clickIdsCarryTheirAction() {
        assertEquals(TileAction.LOG_BOTH, TileAction.fromClickId(TileAction.LOG_BOTH.clickId(42)))
        assertEquals("sleep_toggle#3", TileAction.SLEEP_TOGGLE.clickId(3))
        assertNull(TileAction.fromClickId("log_feed#1"))
    }
}
