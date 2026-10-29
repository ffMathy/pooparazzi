package com.ffmathy.pooparazzi.phone

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.ffmathy.pooparazzi.core.domain.StatsMetric
import com.ffmathy.pooparazzi.core.domain.StatsRange
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.phone.ui.preview.StatisticsSamples
import com.ffmathy.pooparazzi.phone.ui.statistics.StatisticsActions
import com.ffmathy.pooparazzi.phone.ui.statistics.StatisticsScreen
import com.ffmathy.pooparazzi.phone.ui.statistics.StatisticsStateBuilder
import com.ffmathy.pooparazzi.phone.ui.theme.NightLightTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Statistics in Danish (V1_ADDITIONS §5, §8): decimal commas, Danish durations, "onsdag den 7. oktober" and full month
 * names for TalkBack.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "da-w390dp-h844dp-xxhdpi", application = Application::class)
class StatisticsDanishSemanticsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun clickLabel(label: String) = SemanticsMatcher("click label $label") { it.config[SemanticsActions.OnClick].label == label }

    @Test
    fun rowsChartHeaderAndChipSpeakDanish() {
        compose.setContent { NightLightTheme(night = true) { StatisticsScreen(StatisticsSamples.lastThirtyDays(), StatisticsActions.None) } }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Bæ. Seneste 30 dage: 1,8 om dagen.").assertIsSelected()
        compose.onNodeWithContentDescription("Søvn. Seneste 30 dage: 14 timer 7 minutter om dagen.").assertExists()
        compose.onNodeWithContentDescription(
            "Bæ pr. dag, 26. september til 25. oktober. Højest 5 onsdag den 7. oktober. Lavest 0 fredag den 23. oktober.",
        ).assertExists()
        compose.onNodeWithContentDescription("Seneste 30 dage, 26. september til 25. oktober").assert(clickLabel("Skift periode"))
    }

    @Test
    fun barsOfSeveralDaysSpeakDanish() {
        // 64 days in 7-day bars from the end: the oldest bar is one day, søndag 23. august, and the lowest.
        val state = StatisticsStateBuilder.build(
            SampleData.history(days = 200),
            SampleData.NOW_1432,
            SampleData.ZONE,
            StatsMetric.POOP,
            StatsRange.Custom(LocalDate.of(2026, 8, 23), LocalDate.of(2026, 10, 25)),
        )
        compose.setContent { NightLightTheme(night = false) { StatisticsScreen(state, StatisticsActions.None) } }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(
            "Bæ pr. dag, 23. august til 25. oktober, hver søjle 7 dage. Højest 2,4 fra 14. september til 20. september. " +
                "Lavest 1,0 søndag den 23. august.",
        ).assertExists()
    }
}
