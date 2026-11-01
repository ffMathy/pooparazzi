package com.ffmathy.pooparazzi.wear.glance

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.domain.summarize
import com.ffmathy.pooparazzi.core.format.Formatters
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.sync.SyncStatus
import com.ffmathy.pooparazzi.wear.state.MainUiState
import com.ffmathy.pooparazzi.wear.tile.TileUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WatchGlanceTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun use24HourClock() {
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
    }

    @Test
    fun theTileAndMainShowTheSameGlance() {
        val summary = summarize(SampleData.history(), SampleData.NOW_1432, SampleData.ZONE)
        val tile = TileUiState.from(summary, SampleData.BABY_NAME, feedback = null, guard = null)
        val main = MainUiState.from(summary, guard = null, sync = SyncStatus.Unavailable, babies = listOf(Baby(Baby.FIRST_ID, SampleData.BABY_NAME)))
        assertEquals(tile.glance, main.glance)
    }

    @Test
    fun theGlanceCarriesTheWeeksEstimates() {
        val glance = WatchGlance.from(summarize(SampleData.history(), SampleData.NOW_1432, SampleData.ZONE), SampleData.BABY_NAME)
        val words = GlanceText(context.resources, Formatters.from(context, SampleData.ZONE))
        val feed = assertNotNull(glance.nextFeed)
        assertEquals("1h 00m", words.nextShort(feed, glance.now))
        assertEquals("50m", words.nextShort(assertNotNull(glance.nextSleep), glance.now))
        // Under five minutes it is "soon"; a minute past it, how long ago.
        assertEquals("soon", words.nextShort(feed, feed.atMillis - 4 * 60_000L))
        assertEquals("24m ago", words.nextShort(feed, feed.atMillis + 24 * 60_000L))
        assertEquals("expected to sleep again around 15:20", words.nextSleepSpoken(glance.nextSleep!!, asleep = false))
        assertEquals(GlanceTitle.Name(SampleData.BABY_NAME), glance.title)
    }

    @Test
    fun asleepTheTitleIsTheSleepAndTheEstimateIsTheWakeUp() {
        val glance = WatchGlance.from(summarize(SampleData.historyAt0312(), SampleData.NOW_0312, SampleData.ZONE), SampleData.BABY_NAME)
        val words = GlanceText(context.resources, Formatters.from(context, SampleData.ZONE))
        assertTrue(glance.title is GlanceTitle.Asleep)
        assertEquals("2h 15m", words.nextShort(glance.nextSleep!!, glance.now))
        assertEquals("expected to wake around 05:25", words.nextSleepSpoken(glance.nextSleep!!, asleep = true))
    }

    private fun <T : Any> assertNotNull(value: T?): T = requireNotNull(value) { "no estimate" }
}
