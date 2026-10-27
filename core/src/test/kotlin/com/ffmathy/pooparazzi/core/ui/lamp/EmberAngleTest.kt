package com.ffmathy.pooparazzi.core.ui.lamp

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.ffmathy.pooparazzi.core.domain.LampState
import com.ffmathy.pooparazzi.core.model.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The ember moves only for the one 6° step per minute (DESIGN_SPEC §2.2, §5.10); everything else snaps. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EmberAngleTest {
    @get:Rule val compose = createComposeRule()

    private data class Orbit(val minutes: Long, val event: LampEvent, val running: Boolean = true, val id: String? = null, val jump: Boolean = false)

    private val orbitState = mutableStateOf(Orbit(67, LampEvent.SLEEP, id = "sleep"))

    /** Writes from the test thread reach the recomposer once apply notifications are sent. */
    private var orbit: Orbit
        get() = orbitState.value
        set(value) {
            orbitState.value = value
            Snapshot.sendApplyNotifications()
        }
    private var angle = Float.NaN

    private fun start() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val o = orbit
            angle = rememberEmberAngle(o.minutes, o.event, o.running, o.id, o.jump).value
        }
        compose.mainClock.advanceTimeByFrame()
    }

    /** A snap needs a frame to recompose and one for the effect; 100 ms is still far inside the 600 ms step. */
    private fun after(millis: Long): Float {
        compose.mainClock.advanceTimeBy(millis)
        return angle
    }

    @Test
    fun aRunningFeedTakingOverFromASleepSnapsInsteadOfSweepingBack() {
        start()
        assertEquals(402f, angle)
        orbit = Orbit(0, LampEvent.FEED, id = "feed") // V1 §2.1: the running feed wins
        assertEquals(0f, after(100))
        orbit = Orbit(67, LampEvent.SLEEP, id = "sleep") // the feed ends: back to the sleep, at once
        assertEquals(402f, after(100))
    }

    @Test
    fun oneMinuteStepsAnimateOverSixHundredMillis() {
        start()
        orbit = orbit.copy(minutes = 68)
        val mid = after(250)
        assertTrue("mid-step: $mid", mid > 402f && mid < 408f)
        assertEquals(408f, after(600))
    }

    @Test
    fun adjustStartSyncAndReducedMotionSnap() {
        start()
        orbit = orbit.copy(minutes = 72) // Adjust start −5m
        assertEquals(432f, after(100))
        orbit = orbit.copy(id = "synced-in", minutes = 73) // another sleep, one minute on
        assertEquals(438f, after(100))
        orbit = orbit.copy(minutes = 74, jump = true)
        assertEquals(444f, after(100))
    }

    @Test
    fun theOrbitAppearingStartsAtItsOwnAngle() {
        orbit = Orbit(0, LampEvent.FEED, running = false)
        start()
        orbit = Orbit(25, LampEvent.SLEEP, id = "synced") // a sleep started on the watch arrives
        assertEquals(150f, after(100))
    }

    @Test
    fun sizeDefaultsToTheGeometry() {
        var measured = 0
        compose.setContent {
            Lamp(
                LampState.SleepRunning(Entry.sleep(0L, id = "s"), 67 * 60_000L),
                LampVariant.WATCH,
                geometry = LampGeometry.WatchSmall,
                modifier = Modifier.onSizeChanged { measured = it.width },
            )
        }
        compose.waitForIdle()
        val expected = with(compose.density) { 93.dp.roundToPx() }
        assertEquals(expected, measured)
    }
}
