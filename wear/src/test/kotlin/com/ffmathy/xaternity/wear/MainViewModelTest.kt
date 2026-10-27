package com.ffmathy.xaternity.wear

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.xaternity.core.data.EndResult
import com.ffmathy.xaternity.core.data.FixedClock
import com.ffmathy.xaternity.core.data.RoomEntryRepository
import com.ffmathy.xaternity.core.data.XaternityDatabase
import com.ffmathy.xaternity.core.domain.epochMillis
import com.ffmathy.xaternity.core.domain.summarize
import com.ffmathy.xaternity.core.model.BreastSide
import com.ffmathy.xaternity.core.model.Entry
import com.ffmathy.xaternity.core.model.FeedType
import com.ffmathy.xaternity.core.model.PoopColor
import com.ffmathy.xaternity.core.model.PoopTexture
import com.ffmathy.xaternity.core.model.isRunning
import com.ffmathy.xaternity.core.sample.SampleData
import com.ffmathy.xaternity.core.sync.NoopEntrySync
import com.ffmathy.xaternity.wear.state.GuardKind
import com.ffmathy.xaternity.wear.state.GuardTarget
import com.ffmathy.xaternity.wear.state.MainMode
import com.ffmathy.xaternity.wear.state.MainUiState
import com.ffmathy.xaternity.wear.state.ReceiptKind
import com.ffmathy.xaternity.wear.state.TimeField
import com.ffmathy.xaternity.wear.state.Undo
import com.ffmathy.xaternity.wear.state.WearEvent
import com.ffmathy.xaternity.wear.state.XHaptic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalTime
import java.time.ZoneId
import java.util.Collections

/**
 * Every watch flow against the real repository on an in-memory database, with a fixed clock
 * (V1_ADDITIONS §2.2, §3.2; DESIGN_SPEC §3.5, §4.4, §5.11–5.12).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainViewModelTest {
    private lateinit var db: XaternityDatabase
    private lateinit var clock: FixedClock
    private lateinit var repo: RoomEntryRepository
    private lateinit var vm: MainViewModel
    private val store = ViewModelStore()
    private val events: MutableList<WearEvent> = Collections.synchronizedList(mutableListOf())
    private lateinit var collector: Job

    private val zone = SampleData.ZONE
    private val minute = 60_000L

    private companion object {
        /** How long the event stream must stay unchanged before haptics are read. */
        const val QUIET_MILLIS = 50L
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = XaternityDatabase.inMemory(ApplicationProvider.getApplicationContext())
        clock = FixedClock(SampleData.NOW_1432)
        repo = RoomEntryRepository(db, clock)
        val factory = viewModelFactory { initializer { MainViewModel(repo, NoopEntrySync, clock, zone = { zone }, ticks = flowOf(Unit)) } }
        vm = ViewModelProvider.create(store, factory)[MainViewModel::class]
        collector = CoroutineScope(Dispatchers.Unconfined).launch { vm.events.collect { events += it } }
    }

    @After
    fun tearDown() {
        collector.cancel()
        store.clear()
        db.close()
        Dispatchers.resetMain()
    }

    // ---- Helpers -------------------------------------------------------------------------------

    private fun test(block: suspend () -> Unit) = runBlocking { block() }

    private suspend fun <T> await(flow: Flow<T>, predicate: (T) -> Boolean): T = withTimeout(5_000) { flow.first(predicate) }

    private suspend fun awaitMain(predicate: (MainUiState) -> Boolean): MainUiState {
        vm.refresh()
        return await(vm.main, predicate)
    }

    /** Stores [entries] and waits until Main reflects all of them (Room emits once per write). */
    private suspend fun seed(entries: List<Entry>) {
        repo.applyRemoteAll(entries)
        val expected = summarize(entries, clock.now(), zone)
        awaitMain {
            it.loaded && it.lastFeed?.id == expected.lastFeed?.id && it.lastDiaper?.id == expected.lastDiaper?.id &&
                it.runningSleep?.id == expected.runningSleep?.id && it.lastSleep?.id == expected.lastSleep?.id
        }
    }

    /** Waits until [event] has been sent (events travel through a channel), then returns all events so far. */
    private suspend fun awaitEvent(event: WearEvent): List<WearEvent> = withTimeout(5_000) {
        while (event !in events.toList()) delay(5)
        events.toList()
    }

    private fun <T : Any> present(value: T?): T = checkNotNull(value)

    /**
     * The haptics sent so far. Events travel through a channel to a collector on another thread, so this first waits
     * until no new event has arrived for a moment (an assertion right after an action must not race the delivery).
     */
    private suspend fun haptics(): List<XHaptic> {
        withTimeout(5_000) {
            var seen = -1
            while (events.size != seen) {
                seen = events.size
                delay(QUIET_MILLIS)
            }
        }
        return events.toList().filterIsInstance<WearEvent.Haptic>().map { it.type }
    }

    private suspend fun stored(): List<Entry> = repo.observeEntries().first()

    private fun at(hour: Int, minute: Int) = epochMillis(SampleData.TODAY, hour, minute, zone)

    // ---- Main state ----------------------------------------------------------------------------

    @Test
    fun idleStateFollowsTheScenario() = test {
        seed(SampleData.scenario())
        val state = awaitMain { it.lastDiaper != null }
        assertEquals(MainMode.IDLE, state.mode)
        assertEquals("sample-formula-1222", state.lastFeed?.id)
        assertEquals("sample-pee-1344", state.lastDiaper?.id)
        assertEquals(at(13, 27), state.lastSleep?.endMillis)
        assertNull(state.runningSleep)
        assertNull(state.guard)
    }

    @Test
    fun todayListsTodaysEntriesNewestFirst() = test {
        seed(SampleData.scenario())
        // Room may emit the merge in steps: wait for the whole day.
        val today = await(vm.today) { it.loaded && it.entries.size == SampleData.scenario().size }
        assertEquals(SampleData.TODAY, today.day)
        assertEquals("sample-pee-1344", today.entries.first().id)
        // The sleep that started on Sunday 22:40 overlaps today and is listed last.
        assertEquals("sample-sleep-2240", today.entries.last().id)
        assertEquals(SampleData.scenario().size, today.entries.size)
    }

    // ---- Feed type ----------------------------------------------------------------------------

    @Test
    fun formulaIsLoggedAtTheStampAndShowsLogged() = test {
        vm.beginFeed()
        clock.advanceBy(20_000) // The stamp is the Feed tap, not the type tap.
        vm.chooseFeedType(FeedType.FORMULA).join()

        val receipt = present(vm.receipt.value)
        assertEquals(ReceiptKind.FEED_LOGGED, receipt.kind)
        assertEquals(FeedType.FORMULA, receipt.entry.feedType)
        assertEquals(SampleData.NOW_1432, receipt.entry.startMillis)
        assertEquals(Undo.Delete(receipt.entry.id), receipt.undo)
        assertFalse(receipt.duplicate)
        awaitEvent(WearEvent.ShowLogged)
        assertEquals(listOf(XHaptic.CONFIRM), haptics())
        assertEquals(listOf(receipt.entry), stored())
    }

    @Test
    fun earlierCyclesTheOffsetAndBackdatesTheFeed() = test {
        vm.beginFeed()
        vm.cycleFeedOffset()
        vm.cycleFeedOffset()
        assertEquals(-10, vm.feedDraft.value?.offsetMinutes)
        vm.chooseFeedType(FeedType.FOOD).join()
        assertEquals(SampleData.NOW_1432 - 10 * minute, vm.receipt.value?.entry?.startMillis)
        assertEquals(FeedType.FOOD, vm.receipt.value?.entry?.feedType)

        vm.beginFeed()
        repeat(4) { vm.cycleFeedOffset() }
        assertEquals("−5 → −10 → −15 → back to 0", 0, vm.feedDraft.value?.offsetMinutes)
        assertTrue(haptics().count { it == XHaptic.SEGMENT_TICK } >= 6)
    }

    @Test
    fun aRepeatFormulaWithinFiveSecondsShowsAlreadyLogged() = test {
        vm.beginFeed()
        vm.chooseFeedType(FeedType.FORMULA).join()
        val first = vm.receipt.value!!
        vm.beginFeed()
        vm.chooseFeedType(FeedType.FORMULA).join()
        val second = vm.receipt.value!!
        assertTrue(second.duplicate)
        assertEquals(first.entry.id, second.entry.id)
        assertTrue("the drain restarts", second.serial > first.serial)
        assertEquals(listOf(XHaptic.CONFIRM, XHaptic.REJECT), haptics())
        assertEquals(1, stored().size)
    }

    @Test
    fun breastStartsTheTimerAndReturnsToMain() = test {
        vm.beginFeed()
        vm.chooseFeedType(FeedType.BREAST).join()
        val state = awaitMain { it.runningFeed != null }
        assertEquals(MainMode.FEEDING, state.mode)
        assertEquals(SampleData.NOW_1432, state.runningFeed?.startMillis)
        awaitEvent(WearEvent.BackToMain)
        assertEquals(listOf(XHaptic.TOGGLE_ON), haptics())
        assertNull("no Logged for a started feed", vm.receipt.value)
    }

    @Test
    fun aSecondBreastfeedShowsTheFeedingGuard() = test {
        vm.beginFeed()
        vm.chooseFeedType(FeedType.BREAST).join()
        clock.advanceBy(5 * minute)
        vm.beginFeed()
        vm.chooseFeedType(FeedType.BREAST).join()

        val state = awaitMain { it.guard != null }
        assertEquals(GuardKind.ALREADY_FEEDING, state.endFeedGuard)
        assertEquals(GuardTarget.END_FEED, state.guard?.target)
        assertEquals(1, stored().count { it.isRunning })
        assertEquals(XHaptic.REJECT, haptics().last())
    }

    @Test
    fun aDoubleTapOnFormulaLogsOnceWithoutAlreadyLogged() = test {
        vm.beginFeed()
        // The second tap lands on the leaving screen and waits for the first write.
        val first = vm.chooseFeedType(FeedType.FORMULA)
        val second = vm.chooseFeedType(FeedType.FORMULA)
        first.join()
        second.join()
        assertEquals(1, stored().size)
        assertFalse(present(vm.receipt.value).duplicate)
        assertEquals(listOf(XHaptic.CONFIRM), haptics())
        assertEquals(1, events.count { it == WearEvent.ShowLogged })
    }

    @Test
    fun aDoubleTapOnBreastStartsOneFeedWithoutTheGuard() = test {
        vm.beginFeed()
        val first = vm.chooseFeedType(FeedType.BREAST)
        val second = vm.chooseFeedType(FeedType.BREAST)
        first.join()
        second.join()
        assertEquals(1, stored().size)
        assertEquals(listOf(XHaptic.TOGGLE_ON), haptics())
        assertNull(vm.main.value.guard)
    }

    // ---- Breastfeed end ------------------------------------------------------------------------

    @Test
    fun endFeedAsksForTheSideAndEndsWithIt() = test {
        vm.beginFeed()
        vm.chooseFeedType(FeedType.BREAST).join()
        clock.set(SampleData.NOW_1450)
        vm.requestEndFeed().join()
        awaitEvent(WearEvent.ShowSide)
        val draft = present(vm.sideDraft.value)
        assertEquals(SampleData.NOW_1450, draft.endAt)

        clock.advanceBy(10_000) // Picking the side takes a moment; the end stays at the "End feed" tap.
        vm.endFeed(BreastSide.LEFT_THEN_RIGHT).join()
        val receipt = present(vm.receipt.value)
        assertEquals(ReceiptKind.FEED_ENDED, receipt.kind)
        assertEquals(SampleData.NOW_1450, receipt.entry.endMillis)
        assertEquals(BreastSide.LEFT_THEN_RIGHT, receipt.entry.breastSide)
        awaitEvent(WearEvent.ShowLogged)
        assertEquals(XHaptic.TOGGLE_OFF, haptics().last())

        val state = awaitMain { it.runningFeed == null }
        assertEquals(MainMode.IDLE, state.mode)
        assertEquals(receipt.entry, stored().single())
    }

    @Test
    fun endFeedWithinTwoSecondsOfTheStartShowsTheGuard() = test {
        vm.beginFeed()
        vm.chooseFeedType(FeedType.BREAST).join()
        clock.advanceBy(1_000)
        vm.requestEndFeed().join()
        val state = awaitMain { it.guard != null }
        assertEquals(GuardKind.ALREADY_FEEDING, state.endFeedGuard)
        assertNull(vm.sideDraft.value)
        assertFalse(WearEvent.ShowSide in events)
    }

    @Test
    fun aBreastfeedUnderAMinuteIsCancelled() = test {
        vm.beginFeed()
        vm.chooseFeedType(FeedType.BREAST).join()
        clock.advanceBy(30_000)
        vm.requestEndFeed().join()
        vm.endFeed(BreastSide.LEFT).join()
        assertEquals(ReceiptKind.FEED_CANCELLED, vm.receipt.value?.kind)
        assertTrue(stored().isEmpty())
    }

    @Test
    fun undoAfterFeedEndedResumesTheBreastfeed() = test {
        vm.beginFeed()
        vm.chooseFeedType(FeedType.BREAST).join()
        clock.set(SampleData.NOW_1450)
        vm.requestEndFeed().join()
        vm.endFeed(BreastSide.RIGHT).join()
        vm.undo().join()

        awaitEvent(WearEvent.ShowUndone)
        val running = stored().single()
        assertTrue(running.isRunning)
        assertNull(running.breastSide)
        assertNull(vm.receipt.value)
        assertEquals(XHaptic.REJECT, haptics().last())
    }

    @Test
    fun aDoubleTapOnASideEndsTheFeedOnceAndKeepsFeedEnded() = test {
        vm.beginFeed()
        vm.chooseFeedType(FeedType.BREAST).join()
        clock.set(SampleData.NOW_1450)
        vm.requestEndFeed().join()
        awaitEvent(WearEvent.ShowSide)

        val first = vm.endFeed(BreastSide.LEFT_THEN_RIGHT)
        val second = vm.endFeed(BreastSide.LEFT_THEN_RIGHT)
        first.join()
        second.join()

        // "Feed ended" stays with its Undo: no Reject, no return to Main closing it.
        assertEquals(ReceiptKind.FEED_ENDED, present(vm.receipt.value).kind)
        assertEquals(listOf(XHaptic.TOGGLE_ON, XHaptic.TOGGLE_OFF), haptics())
        val all = awaitEvent(WearEvent.ShowLogged)
        assertFalse(WearEvent.BackToMain in all.subList(all.indexOf(WearEvent.ShowLogged), all.size))
        assertEquals(BreastSide.LEFT_THEN_RIGHT, stored().single().breastSide)
    }

    // ---- Diaper --------------------------------------------------------------------------------

    @Test
    fun diaperWithPoopColourAndTexture() = test {
        vm.beginDiaper()
        assertFalse("both checkboxes start off", vm.diaperDraft.value!!.canSave)
        vm.setPee(true)
        vm.setPoop(true)
        vm.setPoopColor(PoopColor.YELLOW)
        vm.setPoopTexture(PoopTexture.SEEDY)
        vm.cycleDiaperOffset()
        vm.saveDiaper().join()

        val receipt = present(vm.receipt.value)
        assertEquals(ReceiptKind.DIAPER_LOGGED, receipt.kind)
        val entry = stored().single()
        assertEquals(receipt.entry, entry)
        assertTrue(entry.pee && entry.poop)
        assertEquals(PoopColor.YELLOW, entry.poopColor)
        assertEquals(PoopTexture.SEEDY, entry.poopTexture)
        assertEquals(SampleData.NOW_1432 - 5 * minute, entry.startMillis)
        awaitEvent(WearEvent.ShowLogged)
        assertEquals(XHaptic.CONFIRM, haptics().last())
    }

    @Test
    fun turningPoopOffDropsItsDetails() = test {
        vm.beginDiaper()
        vm.setPoop(true)
        vm.setPoopColor(PoopColor.GREEN)
        vm.setPoopTexture(PoopTexture.WATERY)
        vm.setPoop(false)
        vm.setPee(true)
        assertNull(vm.diaperDraft.value?.color)
        assertNull(vm.diaperDraft.value?.texture)
        vm.saveDiaper().join()
        val entry = stored().single()
        assertTrue(entry.pee)
        assertFalse(entry.poop)
        assertNull(entry.poopColor)
    }

    @Test
    fun saveWithBothCheckboxesOffIsRejected() = test {
        vm.beginDiaper()
        vm.saveDiaper().join()
        assertTrue(stored().isEmpty())
        assertNull(vm.receipt.value)
        assertEquals(listOf(XHaptic.REJECT), haptics())
    }

    @Test
    fun aDoubleTapOnSaveLogsOneDiaperWithoutAlreadyLogged() = test {
        vm.beginDiaper()
        vm.setPee(true)
        val first = vm.saveDiaper()
        val second = vm.saveDiaper()
        first.join()
        second.join()
        assertEquals(1, stored().size)
        assertFalse(present(vm.receipt.value).duplicate)
        assertEquals(listOf(XHaptic.CONFIRM), haptics())
    }

    // ---- Sleep ---------------------------------------------------------------------------------

    @Test
    fun startSleepThenWokeUpShowsTheSleptReceipt() = test {
        vm.toggleSleep(shownSleepId = null).join()
        val sleep = awaitMain { it.runningSleep != null }
        assertEquals(MainMode.ASLEEP, sleep.mode)
        assertEquals(listOf(XHaptic.TOGGLE_ON), haptics())

        clock.advanceBy(67 * minute)
        vm.toggleSleep(sleep.runningSleep?.id).join()
        val receipt = present(vm.receipt.value)
        assertEquals(ReceiptKind.WOKE_UP, receipt.kind)
        assertEquals(SampleData.NOW_1432 + 67 * minute, receipt.entry.endMillis)
        assertTrue(receipt.undo is Undo.Restore)
        awaitEvent(WearEvent.ShowLogged)
        assertEquals(XHaptic.TOGGLE_OFF, haptics().last())
        assertEquals(MainMode.IDLE, awaitMain { it.runningSleep == null }.mode)
    }

    @Test
    fun aToggleWithinTwoSecondsShowsAlreadyAsleep() = test {
        vm.toggleSleep(shownSleepId = null).join()
        clock.advanceBy(1_500)
        // The second tap of a double tap still saw "Start sleep".
        vm.toggleSleep(shownSleepId = null).join()

        val state = awaitMain { it.guard != null }
        assertEquals(GuardKind.ALREADY_ASLEEP, state.sleepGuard)
        assertNotNull("no state change", state.runningSleep)
        assertEquals(listOf(XHaptic.TOGGLE_ON, XHaptic.REJECT), haptics())

        // The message shows for 2 s from the rejected tap; after it, a tap ends the sleep (a mis-tap under 60 s:
        // "Sleep cancelled").
        clock.advanceBy(2_500)
        assertNull(awaitMain { it.guard == null }.guard)
        vm.toggleSleep(state.runningSleep?.id).join()
        assertEquals(ReceiptKind.SLEEP_CANCELLED, vm.receipt.value?.kind)
        assertTrue(stored().isEmpty())
    }

    @Test
    fun aToggleRightAfterWokeUpShowsAlreadyAwake() = test {
        vm.toggleSleep(shownSleepId = null).join()
        val sleep = stored().single()
        clock.advanceBy(10 * minute)
        vm.toggleSleep(sleep.id).join()
        clock.advanceBy(500)
        vm.toggleSleep(sleep.id).join()

        val state = awaitMain { it.guard != null }
        assertEquals(GuardKind.ALREADY_AWAKE, state.sleepGuard)
        assertNull(state.runningSleep)
        assertEquals(1, stored().size)
    }

    @Test
    fun wokeUpForASleepTheOtherDeviceAlreadyEndedSaysAlreadyAwake() = test {
        vm.toggleSleep(shownSleepId = null).join()
        val sleep = stored().single()
        clock.advanceBy(10 * minute)
        // The phone ends the sleep while the watch still shows it running ("Woke up").
        repo.endSleep(sleep.id, clock.now())
        clock.advanceBy(5_000)
        vm.toggleSleep(shownSleepId = sleep.id).join()

        // The tap meant "end the sleep I see": it is already over, so nothing new starts (DESIGN_SPEC §4.4).
        assertEquals(GuardKind.ALREADY_AWAKE, awaitMain { it.guard != null }.sleepGuard)
        assertEquals(1, stored().size)
        assertNull(stored().single { it.id == sleep.id }.takeIf { it.isRunning })
        assertEquals(XHaptic.REJECT, haptics().last())
        assertNull(vm.receipt.value)
    }

    @Test
    fun startSleepWhileTheOtherDeviceJustStartedOneSaysAlreadyAsleep() = test {
        // The phone started a sleep 4 s ago; the watch still shows "Start sleep".
        val phone = repo.startSleep(clock.now()).getOrThrow()
        clock.advanceBy(4_000)
        vm.toggleSleep(shownSleepId = null).join()

        assertEquals(GuardKind.ALREADY_ASLEEP, awaitMain { it.guard != null }.sleepGuard)
        // The phone's sleep is neither ended nor discarded as a mis-tap.
        assertTrue(stored().single().let { it.id == phone.id && it.isRunning })
        assertNull(vm.receipt.value)
    }

    // ---- Undo ----------------------------------------------------------------------------------

    @Test
    fun undoRemovesALoggedEntryAndShowsUndone() = test {
        vm.beginFeed()
        vm.chooseFeedType(FeedType.FOOD).join()
        vm.undo().join()
        assertTrue(stored().isEmpty())
        val all = awaitEvent(WearEvent.ShowUndone)
        assertTrue(all.indexOf(WearEvent.ShowLogged) < all.indexOf(WearEvent.ShowUndone))
        assertEquals(listOf(XHaptic.CONFIRM, XHaptic.REJECT), haptics())
    }

    @Test
    fun undoAfterWokeUpResumesTheSleepAndAllowsAnImmediateToggle() = test {
        vm.toggleSleep(shownSleepId = null).join()
        val sleep = stored().single()
        clock.advanceBy(30 * minute)
        vm.toggleSleep(sleep.id).join()
        vm.undo().join()
        assertTrue(awaitMain { it.runningSleep != null }.runningSleep!!.isRunning)

        clock.advanceBy(1_000)
        vm.toggleSleep(sleep.id).join()
        assertEquals("not blocked by the guard", ReceiptKind.WOKE_UP, vm.receipt.value?.kind)
    }

    @Test
    fun undoOfADiaperDeletesIt() = test {
        vm.beginDiaper()
        vm.setPee(true)
        vm.saveDiaper().join()
        vm.undo().join()
        assertTrue(stored().isEmpty())
        awaitEvent(WearEvent.ShowUndone)
    }

    @Test
    fun theTileUndoPathOpensLoggedForAnExistingEntry() = test {
        val entry = repo.logFeed(FeedType.FORMULA, clock.now())
        vm.openReceipt(entry.id, fromTile = true).join()
        val receipt = present(vm.receipt.value)
        assertTrue(receipt.fromTile)
        assertEquals(ReceiptKind.FEED_LOGGED, receipt.kind)
        awaitEvent(WearEvent.ShowLogged)
    }

    @Test
    fun theTileUndoPathRestoresASleepCancelledOnTheTile() = test {
        // The tile's "Woke up" under a minute discards the sleep (soft-deleted).
        val sleep = repo.startSleep(clock.now()).getOrThrow()
        clock.advanceBy(30_000)
        val discarded = (repo.endSleep(sleep.id, clock.now()) as EndResult.Discarded).entry
        assertTrue(stored().isEmpty())

        vm.openReceipt(discarded.id, fromTile = true).join()
        val receipt = present(vm.receipt.value)
        assertEquals(ReceiptKind.SLEEP_CANCELLED, receipt.kind)
        assertTrue(receipt.fromTile)
        awaitEvent(WearEvent.ShowLogged)

        vm.undo().join()
        val restored = stored().single()
        assertEquals(sleep.id, restored.id)
        assertEquals(sleep.startMillis, restored.startMillis)
        assertTrue(restored.isRunning)
        awaitEvent(WearEvent.ShowUndone)
    }

    // ---- Adjust / Set time ---------------------------------------------------------------------

    @Test
    fun adjustStartOfTheRunningSleep() = test {
        clock.set(SampleData.NOW_0312)
        seed(SampleData.scenarioAt0312())
        vm.beginAdjust("sample-sleep-0205", listOf(TimeField.START))
        val state = await(vm.adjust) { it != null }!!
        assertEquals(listOf(at(1, 50), at(2, 0), at(2, 10)), state.groups.single().options.map { it.at })
        assertTrue(state.groups.single().options.all { it.enabled })

        vm.adjust(TimeField.START, at(1, 50)).join()
        assertEquals(at(1, 50), repo.get("sample-sleep-0205")?.startMillis)
        assertTrue(repo.get("sample-sleep-0205")!!.isRunning)
        assertEquals(WearEvent.CloseAdjust, awaitEvent(WearEvent.CloseAdjust).last())
        assertEquals(XHaptic.SEGMENT_TICK, haptics().last())
    }

    @Test
    fun adjustDisablesAndRejectsATimeInTheFuture() = test {
        val pee = repo.logDiaper(clock.now() - 3 * minute, pee = true, poop = false)
        vm.beginAdjust(pee.id, listOf(TimeField.START))
        val state = await(vm.adjust) { it != null }!!
        assertEquals(listOf(true, true, false), state.groups.single().options.map { it.enabled })

        vm.adjust(TimeField.START, pee.startMillis + 5 * minute).join()
        assertEquals(pee.startMillis, repo.get(pee.id)?.startMillis)
        assertEquals(XHaptic.REJECT, haptics().last())
    }

    @Test
    fun theAwakeCardAdjustsTheEndOfTheLastSleep() = test {
        seed(SampleData.scenario())
        vm.beginAdjust("sample-sleep-1240", listOf(TimeField.END))
        await(vm.adjust) { it != null }
        vm.adjust(TimeField.END, at(13, 22)).join()
        assertEquals(at(13, 22), repo.get("sample-sleep-1240")?.endMillis)
    }

    @Test
    fun aDoubleTapOnAnAdjustChoiceAppliesItOnceAndReturnsOnce() = test {
        clock.set(SampleData.NOW_0312)
        seed(SampleData.scenarioAt0312())
        vm.beginAdjust("sample-sleep-0205", listOf(TimeField.START))
        val option = await(vm.adjust) { it != null }!!.groups.single().options.single { it.minutes == -5 }
        assertEquals(at(2, 0), option.at)

        val first = vm.adjust(TimeField.START, option.at)
        val second = vm.adjust(TimeField.START, option.at)
        first.join()
        second.join()

        // Never cumulative (DESIGN_SPEC §4.4): 02:05 − 5m, not − 10m.
        assertEquals(at(2, 0), repo.get("sample-sleep-0205")?.startMillis)
        assertEquals(1, events.count { it == WearEvent.CloseAdjust })
        assertEquals(listOf(XHaptic.SEGMENT_TICK), haptics())
    }

    @Test
    fun aDoubleConfirmOnSetTimeAppliesItOnceAndReturnsOnce() = test {
        seed(SampleData.scenario())
        vm.beginAdjust("sample-sleep-1240", listOf(TimeField.START, TimeField.END), allowSetTime = true)
        vm.beginSetTime(TimeField.END)
        await(vm.setTime) { it != null }
        val first = vm.setTime(LocalTime.of(13, 15))
        val second = vm.setTime(LocalTime.of(13, 15))
        first.join()
        second.join()
        assertEquals(at(13, 15), repo.get("sample-sleep-1240")?.endMillis)
        assertEquals(1, events.count { it == WearEvent.CloseAdjust })
        assertEquals(listOf(XHaptic.SEGMENT_TICK), haptics())
    }

    @Test
    fun setTimePutsThePickedTimeOnTheEntrysDayOrTheDayBefore() = test {
        seed(SampleData.scenario())
        vm.beginAdjust("sample-sleep-1240", listOf(TimeField.START, TimeField.END), allowSetTime = true)
        vm.beginSetTime(TimeField.END)
        assertEquals(at(13, 27), await(vm.setTime) { it != null }!!.base)
        vm.setTime(LocalTime.of(13, 15)).join()
        assertEquals(at(13, 15), repo.get("sample-sleep-1240")?.endMillis)
        assertEquals(WearEvent.CloseAdjust, awaitEvent(WearEvent.CloseAdjust).last())

        // 23:00 today would be in the future: it lands on the day before.
        vm.beginAdjust("sample-pee-1344", listOf(TimeField.START), allowSetTime = true)
        vm.beginSetTime(TimeField.START)
        await(vm.setTime) { it?.entry?.id == "sample-pee-1344" }
        vm.setTime(LocalTime.of(23, 0)).join()
        assertEquals(epochMillis(SampleData.YESTERDAY, 23, 0, zone), repo.get("sample-pee-1344")?.startMillis)
    }

    @Test
    fun setTimeRejectsAnEndBeforeTheStart() = test {
        seed(SampleData.scenario())
        vm.beginAdjust("sample-sleep-1240", listOf(TimeField.START, TimeField.END), allowSetTime = true)
        vm.beginSetTime(TimeField.END)
        await(vm.setTime) { it != null }
        vm.setTime(LocalTime.of(12, 0)).join()
        assertEquals(at(13, 27), repo.get("sample-sleep-1240")?.endMillis)
        assertEquals(XHaptic.REJECT, haptics().last())
    }

    // ---- Time zone -----------------------------------------------------------------------------

    @Test
    fun aNewTimeZoneAppliesWithoutANewViewModel() = test {
        var system: ZoneId = SampleData.ZONE
        val zoneStore = ViewModelStore()
        val factory = viewModelFactory { initializer { MainViewModel(repo, NoopEntrySync, clock, zone = { system }, ticks = flowOf(Unit)) } }
        val travelling = ViewModelProvider.create(zoneStore, factory)[MainViewModel::class]
        try {
            assertEquals(SampleData.ZONE, await(travelling.main) { it.loaded }.zone)
            system = ZoneId.of("Europe/London")
            // The time-zone broadcast (or the next minute tick) re-reads the zone.
            travelling.refresh()
            assertEquals(ZoneId.of("Europe/London"), await(travelling.main) { it.zone == system }.zone)
            assertEquals(ZoneId.of("Europe/London"), await(travelling.today) { it.zone == system }.zone)
            travelling.beginDiaper()
            assertEquals(ZoneId.of("Europe/London"), travelling.diaperDraft.value?.zone)
        } finally {
            zoneStore.clear()
        }
    }
}
