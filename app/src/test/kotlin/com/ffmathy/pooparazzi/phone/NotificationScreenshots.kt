package com.ffmathy.pooparazzi.phone

import android.app.Application
import android.app.Notification
import android.content.Context
import android.provider.Settings
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.settings.AppLanguage
import com.ffmathy.pooparazzi.phone.notify.RunningTimer
import com.ffmathy.pooparazzi.phone.notify.SleepingBaby
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
 * The per-baby sleep notifications (BABIES_AND_BACKUP §2.3) as the system's own notification template draws them:
 * twins both asleep, so "Noa is asleep" and "Ida is asleep" each with their "Woke up". Written to
 * `app/screenshots/<lang>/28-notification-twins.png`. Rendered by Robolectric from the real [Notification], so the
 * frame is the platform's template on a shade-coloured panel, not a pixel-exact system shade.
 */
abstract class NotificationScreenshots(private val language: String) {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun use24HourClock() {
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
    }

    @Test
    fun sleepNotificationsForTwins() {
        val noa = Baby(Baby.FIRST_ID, SampleData.BABY_NAME)
        val ida = Baby(SampleData.TWIN_ID, SampleData.TWIN_NAME)
        val sleeping = listOf(
            SleepingBaby(noa, RunningTimer("sleep-noa", SampleData.at(13, 5)), lastFeedStartMillis = SampleData.at(12, 40)),
            SleepingBaby(ida, RunningTimer("sleep-ida", SampleData.at(13, 20)), lastFeedStartMillis = SampleData.at(12, 55)),
        )
        val state = TimerState(sleeping = sleeping, babies = listOf(noa, ida), language = AppLanguage.SYSTEM, zone = SampleData.ZONE)
        val notifications = TimerNotifications(context)
        // The expanded template (title, text and the "Woke up" action), as the shade shows the top notification.
        @Suppress("DEPRECATION")
        val views = sleeping.map { Notification.Builder.recoverBuilder(context, notifications.buildSleep(state, it)).createBigContentView() }
        compose.setContent {
            Column(
                Modifier.testTag(TAG).background(Shade).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                views.forEach { view ->
                    AndroidView(
                        factory = { view.apply(it, FrameLayout(it)) },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card),
                    )
                }
            }
        }
        compose.onNodeWithTag(TAG).captureRoboImage("screenshots/$language/28-notification-twins.png")
    }

    private companion object {
        const val TAG = "notifications"
        val Shade = Color(0xFFDCD5CB)
        val Card = Color(0xFFF7F2EA)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi", application = Application::class)
class NotificationScreenshotsEnglish : NotificationScreenshots("en")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "da-w390dp-h844dp-xxhdpi", application = Application::class)
class NotificationScreenshotsDanish : NotificationScreenshots("da")
