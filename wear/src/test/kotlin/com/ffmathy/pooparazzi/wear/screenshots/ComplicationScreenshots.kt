package com.ffmathy.pooparazzi.wear.screenshots

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.domain.Summary
import com.ffmathy.pooparazzi.core.domain.summarize
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.wear.complication.ComplicationDataPreview
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A preview of the complication data (the real [com.ffmathy.pooparazzi.wear.complication.ComplicationContent] output,
 * read at the sample time) on a neutral stand-in face: `wear/screenshots/<lang>/13*-complications*.png`. Watch faces
 * cannot run under Robolectric, so this shows the data, not a real face.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
abstract class ComplicationScreenshots(private val lang: String) {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun use24HourClock() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
    }

    @Test
    fun s13_complications() =
        capture("13-complications", summarize(SampleData.scenario(), SampleData.NOW_1432, SampleData.ZONE))

    @Test
    fun s13b_complicationsAsleep() =
        capture("13b-complications-asleep", summarize(SampleData.scenarioAt0312(), SampleData.NOW_0312, SampleData.ZONE))

    private fun capture(name: String, summary: Summary) {
        compose.setContent { ComplicationDataPreview(summary) }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(filePath = "screenshots/$lang/$name.png")
    }
}

@Config(qualifiers = "w227dp-h227dp-round-watch-xhdpi")
class EnglishComplicationScreenshots : ComplicationScreenshots("en")

@Config(qualifiers = "da-w227dp-h227dp-round-watch-xhdpi")
class DanishComplicationScreenshots : ComplicationScreenshots("da")
