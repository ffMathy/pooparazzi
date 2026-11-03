package com.ffmathy.pooparazzi.phone

import android.app.AlarmManager
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.phone.widget.WidgetUpdater
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/** The widgets' minute tick (DESIGN_SPEC §10.11): an exact, non-waking alarm at the start of the next minute. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class WidgetMinuteTickTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))

    @After
    fun reset() = ShadowAlarmManager.setCanScheduleExactAlarms(true)

    @Test
    fun theNextMinuteIsTheStartOfTheFollowingMinute() {
        assertEquals(SampleData.at(14, 33), WidgetUpdater.nextMinute(SampleData.NOW_1432))
        assertEquals(SampleData.at(14, 33), WidgetUpdater.nextMinute(SampleData.NOW_1432 + 59_999))
        assertEquals(SampleData.at(14, 34), WidgetUpdater.nextMinute(SampleData.at(14, 33)))
    }

    @Test
    fun theTickIsAnExactAlarmThatDoesNotWakeThePhone() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        WidgetUpdater.scheduleMinuteTick(context, SampleData.NOW_1432 + 12_345)
        val alarm = alarms.scheduledAlarms.single()
        assertEquals(AlarmManager.RTC, alarm.type)
        assertEquals(SampleData.at(14, 33), alarm.triggerAtMs)
        assertEquals(ShadowAlarmManager.WINDOW_EXACT, alarm.windowLengthMs)
    }

    @Test
    fun withoutTheExactAlarmPermissionTheTickFallsBackToAnInexactAlarm() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        WidgetUpdater.scheduleMinuteTick(context, SampleData.NOW_1432)
        val alarm = alarms.scheduledAlarms.single()
        assertEquals(SampleData.at(14, 33), alarm.triggerAtMs)
        assertEquals(ShadowAlarmManager.WINDOW_HEURISTIC, alarm.windowLengthMs)
    }

    @Test
    fun aNewTickReplacesTheQueuedOne() {
        WidgetUpdater.scheduleMinuteTick(context, SampleData.NOW_1432)
        WidgetUpdater.scheduleMinuteTick(context, SampleData.at(14, 33))
        assertEquals(SampleData.at(14, 34), alarms.scheduledAlarms.single().triggerAtMs)
    }
}
