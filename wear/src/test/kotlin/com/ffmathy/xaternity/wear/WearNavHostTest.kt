package com.ffmathy.xaternity.wear

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import androidx.wear.compose.material3.AppScaffold
import com.ffmathy.xaternity.core.data.FixedClock
import com.ffmathy.xaternity.core.data.RoomEntryRepository
import com.ffmathy.xaternity.core.data.XaternityDatabase
import com.ffmathy.xaternity.core.domain.epochMillis
import com.ffmathy.xaternity.core.model.Entry
import com.ffmathy.xaternity.core.model.PoopColor
import com.ffmathy.xaternity.core.model.isRunning
import com.ffmathy.xaternity.core.sample.SampleData
import com.ffmathy.xaternity.core.sync.NoopEntrySync
import com.ffmathy.xaternity.wear.logged.LOGGED_INERT_MILLIS
import com.ffmathy.xaternity.wear.logged.UNDONE_MILLIS
import com.ffmathy.xaternity.wear.phone.PhoneLink
import com.ffmathy.xaternity.wear.theme.XWearTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The navigation graph end to end: real ViewModel and repository (in-memory), a fixed clock, English UI. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w227dp-h227dp-round-watch-xhdpi")
class WearNavHostTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: XaternityDatabase
    private lateinit var clock: FixedClock
    private lateinit var repo: RoomEntryRepository
    private lateinit var vm: MainViewModel
    private val store = ViewModelStore()
    private val phone = FakePhoneLink()

    /** A phone that is not reachable unless a test says so, and that cannot open History unless told it can. */
    private class FakePhoneLink : PhoneLink {
        override val available = MutableStateFlow(false)
        var opens = false
        var requests = 0

        override suspend fun openHistory(): Boolean {
            requests++
            return opens
        }
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
        db = XaternityDatabase.inMemory(context)
        clock = FixedClock(SampleData.NOW_1432)
        repo = RoomEntryRepository(db, clock)
        val factory = viewModelFactory {
            initializer { MainViewModel(repo, NoopEntrySync, clock, zone = { SampleData.ZONE }, ticks = flowOf(Unit)) }
        }
        vm = ViewModelProvider.create(store, factory)[MainViewModel::class]
        compose.mainClock.autoAdvance = false
        compose.setContent { XWearTheme { AppScaffold { WearNavHost(vm, phoneLink = phone) } } }
        settle()
    }

    @After
    fun tearDown() {
        store.clear()
        db.close()
    }

    /** Lets Room deliver and the UI catch up, then moves the frame clock by [millis]. */
    private fun settle(millis: Long = 32) {
        compose.mainClock.advanceTimeBy(millis)
        compose.waitForIdle()
    }

    private fun waitFor(text: String): SemanticsNodeInteraction {
        compose.waitUntil(5_000) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        return compose.onNodeWithText(text, substring = true)
    }

    private fun waitForDescription(description: String): SemanticsNodeInteraction {
        compose.waitUntil(5_000) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodes(hasContentDescription(description, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        return compose.onNodeWithContentDescription(description, substring = true)
    }

    private fun entries(): List<Entry> = runBlocking { repo.observeEntries().first() }

    private fun at(hour: Int, minute: Int) = epochMillis(SampleData.TODAY, hour, minute, SampleData.ZONE)

    @Test
    fun formulaThenUndo() {
        waitFor("Feed").performClick()
        waitFor("Feed · 14:32")
        settle(600) // Past the picker cooldown.
        compose.onNodeWithText("Formula").performClick()

        waitFor("Formula at 14:32")
        assertEquals(1, entries().size)
        settle(LOGGED_INERT_MILLIS + 100L)
        compose.onNodeWithText("Undo").performClick()

        waitFor("Undone")
        assertTrue(entries().isEmpty())
        settle(UNDONE_MILLIS + 100)
        waitForDescription("Start sleep")
    }

    @Test
    fun diaperWithPeeIsSaved() {
        waitFor("Diaper").performClick()
        waitFor("Diaper · 14:32")
        settle(600)
        compose.onNodeWithText("Pee").performClick()
        settle()
        compose.onNodeWithText("Save").performClick()

        waitFor("Pee at 14:32")
        val diaper = entries().single()
        assertTrue(diaper.pee)
        assertTrue(!diaper.poop)
    }

    @Test
    fun startSleepThenWokeUp() {
        waitForDescription("Start sleep").performClick()
        waitForDescription("Woke up, end sleep")
        assertTrue(entries().single().isRunning)

        clock.advanceBy(10 * 60_000L)
        vm.refresh()
        settle()
        compose.onNodeWithContentDescription("Woke up, end sleep").performClick()
        waitFor("Woke up at 14:42")
        waitFor("Slept 10m")
    }

    @Test
    fun breastfeedThenEndWithTickedSides() {
        waitFor("Feed").performClick()
        settle(600)
        compose.onNodeWithText("Breast").performClick()
        waitForDescription("End feed")

        clock.advanceBy(18 * 60_000L)
        vm.refresh()
        settle()
        compose.onNodeWithContentDescription("End feed").performClick()
        waitFor("Which side?")
        settle(600)
        // Tick Left, then Right: the order is the order recorded, read as "Left then right".
        compose.onNodeWithContentDescription("Left").performClick()
        settle()
        compose.onNodeWithContentDescription("Right").performClick()
        settle()
        compose.onNodeWithContentDescription("Left then right").assertExists()
        // Main (with its own "End feed") has slid away once the transition is over.
        settle(1_000)
        compose.onNodeWithContentDescription("End feed").performClick()

        waitFor("Feed ended")
        // The side label is kept on one line with no-break spaces around the arrow.
        waitFor("Left\u00A0→\u00A0Right · 18m")
        assertTrue(!entries().single().isRunning)
    }

    @Test
    fun historyOpensTodayAndARowOpensAdjust() {
        runBlocking { repo.applyRemoteAll(SampleData.scenario()) }
        waitForDescription("Last feed, formula")
        // Hero, Awake, last diaper, History: the list continues below the fold.
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        waitFor("History").performClick()
        waitFor("Today")
        settle(600) // Past the screen transition: a tap only counts on a settled screen.
        waitForDescription("Pee, 13:44").performClick()
        waitFor("Pee · 13:44")
        waitForDescription("5 minutes earlier, 13:39")
    }

    @Test
    fun aDoubleTapOnAnAdjustChoiceMovesTheTimeOnceAndReturnsToMain() {
        runBlocking { repo.applyRemoteAll(SampleData.scenario()) }
        waitForDescription("Last feed, formula")
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        // The Awake card adjusts the end of the last sleep (13:27).
        waitForDescription("Awake 1 hour 5 minutes").performClick()
        waitFor("Woke up · 13:27")
        settle(600)
        val minusFive = compose.onNodeWithContentDescription("5 minutes earlier, 13:22")
        minusFive.performClick()
        minusFive.performClick()
        settle(600)

        assertEquals("once, not −10m", at(13, 22), runBlocking { repo.get("sample-sleep-1240") }?.endMillis)
        // Back on Main (a second pop used to remove Main itself and leave a blank screen).
        waitForDescription("Start sleep")
        waitForDescription("Last feed, formula")
    }

    @Test
    fun aDoubleTapOnAColourReturnsToTheDiaperScreenOnce() {
        waitFor("Diaper").performClick()
        waitFor("Diaper · 14:32")
        settle(600)
        compose.onNodeWithText("Poop").performClick()
        settle()
        // Colour sits below the fold, under the pinned Save button: bring it into view first.
        compose.onNode(hasScrollToIndexAction() and hasAnyDescendant(hasText("Diaper · 14:32"))).performScrollToIndex(3)
        settle(600)
        waitFor("Colour").performClick()
        waitFor("Yellow")
        settle(600)
        val yellow = compose.onNodeWithText("Yellow")
        yellow.performClick()
        yellow.performClick()
        settle(600)

        // Still on Diaper with its unsaved choices (its Texture row is in view), not popped back to Main.
        waitFor("Texture")
        assertEquals(PoopColor.YELLOW, vm.diaperDraft.value?.color)
        assertTrue(vm.diaperDraft.value?.poop == true)
    }

    @Test
    fun moreOnPhoneSaysSoWhenThePhoneCannotOpenHistory() {
        phone.available.value = true
        runBlocking { repo.applyRemoteAll(SampleData.scenario()) }
        waitForDescription("Last feed, formula")
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
        waitFor("History").performClick()
        waitFor("Today")
        settle(600)
        // Main stays composed behind Today (for swipe-to-dismiss): scroll Today's own list.
        compose.onNode(hasScrollToIndexAction() and hasAnyDescendant(hasText("Today"))).performScrollToIndex(SampleData.scenario().size)
        settle(600)
        waitFor("More on phone").performClick()

        waitForDescription("Couldn't open on phone")
        assertEquals(1, phone.requests)
    }
}
