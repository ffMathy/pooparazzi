package com.ffmathy.pooparazzi.phone

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.ffmathy.pooparazzi.phone.ui.preview.StatisticsSamples
import com.ffmathy.pooparazzi.phone.ui.statistics.StatisticsActions
import com.ffmathy.pooparazzi.phone.ui.statistics.StatisticsScreen
import com.ffmathy.pooparazzi.phone.ui.theme.NightLightTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
        compose.onNodeWithContentDescription("Bæ. Seneste 7 dage: 0,7 om dagen. Seneste 30 dage: 1,8 om dagen.").assertIsSelected()
        compose.onNodeWithContentDescription(
            "Søvn. Seneste 7 dage: 14 timer 1 minut om dagen. Seneste 30 dage: 14 timer 7 minutter om dagen.",
        ).assertExists()
        compose.onNodeWithContentDescription(
            "Bæ pr. dag, 26. september til 25. oktober. Højest 5 onsdag den 7. oktober. Lavest 0 fredag den 23. oktober.",
        ).assertExists()
        compose.onNodeWithContentDescription("Seneste 7 dage, 19. oktober til 25. oktober").assert(isHeading())
        compose.onNodeWithContentDescription("Seneste 30 dage, 26. september til 25. oktober").assert(clickLabel("Skift periode"))
    }
}
