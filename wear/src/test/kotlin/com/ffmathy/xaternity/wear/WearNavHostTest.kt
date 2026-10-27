package com.ffmathy.xaternity.wear

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.SemanticsNodeInteraction
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
import com.ffmathy.xaternity.core.model.Entry
import com.ffmathy.xaternity.core.model.isRunning
import com.ffmathy.xaternity.core.sample.SampleData
import com.ffmathy.xaternity.core.sync.NoopEntrySync
import com.ffmathy.xaternity.wear.logged.LOGGED_INERT_MILLIS
import com.ffmathy.xaternity.wear.logged.UNDONE_MILLIS
import com.ffmathy.xaternity.wear.theme.XWearTheme
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

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
        db = XaternityDatabase.inMemory(context)
        clock = FixedClock(SampleData.NOW_1432)
        repo = RoomEntryRepository(db, clock)
        val factory = viewModelFactory {
            initializer { MainViewModel(repo, NoopEntrySync, clock, SampleData.ZONE, ticks = flowOf(Unit)) }
        }
        vm = ViewModelProvider.create(store, factory)[MainViewModel::class]
        compose.mainClock.autoAdvance = false
        compose.setContent { XWearTheme { AppScaffold { WearNavHost(vm, phoneLink = null) } } }
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
    fun breastfeedThenEndWithASide() {
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
        compose.onNodeWithText("Left → Right").performClick()

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
        waitForDescription("Pee, 13:44").performClick()
        waitFor("Pee · 13:44")
        waitForDescription("5 minutes earlier, 13:39")
    }
}
