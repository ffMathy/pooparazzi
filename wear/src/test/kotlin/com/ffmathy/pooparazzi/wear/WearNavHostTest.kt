package com.ffmathy.pooparazzi.wear

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavHostController
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ApplicationProvider
import androidx.wear.compose.material3.AppScaffold
import com.ffmathy.pooparazzi.core.data.FixedClock
import com.ffmathy.pooparazzi.core.data.RoomEntryRepository
import com.ffmathy.pooparazzi.core.data.PooparazziDatabase
import com.ffmathy.pooparazzi.core.domain.epochMillis
import com.ffmathy.pooparazzi.core.format.VolumeFormatter
import com.ffmathy.pooparazzi.core.model.BottleContents
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.PoopColor
import com.ffmathy.pooparazzi.core.model.isRunning
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.sync.NoopEntrySync
import com.ffmathy.pooparazzi.wear.logged.LOGGED_INERT_MILLIS
import com.ffmathy.pooparazzi.wear.logged.UNDONE_MILLIS
import com.ffmathy.pooparazzi.wear.testing.FakeSettingsRepository
import com.ffmathy.pooparazzi.wear.theme.XWearTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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

    private lateinit var db: PooparazziDatabase
    private lateinit var clock: FixedClock
    private lateinit var repo: RoomEntryRepository
    private lateinit var vm: MainViewModel
    private lateinit var nav: NavHostController
    private val store = ViewModelStore()
    private val settings = FakeSettingsRepository()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
        db = PooparazziDatabase.inMemory(context)
        clock = FixedClock(SampleData.NOW_1432)
        repo = RoomEntryRepository(db, clock, selectedBaby = settings.state.map { it.selectedBaby.id })
        val factory = viewModelFactory {
            initializer { MainViewModel(repo, settings, NoopEntrySync, clock, zone = { SampleData.ZONE }, ticks = flowOf(Unit)) }
        }
        vm = ViewModelProvider.create(store, factory)[MainViewModel::class]
        compose.mainClock.autoAdvance = false
        compose.setContent {
            XWearTheme {
                AppScaffold {
                    val controller = rememberSwipeDismissableNavController()
                    nav = controller
                    WearNavHost(vm, navController = controller)
                }
            }
        }
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

    /**
     * Centres the list item with [key] (the pinned Save covers the end of a list at rest, so an item merely scrolled
     * into view could still sit under it).
     */
    private fun scrollTo(key: String) {
        compose.onNode(hasScrollToKeyAction()).performScrollToKey(key)
        settle()
    }

    /** The routes of the top two screens: the one showing and the one a swipe right returns to. */
    private fun topRoutes(): List<String?> = listOf(nav.previousBackStackEntry?.destination?.route, nav.currentBackStackEntry?.destination?.route)

    private fun at(hour: Int, minute: Int) = epochMillis(SampleData.TODAY, hour, minute, SampleData.ZONE)

    @Test
    fun bottleThenSaveThenUndo() {
        waitForDescription("Feed.").performClick()
        waitFor("Feed · 14:32")
        settle(600) // Past the picker cooldown.
        compose.onNodeWithText("Bottle").performClick()

        // Bottle: the details are optional, so Save at once records just the time.
        waitFor("Bottle · 14:32")
        assertTrue("nothing is logged before Save", entries().isEmpty())
        settle(1_000)
        compose.onNodeWithText("Save").performClick()

        waitFor("Bottle at 14:32")
        assertEquals(1, entries().size)
        settle(LOGGED_INERT_MILLIS + 100L)
        compose.onNodeWithText("Undo").performClick()

        waitFor("Undone")
        assertTrue(entries().isEmpty())
        settle(UNDONE_MILLIS + 100)
        waitForDescription("Start sleep")
    }

    @Test
    fun theTimeOnLoggedMovesTheFeedAndReturnsToLogged() {
        waitForDescription("Feed.").performClick()
        waitFor("Feed · 14:32")
        settle(600)
        compose.onNodeWithText("Food").performClick()

        waitFor("Food at 14:32")
        settle(LOGGED_INERT_MILLIS + 100L)
        compose.onNodeWithContentDescription("Food at 14:32").performClick()

        // Time: "Feed" over 14:32; −5m twice, then Done.
        waitForDescription("Feed, 14:32")
        settle(600)
        compose.onNodeWithContentDescription("5 minutes earlier").performClick()
        settle()
        compose.onNodeWithContentDescription("5 minutes earlier").performClick()
        settle()
        waitForDescription("Feed, 14:22, 10 minutes earlier")
        compose.onNodeWithText("Done").performClick()

        waitFor("Food at 14:22")
        assertEquals(at(14, 22), entries().single().startMillis)
    }

    @Test
    fun bottleWithContentsAmountAndAnEarlierTime() {
        waitForDescription("Feed.").performClick()
        settle(600)
        compose.onNodeWithText("Bottle").performClick()
        waitFor("Bottle · 14:32")
        settle(1_000)

        // Everything on the one screen: contents, an amount chip and a step, then the time.
        compose.onNodeWithText("Formula").performClick()
        settle()
        // The chips and steps follow the region (ml, or fl oz in the US), so the amounts come from the formatter.
        val volume = VolumeFormatter.from(ApplicationProvider.getApplicationContext())
        val chip = volume.quickAmounts(null).last()
        scrollTo("amountChips0")
        compose.onNodeWithContentDescription(volume.format(chip)).performClick()
        settle()
        assertEquals(chip, vm.bottleDraft.value?.amountMl)
        // The stepper above the chips shows it, and steps on from it.
        scrollTo("amount")
        val amount = checkNotNull(volume.amountAt(volume.stepOf(chip) + 1))
        compose.onNodeWithContentDescription(" more", substring = true).performClick()
        settle()
        val shown = volume.format(amount)
        waitForDescription("Amount, $shown")

        scrollTo("timeNudges0")
        compose.onNodeWithContentDescription("5 minutes earlier").performClick()
        settle()
        compose.onNodeWithContentDescription("5 minutes earlier").performClick()
        settle()
        waitForDescription("10 minutes earlier, 14:22")
        assertEquals(at(14, 22), vm.bottleDraft.value?.at)
        assertEquals("no logging flow opens a screen of its own", listOf(WearRoutes.FEED_TYPE, WearRoutes.BOTTLE), topRoutes())
        compose.onNodeWithText("Save").performClick()

        waitFor("Bottle at 14:22")
        waitFor("Formula · $shown")
        val feed = entries().single()
        assertEquals(at(14, 22), feed.startMillis)
        assertEquals(BottleContents.FORMULA, feed.bottleContents)
        assertEquals(amount, feed.amountMl)
    }

    @Test
    fun noLoggingFlowOpensASubScreen() {
        waitForDescription("Diaper.").performClick()
        waitFor("Diaper · 14:32")
        settle(600)
        compose.onNodeWithText("Pee").performClick()
        compose.onNodeWithText("Poop").performClick()
        settle(600) // Past the cooldown of what Poop revealed.
        scrollTo("colour0")
        compose.onNodeWithText("Yellow").performClick()
        settle()
        scrollTo("texture0")
        compose.onNodeWithText("Seedy").performClick()
        settle()
        scrollTo("timeNudges0")
        compose.onNodeWithContentDescription("15 minutes earlier").performClick()
        settle(1_000)
        assertEquals(listOf(WearRoutes.MAIN, WearRoutes.DIAPER), topRoutes())
        waitForDescription("15 minutes earlier, 14:17")

        compose.onNodeWithText("Save").performClick()
        waitFor("Pee + poop at 14:17")
        val diaper = entries().single()
        assertTrue(diaper.pee && diaper.poop)
        assertEquals(PoopColor.YELLOW, diaper.poopColor)
        assertEquals(com.ffmathy.pooparazzi.core.model.PoopTexture.SEEDY, diaper.poopTexture)
        assertEquals(at(14, 17), diaper.startMillis)
    }

    @Test
    fun aDiaperWithNothingSetStillLogs() {
        waitForDescription("Diaper.").performClick()
        waitFor("Diaper · 14:32")
        settle(600)
        compose.onNodeWithText("Save").performClick()
        waitFor("Diaper at 14:32")
        val diaper = entries().single()
        assertTrue(!diaper.pee && !diaper.poop)
    }

    @Test
    fun aBottleWithNothingSetStillLogs() {
        waitForDescription("Feed.").performClick()
        settle(600)
        compose.onNodeWithText("Bottle").performClick()
        waitFor("Bottle · 14:32")
        settle(1_000)
        compose.onNodeWithText("Save").performClick()
        waitFor("Bottle at 14:32")
        val bottle = entries().single { it.kind == com.ffmathy.pooparazzi.core.model.EntryKind.FEED }
        assertEquals(null, bottle.amountMl)
        assertEquals(null, bottle.bottleContents)
    }

    @Test
    fun diaperWithPeeIsSaved() {
        waitForDescription("Diaper.").performClick()
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
    fun breastThenSideThenSaveLogsAnInstantWithTheTickOrder() {
        waitForDescription("Feed.").performClick()
        settle(600)
        compose.onNodeWithText("Breast").performClick()
        waitFor("Which side?")
        assertTrue("nothing is logged before Save", entries().isEmpty())
        settle(600)
        // Tick Left, then Right: the order is the order recorded, read as "Left then right".
        compose.onNodeWithContentDescription("Left").performClick()
        settle()
        compose.onNodeWithContentDescription("Right").performClick()
        settle()
        compose.onNodeWithContentDescription("Left then right").assertExists()
        // The Feed type screen has slid away once the transition is over.
        settle(1_000)
        compose.onNodeWithText("Save").performClick()

        waitFor("Breast at 14:32")
        // The side label is kept on one line with no-break spaces around the arrow.
        waitFor("Left\u00A0→\u00A0Right")
        val feed = entries().single()
        assertEquals(com.ffmathy.pooparazzi.core.model.BreastSide.LEFT_THEN_RIGHT, feed.breastSide)
        assertEquals(SampleData.NOW_1432, feed.startMillis)
        assertTrue(feed.endMillis == null && !feed.isRunning)
    }

    @Test
    fun aDoubleTapOnAColourChipLeavesItSelected() {
        waitForDescription("Diaper.").performClick()
        waitFor("Diaper · 14:32")
        settle(600)
        compose.onNodeWithText("Poop").performClick()
        settle(600)
        // The colours sit below the fold, under the pinned Save button: bring them into view first.
        scrollTo("colour0")
        val yellow = compose.onNodeWithText("Yellow")
        yellow.performClick()
        yellow.performClick()
        settle(600)

        // Chosen once, not chosen and cleared; still on Diaper with its unsaved choices.
        assertEquals(PoopColor.YELLOW, vm.diaperDraft.value?.color)
        assertTrue(vm.diaperDraft.value?.poop == true)
        compose.onNode(hasText("Yellow") and isSelected(true)).assertExists()
        assertEquals(listOf(WearRoutes.MAIN, WearRoutes.DIAPER), topRoutes())
    }

    // ---- Babies (BABIES_AND_BACKUP §2.5) --------------------------------------------------------

    @Test
    fun withOneBabyMainHasNoBabyButton() {
        runBlocking { repo.applyRemoteAll(SampleData.scenario()) }
        waitForDescription("Last feed, bottle")
        settle()
        assertTrue(compose.onAllNodes(hasContentDescription("Switch baby", substring = true)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun theBabyPickerSwitchesTheBabyAndReturnsToMain() {
        settings.state.value = SampleData.twinSettings
        runBlocking { repo.applyRemoteAll(SampleData.scenario()) }
        waitForDescription("Last feed, bottle")
        waitForDescription("Noa. Switch baby").performClick()

        waitFor("Switch baby")
        settle(600) // Past the picker cooldown.
        // The current baby is marked.
        compose.onNode(hasText("Noa") and isSelected(true)).assertExists()
        compose.onNode(hasText("Ida") and isSelected(false)).assertExists()
        compose.onNodeWithText("Ida").performClick()

        // Back on Main, now for Ida, whose log is empty.
        waitForDescription("No feeds yet")
        assertEquals(SampleData.TWIN_ID, settings.state.value.selectedBabyId)
        settle(1_000) // The picker has slid away once the transition is over.
        waitForDescription("Ida. Switch baby")
        // The diaper is Ida's.
        waitForDescription("Diaper.").performClick()
        waitFor("Diaper · 14:32")
        settle(600)
        compose.onNodeWithText("Save").performClick()
        waitFor("Diaper at 14:32")
        assertEquals(SampleData.TWIN_ID, entries().single().babyId)
    }

    private fun isSelected(selected: Boolean): SemanticsMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Selected, selected)
}
