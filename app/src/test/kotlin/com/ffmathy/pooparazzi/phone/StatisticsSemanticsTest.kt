package com.ffmathy.pooparazzi.phone

import android.app.Application
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.domain.StatsMetric
import com.ffmathy.pooparazzi.core.domain.StatsRange
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.phone.ui.preview.StatisticsSamples
import com.ffmathy.pooparazzi.phone.ui.statistics.StatisticsActions
import com.ffmathy.pooparazzi.phone.ui.statistics.StatisticsRangeDialog
import com.ffmathy.pooparazzi.phone.ui.statistics.StatisticsRangeMenuItems
import com.ffmathy.pooparazzi.phone.ui.statistics.StatisticsScreen
import com.ffmathy.pooparazzi.phone.ui.statistics.StatisticsStateBuilder
import com.ffmathy.pooparazzi.phone.ui.statistics.StatisticsUiState
import com.ffmathy.pooparazzi.phone.ui.theme.NightLightTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * TalkBack names, states and click wiring on Statistics (V1_ADDITIONS §8, DESIGN_SPEC §9.4): rows are radio buttons
 * that speak both averages, the chart is one node with a summary, the header is a heading, the range chip names its
 * range and dates. Samples are seen at Mon 26 Oct 2026 14:32.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi", application = Application::class)
class StatisticsSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private val actions = object : StatisticsActions {
        override fun onBack() { calls += "back" }
        override fun onMetric(metric: StatsMetric) { calls += "metric:$metric" }
        override fun onRange(range: StatsRange) { calls += "range:$range" }
    }

    @Before
    fun use24HourClock() {
        Settings.System.putString(ApplicationProvider.getApplicationContext<Application>().contentResolver, Settings.System.TIME_12_24, "24")
    }

    private fun clickLabel(label: String) = SemanticsMatcher("click label $label") { it.config[SemanticsActions.OnClick].label == label }

    private fun show(state: StatisticsUiState, night: Boolean = true) {
        compose.setContent { NightLightTheme(night = night) { StatisticsScreen(state, actions) } }
        compose.waitForIdle()
    }

    private fun oct(day: Int): LocalDate = LocalDate.of(2026, 10, day)

    /** 64 days cut into 7-day bars from the end: the oldest bar is one day, Sun 23 Aug (1 poop), the lowest. */
    private fun oneDayOldestBar(): StatisticsUiState = StatisticsStateBuilder.build(
        SampleData.history(days = 200),
        SampleData.NOW_1432,
        SampleData.ZONE,
        StatsMetric.POOP,
        StatsRange.Custom(LocalDate.of(2026, 8, 23), oct(25)),
    )

    @Test
    fun theRowsSpeakTheirAveragesAndPoopIsOpen() {
        show(StatisticsSamples.lastThirtyDays())
        compose.onNodeWithContentDescription("Poop. Last 30 days: 1.8 a day.")
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        compose.onNodeWithContentDescription("Pee. Last 30 days: 7.6 a day.").assertExists()
        compose.onNodeWithContentDescription("Sleep. Last 30 days: 14 hours 7 minutes a day.").assertExists()
        compose.onNodeWithContentDescription("Feeds. Last 30 days: 7.1 a day.")
            .assertIsNotSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .performClick()
        assertEquals(listOf("metric:FEEDS"), calls)
    }

    @Test
    fun theChartIsOneNodeWithASummary() {
        show(StatisticsSamples.lastThirtyDays())
        compose.onNodeWithContentDescription(
            "Poop per day, 26 September to 25 October. Highest 5 on Wednesday 7 October. Lowest 0 on Friday 23 October.",
        ).assertExists()
        // The axis dates are drawn but not read on their own: the summary says them.
        compose.onAllNodes(hasText("25 Oct")).assertCountEquals(0)
    }

    /** Only the chosen range is shown: no "Last 7 days" next to it. */
    @Test
    fun onlyTheChosenRangeIsShown() {
        show(StatisticsSamples.lastThirtyDays())
        compose.onAllNodes(hasText("Last 7 days")).assertCountEquals(0)
        compose.onAllNodes(hasContentDescription("Last 7 days", substring = true)).assertCountEquals(0)
    }

    @Test
    fun theLastSevenDaysAreARange() {
        show(StatisticsSamples.lastSevenDays())
        compose.onNodeWithContentDescription("Last 7 days, 19 October to 25 October").assert(clickLabel("Change range"))
        compose.onNodeWithContentDescription("Poop. Last 7 days: 0.7 a day.").assertIsSelected()
        compose.onNodeWithContentDescription(
            "Poop per day, 19 October to 25 October. Highest 2 on Saturday 24 October. Lowest 0 on Friday 23 October.",
        ).assertExists()
    }

    @Test
    fun theRangeChipNamesItsRangeAndOpensTheMenu() {
        show(StatisticsSamples.lastThirtyDays())
        compose.onNodeWithContentDescription("Last 30 days, 26 September to 25 October")
            .assert(clickLabel("Change range"))
            .performClick()
        compose.onNodeWithText("Last 90 days").performClick()
        assertEquals(listOf("range:Last90Days"), calls)
    }

    /** The menu's items on their own (the same composable the chip's menu shows). */
    @Test
    fun theRangeMenuItemsChooseARange() {
        compose.setContent {
            NightLightTheme(night = true) {
                Column {
                    StatisticsRangeMenuItems(StatsRange.Last30Days, onRange = actions::onRange, onChooseDates = { calls += "choose" })
                }
            }
        }
        compose.onNodeWithText("Last 30 days").assertIsSelected()
        compose.onNodeWithText("Last 90 days").assertIsNotSelected()
        compose.onNodeWithText("Last 7 days").assertIsNotSelected().performClick()
        compose.onNodeWithText("All days").performClick()
        compose.onNodeWithText("Choose dates…").assertIsNotSelected().performClick()
        assertEquals(listOf("range:Last7Days", "range:AllDays", "choose"), calls)
    }

    @Test
    fun aCustomRangeIsSaidByItsDates() {
        show(StatisticsSamples.custom())
        compose.onNodeWithContentDescription("Feeds. Chosen dates: 7.1 a day.").assertIsSelected()
        compose.onNodeWithContentDescription("Chosen dates, 5 October to 18 October").assert(clickLabel("Change range"))
        compose.onNodeWithContentDescription(
            "Feeds per day, 5 October to 18 October. Highest 8 on Thursday 15 October. Lowest 6 on Wednesday 14 October.",
        ).assertExists()
    }

    @Test
    fun fewLoggedDaysAreCountedAndSaid() {
        show(StatisticsSamples.firstDays())
        compose.onNodeWithContentDescription("Last 30 days, 26 September to 25 October, 3 of 30 days logged").assertExists()
        compose.onNodeWithContentDescription(
            "Poop per day, 26 September to 25 October. Highest 3 on Friday 23 October. Lowest 0 on Sunday 25 October. " +
                "Days before 23 October are left out.",
        ).assertExists()
    }

    @Test
    fun gapsAreLeftOutAndSaid() {
        show(StatisticsSamples.gaps())
        compose.onNodeWithContentDescription("Last 30 days, 26 September to 25 October, 28 of 30 days logged").assertExists()
        compose.onNodeWithContentDescription(
            "Poop per day, 26 September to 25 October. Highest 5 on Wednesday 7 October. Lowest 0 on Friday 23 October. " +
                "2 days with no diapers logged are left out.",
        ).assertExists()
    }

    @Test
    fun sleepSpeaksDurations() {
        show(StatisticsSamples.sleep(), night = false)
        compose.onNodeWithContentDescription(
            "Sleep per day, 26 September to 25 October. Highest 15 hours 27 minutes on Friday 2 October. " +
                "Lowest 11 hours 24 minutes on Saturday 26 September.",
        ).assertExists()
    }

    @Test
    fun longRangesSpeakTheirBars() {
        show(StatisticsSamples.halfYear(), night = false)
        compose.onNodeWithContentDescription(
            "Poop per day, 29 April to 25 October, each bar 7 days. Highest 3.3 from 11 May to 17 May. " +
                "Lowest 1.1 from 17 August to 23 August.",
        ).assertExists()
    }

    @Test
    fun aOneDayBarIsSpokenLikeADay() {
        show(oneDayOldestBar(), night = false)
        compose.onNodeWithContentDescription(
            "Poop per day, 23 August to 25 October, each bar 7 days. Highest 2.4 from 14 September to 20 September. " +
                "Lowest 1.0 on Sunday 23 August.",
        ).assertExists()
    }

    @Test
    fun theEmptyStateSaysWhenStatisticsStart() {
        show(StatisticsSamples.empty())
        compose.onNodeWithText("Statistics start as soon as something is logged.").assertExists()
        compose.onNodeWithText("Average per day").assertDoesNotExist()
    }

    @Test
    fun beforeAWholeDayTodaySoFarIsShown() {
        show(StatisticsSamples.soFar())
        compose.onNodeWithContentDescription("Poop. Last 30 days: 2.0 a day.").assertIsSelected()
        compose.onNodeWithText(SO_FAR_FOOTNOTE).assertExists()
    }

    @Test
    fun theDateDialogConfirmsItsSelection() {
        compose.setContent {
            NightLightTheme(night = true) {
                StatisticsRangeDialog(
                    initialFrom = LocalDate.of(2026, 9, 26),
                    initialTo = oct(25),
                    firstDay = LocalDate.of(2026, 9, 23),
                    lastDay = oct(25),
                    onConfirm = { from, to -> calls += "confirm:$from:$to" },
                    onDismiss = { calls += "dismiss" },
                )
            }
        }
        compose.onNodeWithText("OK").performClick()
        assertEquals(listOf("confirm:2026-09-26:2026-10-25"), calls)
    }

    @Test
    fun theDateDialogCancels() {
        compose.setContent {
            NightLightTheme(night = true) {
                StatisticsRangeDialog(
                    initialFrom = LocalDate.of(2026, 9, 26),
                    initialTo = oct(25),
                    firstDay = LocalDate.of(2026, 9, 23),
                    lastDay = oct(25),
                    onConfirm = { from, to -> calls += "confirm:$from:$to" },
                    onDismiss = { calls += "dismiss" },
                )
            }
        }
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(listOf("dismiss"), calls)
    }

    @Test
    fun backIsNamedBack() {
        show(StatisticsSamples.lastThirtyDays())
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("back"), calls)
    }

    /** One scrolling column, not a list: the header, all four rows and the open chart scroll together. */
    @Test
    fun thereIsOneScrollableNode() {
        show(StatisticsSamples.lastThirtyDays())
        compose.onAllNodes(hasScrollAction()).assertCountEquals(1)
    }

    private companion object {
        const val SO_FAR_FOOTNOTE =
            "No whole day has been logged yet, so these averages count today so far. From the first whole day on, they count whole days up to yesterday."
    }
}
