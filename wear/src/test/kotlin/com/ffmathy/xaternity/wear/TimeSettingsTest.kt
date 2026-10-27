package com.ffmathy.xaternity.wear

import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A new time zone or 12/24 h setting is not a configuration change, and the activity can live for days: the app
 * listens for the system broadcasts and re-reads both (the surfaces' "since 02:05", the app's times and day).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimeSettingsTest {
    private val app: WearApp get() = ApplicationProvider.getApplicationContext()

    @Test
    fun aTimeZoneOr24HourChangeIsPickedUp() {
        val before = app.timeSettingsChanges.value
        app.sendBroadcast(Intent(Intent.ACTION_TIMEZONE_CHANGED))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(before + 1, app.timeSettingsChanges.value)

        // The 12/24 h toggle is sent as TIME_SET.
        app.sendBroadcast(Intent(Intent.ACTION_TIME_CHANGED))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(before + 2, app.timeSettingsChanges.value)
    }
}
