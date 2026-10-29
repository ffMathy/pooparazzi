package com.ffmathy.pooparazzi.phone

import android.app.Application
import android.app.Notification
import android.content.Context
import android.provider.Settings
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.settings.AppLanguage
import com.ffmathy.pooparazzi.phone.notify.TimerNotifications
import com.ffmathy.pooparazzi.phone.notify.TimerState
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The sleep notifications as the system's own templates draw them (Android 16, night): the running sleep at 03:12
 * expanded (its duration, the last feed and diaper, today's counts and Woke up), then the woke-up notification after
 * "−10m" (its offsets). Built by [TimerNotifications], turned into the template's [RemoteViews] and captured.
 * Written to `app/screenshots/<lang>/20-notifications.png`.
 */
abstract class NotificationScreenshots(private val language: AppLanguage) {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun use24HourClock() {
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
    }

    @Test
    fun notifications() {
        val zone = SampleData.ZONE
        val entries = SampleData.scenarioAt0312(zone)
        val settings = SampleData.settings.copy(language = language)
        val notifications = TimerNotifications(context)
        val asleep = TimerState.of(entries, settings, SampleData.NOW_0312, zone)
        val running = notifications.buildSleep(asleep, asleep.sleep!!)

        val sleep = entries.single { it.id == asleep.sleep!!.entryId }.copy(endMillis = SampleData.at(3, 2))
        val awake = TimerState.of(entries.map { if (it.id == sleep.id) sleep else it }, settings, SampleData.NOW_0312, zone)
        val wokeUp = notifications.buildWokeUp(awake, sleep, baseMillis = SampleData.NOW_0312, offsetMinutes = -10)

        val views = listOf(running, wokeUp).map { expanded(it) }
        compose.setContent {
            Column(Modifier.testTag(TAG).background(Backdrop).padding(16.dp)) {
                views.forEach { Card(it) }
            }
        }
        compose.onNodeWithTag(TAG).captureRoboImage("screenshots/${language.tag}/20-notifications.png")
    }

    private fun expanded(notification: Notification): RemoteViews =
        Notification.Builder.recoverBuilder(context, notification).createBigContentView()

    @Composable
    private fun Card(view: RemoteViews) {
        Box(Modifier.padding(vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Shade)) {
            AndroidView(factory = { view.apply(it, FrameLayout(it)) }, modifier = Modifier.fillMaxWidth())
        }
    }

    private companion object {
        const val TAG = "notifications"
        val Backdrop = Color(0xFF24201C)
        val Shade = Color(0xFF2E2A27)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w390dp-h640dp-night-xxhdpi", application = Application::class)
class NotificationScreenshotsEnglish : NotificationScreenshots(AppLanguage.EN)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "da-w390dp-h640dp-night-xxhdpi", application = Application::class)
class NotificationScreenshotsDanish : NotificationScreenshots(AppLanguage.DA)
