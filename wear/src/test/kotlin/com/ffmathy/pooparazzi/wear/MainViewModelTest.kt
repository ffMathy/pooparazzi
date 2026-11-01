package com.ffmathy.pooparazzi.wear

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.data.EndResult
import com.ffmathy.pooparazzi.core.data.FixedClock
import com.ffmathy.pooparazzi.core.data.PooparazziDatabase
import com.ffmathy.pooparazzi.core.data.RoomEntryRepository
import com.ffmathy.pooparazzi.core.domain.epochMillis
import com.ffmathy.pooparazzi.core.domain.summarize
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.model.Breast
import com.ffmathy.pooparazzi.core.model.BreastSide
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.model.PoopColor
import com.ffmathy.pooparazzi.core.model.PoopTexture
import com.ffmathy.pooparazzi.core.model.isRunning
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.sync.NoopEntrySync
import com.ffmathy.pooparazzi.wear.state.GuardKind
import com.ffmathy.pooparazzi.wear.state.MainMode
import com.ffmathy.pooparazzi.wear.state.MainUiState
import com.ffmathy.pooparazzi.wear.state.ReceiptKind
import com.ffmathy.pooparazzi.wear.state.Undo
import com.ffmathy.pooparazzi.wear.state.WearEvent
import com.ffmathy.pooparazzi.wear.state.XHaptic
import com.ffmathy.pooparazzi.wear.testing.FakeSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
import java.time.ZoneId
import java.util.Collections

/**
 * Every watch flow against the real repository on an in-memory database, with a fixed clock
 * (V1_ADDITIONS §2.2, §3.2; DESIGN_SPEC §3.5, §4.4, §5.11–5.12).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
// A plain Application: WearApp would start the surface updater, whose work on Dispatchers.Main races the test's
// setMain ("Dispatchers.Main is used concurrently with setting it").
@Config(sdk = [36], application = Application::class)
class MainViewModelTest {
    private lateinit var db: PooparazziDatabase
    private lateinit var clock: FixedClock
    private lateinit var repo: RoomEntryRepository
    private lateinit var settings: FakeSettingsRepository
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
        val main = UnconfinedTestDispatcher()
        Dispatchers.setMain(main)
        // Room runs on the test's Main dispatcher, as in the app's test rigs: with its own executor threads, a query
        // resuming the view model could read Dispatchers.Main while tearDown resets it ("used concurrently").
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PooparazziDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCoroutineContext(main)
            .build()
        clock = FixedClock(SampleData.NOW_1432)
        settings = FakeSettingsRepository()
        repo = RoomEntryRepository(db, clock, selectedBaby = settings.state.map { it.selectedBaby.id })
        val factory = viewModelFactory { initializer { MainViewModel(repo, settings, NoopEntrySync, clock, zone = { zone }, ticks = flowOf(Unit)) } }
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
    fun breastOpensSideWithTheFeedTypeScreensTimeAndLogsNothingYet() = test {
        vm.beginFeed()
        vm.cycleFeedOffset() // −5m
        vm.chooseFeedType(FeedType.BREAST).join()
        awaitEvent(WearEvent.ShowSide)
        val draft = present(vm.sideDraft.value)
        assertEquals(SampleData.NOW_1432 - 5 * minute, draft.at)
        assertEquals(emptyList<Breast>(), draft.ticks)
        assertTrue("nothing is logged before Save", stored().isEmpty())
        assertNull(vm.receipt.value)
        assertNotNull("swiping back keeps the Feed type screen's time", vm.feedDraft.value)
        assertEquals(MainMode.IDLE, vm.main.value.mode)
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

    // ---- Breast side -----------------------------------------------------------------------------

    @Test
    fun saveOnSideLogsABreastFeedInstantWithTheTickOrder() = test {
        vm.beginFeed()
        vm.chooseFeedType(FeedType.BREAST).join()
        clock.advanceBy(10_000) // Ticking the sides takes a moment; the time stays at the Feed tap.
        vm.tickSide(Breast.LEFT)
        vm.tickSide(Breast.RIGHT)
        assertEquals(BreastSide.LEFT_THEN_RIGHT, present(vm.sideDraft.value).side)
        vm.saveBreastfeed().join()

        val receipt = present(vm.receipt.value)
        assertEquals(ReceiptKind.FEED_LOGGED, receipt.kind)
        assertEquals(FeedType.BREAST, receipt.entry.feedType)
        assertEquals(SampleData.NOW_1432, receipt.entry.startMillis)
        assertNull("a breast feed has no end", receipt.entry.endMillis)
        assertEquals(BreastSide.LEFT_THEN_RIGHT, receipt.entry.breastSide)
        assertEquals(Undo.Delete(receipt.entry.id), receipt.undo)
        awaitEvent(WearEvent.ShowLogged)
        assertEquals(XHaptic.CONFIRM, haptics().last())
        assertEquals(receipt.entry, stored().single())
        assertNull(vm.sideDraft.value)
        assertNull(vm.feedDraft.value)

        val state = awaitMain { it.lastFeed != null }
        assertEquals(MainMode.IDLE, state.mode)
        assertEquals(receipt.entry.id, state.lastFeed?.id)
    }

    @Test
    fun theSideIsTheTickOrderAndOptional() = test {
        vm.beginFeed()
        vm.chooseFeedType(FeedType.BREAST).join()
        vm.tickSide(Breast.RIGHT)
        vm.tickSide(Breast.LEFT)
        assertEquals(BreastSide.RIGHT_THEN_LEFT, present(vm.sideDraft.value).side)
        vm.tickSide(Breast.RIGHT)
        vm.tickSide(Breast.LEFT)
        assertEquals(emptyList<Breast>(), present(vm.sideDraft.value).ticks)
        vm.saveBreastfeed().join()

        val receipt = present(vm.receipt.value)
        assertEquals(ReceiptKind.FEED_LOGGED, receipt.kind)
        assertNull("nothing ticked: only the time", receipt.entry.breastSide)
        assertNull(stored().single().endMillis)
    }

    @Test
    fun aDoubleTapOnSaveLogsOnce() = test {
        vm.beginFeed()
        vm.chooseFeedType(FeedType.BREAST).join()
        vm.tickSide(Breast.LEFT)
        val first = vm.saveBreastfeed()
        val second = vm.saveBreastfeed()
        first.join()
        second.join()
        assertEquals(1, stored().size)
        assertFalse(present(vm.receipt.value).duplicate)
        assertEquals(listOf(XHaptic.CONFIRM), haptics())
    }

    @Test
    fun aRepeatBreastFeedWithinFiveSecondsShowsAlreadyLogged() = test {
        vm.beginFeed()
        vm.chooseFeedType(FeedType.BREAST).join()
        vm.saveBreastfeed().join()
        clock.advanceBy(2_000)
        vm.beginFeed()
        vm.chooseFeedType(FeedType.BREAST).join()
        vm.tickSide(Breast.RIGHT)
        vm.saveBreastfeed().join()
        assertTrue(present(vm.receipt.value).duplicate)
        assertEquals(1, stored().size)
    }

    @Test
    fun breastFeedsNeverRunOrBlockTheNextOne() = test {
        vm.beginFeed()
        vm.chooseFeedType(FeedType.BREAST).join()
        vm.saveBreastfeed().join()
        clock.advanceBy(5 * minute)
        vm.beginFeed()
        vm.chooseFeedType(FeedType.BREAST).join()
        vm.saveBreastfeed().join()
        assertEquals(2, stored().size)
        assertTrue(stored().none { it.isRunning })
        assertNull(vm.main.value.guard)
    }

    @Test
    fun undoRemovesTheBreastFeed() = test {
        vm.beginFeed()
        vm.chooseFeedType(FeedType.BREAST).join()
        vm.tickSide(Breast.RIGHT)
        vm.saveBreastfeed().join()
        vm.undo().join()
        awaitEvent(WearEvent.ShowUndone)
        assertTrue(stored().isEmpty())
        assertNull(vm.receipt.value)
    }

    @Test
    fun saveWithoutBreastDoesNothing() = test {
        vm.saveBreastfeed().join()
        vm.tickSide(Breast.LEFT)
        assertNull(vm.sideDraft.value)
        assertTrue(stored().isEmpty())
    }

    @Test
    fun justTheTimeLogsAFeedWithNoType() = test {
        vm.beginFeed()
        vm.chooseFeedType(null).join()
        val receipt = present(vm.receipt.value)
        assertEquals(ReceiptKind.FEED_LOGGED, receipt.kind)
        val feed = stored().single()
        assertNull(feed.feedType)
        assertNull(feed.endMillis)
        awaitEvent(WearEvent.ShowLogged)
    }

    @Test
    fun aDiaperWithNothingTickedIsAPlainChange() = test {
        vm.beginDiaper()
        vm.saveDiaper().join()
        val receipt = present(vm.receipt.value)
        assertEquals(ReceiptKind.DIAPER_LOGGED, receipt.kind)
        val diaper = stored().single()
        assertFalse(diaper.pee || diaper.poop)
        awaitEvent(WearEvent.ShowLogged)
    }

    // ---- Diaper --------------------------------------------------------------------------------

    @Test
    fun diaperWithPoopColourAndTexture() = test {
        vm.beginDiaper()
        assertFalse("both checkboxes start off", vm.diaperDraft.value!!.pee || vm.diaperDraft.value!!.poop)
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

        val state = awaitMain { it.guard != null && it.runningSleep != null }
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

        // Wait for the settled state: the guard can show before Room's re-query reports the ended sleep, and on a
        // slow CI runner that intermediate state (guard set, sleep still running) used to fail the assertions below.
        val state = awaitMain { it.guard != null && it.runningSleep == null }
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
        assertEquals(GuardKind.ALREADY_AWAKE, awaitMain { it.guard != null && it.runningSleep == null }.sleepGuard)
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

        assertEquals(GuardKind.ALREADY_ASLEEP, awaitMain { it.guard != null && it.runningSleep != null }.sleepGuard)
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

    // ---- Babies (BABIES_AND_BACKUP §2.5) --------------------------------------------------------

    @Test
    fun withOneBabyMainHasNoBabyChoice() = test {
        seed(SampleData.scenario())
        val state = awaitMain { it.loaded }
        assertFalse(state.hasSeveralBabies)
        assertEquals(listOf(Baby(Baby.FIRST_ID)), state.babies)
        assertEquals(Baby.FIRST_ID, state.babyId)
    }

    @Test
    fun selectingABabySwitchesTheEntriesAndReturnsToMain() = test {
        settings.state.value = SampleData.twinSettings
        seed(SampleData.scenario())
        assertEquals(Baby.FIRST_ID, awaitMain { it.hasSeveralBabies }.babyId)

        vm.selectBaby(SampleData.TWIN_ID).join()
        val ida = awaitMain { it.babyId == SampleData.TWIN_ID && it.lastFeed == null }
        assertNull("Ida's log is empty", ida.lastDiaper)
        assertEquals(SampleData.TWIN_NAME, ida.baby.name)
        assertEquals(SampleData.TWIN_ID, settings.state.value.selectedBabyId)
        awaitEvent(WearEvent.BackToMain)
        assertEquals(listOf(XHaptic.CONFIRM), haptics())

        // A tap now logs for Ida; Noa's log is untouched.
        vm.beginDiaper()
        vm.setPee(true)
        vm.saveDiaper().join()
        assertEquals(SampleData.TWIN_ID, vm.receipt.value?.entry?.babyId)
        assertEquals(1, stored().size)

        vm.selectBaby(Baby.FIRST_ID).join()
        assertEquals("sample-pee-1344", awaitMain { it.babyId == Baby.FIRST_ID && it.lastDiaper?.id == "sample-pee-1344" }.lastDiaper?.id)
    }

    @Test
    fun switchingBabiesDropsWhatWasOpenForThePreviousOne() = test {
        settings.state.value = SampleData.twinSettings
        seed(SampleData.scenario())
        vm.beginFeed()
        vm.beginDiaper()
        vm.beginFeed()
        vm.chooseFeedType(FeedType.FORMULA).join()
        assertNotNull(vm.receipt.value)
        vm.beginFeed()
        vm.chooseFeedType(FeedType.BREAST).join()
        assertNotNull(vm.sideDraft.value)

        vm.selectBaby(SampleData.TWIN_ID).join()
        assertNull(vm.feedDraft.value)
        assertNull(vm.sideDraft.value)
        assertNull(vm.diaperDraft.value)
        assertNull("Undo belonged to Noa's formula", vm.receipt.value)
        // Undo now has nothing to revert.
        vm.undo().join()
        assertEquals(SampleData.scenario().size + 1, repo.observeAllEntries().first().size)
    }

    @Test
    fun switchingBabiesResetsTheDoubleToggleGuard() = test {
        settings.state.value = SampleData.twinSettings
        awaitMain { it.hasSeveralBabies }
        vm.toggleSleep(shownSleepId = null).join()
        assertNotNull(awaitMain { it.runningSleep != null }.runningSleep)

        // Within 2 s of Noa's toggle, but Ida's sleep is another toggle: it starts at once.
        vm.selectBaby(SampleData.TWIN_ID).join()
        awaitMain { it.babyId == SampleData.TWIN_ID && it.runningSleep == null }
        vm.toggleSleep(shownSleepId = null).join()
        val ida = awaitMain { it.babyId == SampleData.TWIN_ID && it.runningSleep != null }
        assertEquals(SampleData.TWIN_ID, ida.runningSleep?.babyId)
        assertNull(ida.guard)
        assertEquals(2, repo.observeAllEntries().first().count { it.isRunning })

        // Ida's own toggle still has its guard.
        vm.toggleSleep(ida.runningSleep?.id).join()
        assertEquals(GuardKind.ALREADY_ASLEEP, awaitMain { it.guard != null }.sleepGuard)
    }

    @Test
    fun selectingTheCurrentBabyKeepsWhatIsOpen() = test {
        settings.state.value = SampleData.twinSettings
        awaitMain { it.hasSeveralBabies }
        vm.beginDiaper()
        vm.selectBaby(Baby.FIRST_ID).join()
        assertNotNull(vm.diaperDraft.value)
        awaitEvent(WearEvent.BackToMain)
    }

    // ---- Time zone -----------------------------------------------------------------------------

    @Test
    fun aNewTimeZoneAppliesWithoutANewViewModel() = test {
        var system: ZoneId = SampleData.ZONE
        val zoneStore = ViewModelStore()
        val factory = viewModelFactory { initializer { MainViewModel(repo, settings, NoopEntrySync, clock, zone = { system }, ticks = flowOf(Unit)) } }
        val travelling = ViewModelProvider.create(zoneStore, factory)[MainViewModel::class]
        try {
            assertEquals(SampleData.ZONE, await(travelling.main) { it.loaded }.zone)
            system = ZoneId.of("Europe/London")
            // The time-zone broadcast (or the next minute tick) re-reads the zone.
            travelling.refresh()
            assertEquals(ZoneId.of("Europe/London"), await(travelling.main) { it.zone == system }.zone)
            travelling.beginDiaper()
            assertEquals(ZoneId.of("Europe/London"), travelling.diaperDraft.value?.zone)
        } finally {
            zoneStore.clear()
        }
    }
}
