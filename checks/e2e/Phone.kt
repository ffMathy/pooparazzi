package com.ffmathy.pooparazzi.checks

import android.app.Application
import android.text.format.DateFormat
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.ffmathy.pooparazzi.core.Core
import com.ffmathy.pooparazzi.phone.MainActivity
import com.ffmathy.pooparazzi.phone.account.AppAccounts
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * The checks' phone: the real app (PooparazziApp and MainActivity, with its Room database, settings and every screen)
 * on a 390 × 844 dp phone, run on the JVM by Robolectric, as the app's own tests run. A check sees and does only what a
 * user can: it finds what is on the screen by its text or its spoken name, taps it, goes back, and lets time pass. It
 * never reaches into the app's code, so it works the same against a rebuilt app.
 *
 * A check starts from a fresh install: [freshInstall] goes through the first run ("Your baby", then the
 * introduction) as a user who skips both.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
abstract class Phone {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @After
    fun closeTheApp() {
        Core.reset()
        AppAccounts.reset()
    }

    /** The first run of a new install, skipped: "Your baby" › Skip for now, the introduction › Skip, then Home. */
    fun freshInstall() {
        waitFor("Skip for now")
        tap("Skip for now")
        waitFor("Skip")
        tap("Skip")
        waitFor("Start sleep")
        // Home ignores taps for a moment after a screen closes, as it does after a sheet.
        pass(1_000)
    }

    /** Waits until something on the screen reads [text] (or is spoken as [text]). */
    fun waitFor(text: String, timeoutMillis: Long = 10_000) {
        compose.waitUntil(timeoutMillis) { all(text).isNotEmpty() }
    }

    /** Waits until nothing on the screen reads [text]. */
    fun waitUntilGone(text: String, timeoutMillis: Long = 10_000) {
        compose.waitUntil(timeoutMillis) { all(text).isEmpty() }
    }

    /** Whether something on the screen reads [text] (or is spoken as [text]). */
    fun shows(text: String): Boolean = all(text).isNotEmpty()

    /** Whether some text on the screen contains [part]. */
    fun showsPart(part: String): Boolean =
        compose.onAllNodes(hasText(part, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Taps what reads [text], or the button spoken as [text], and gives the app a moment to answer. */
    fun tap(text: String) {
        button(text).performClick()
        pass(300)
    }

    /** Goes back, as the system's back gesture or button does. */
    fun goBack() {
        Espresso.pressBack()
        pass(800)
    }

    /** Whether what reads [text] can be tapped now: it is shown, and enabled. */
    fun canTap(text: String): Boolean =
        compose.onAllNodes(named(text) and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() ||
            compose.onAllNodes(named(text) and hasClickAction() and isEnabled(), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Lets [millis] go by: the phone's wall clock and its uptime clock both move on, and the app catches up. */
    fun pass(millis: Long) {
        Thread.sleep(millis)
        ShadowLooper.idleMainLooper(millis, TimeUnit.MILLISECONDS)
        compose.waitForIdle()
    }

    /** The phone's clock time as the app writes it, [minutesFromNow] from now: "14:32", or "2:32 PM" on a 12-hour phone. */
    fun clock(minutesFromNow: Long = 0): String = format(LocalTime.now().plusMinutes(minutesFromNow))

    /** The clock times [minutesFromNow] from now and a minute before: what the app wrote a moment ago, if the minute has turned since. */
    fun recentClocks(minutesFromNow: Long = 0): List<String> =
        listOf(0L, -1L).map { format(LocalTime.now().plusMinutes(minutesFromNow + it)) }.distinct()

    /** Whether the screen shows [text] with "{time}" as the clock time [minutesFromNow] from now (or a minute before). */
    fun showsAt(text: String, minutesFromNow: Long = 0): Boolean =
        recentClocks(minutesFromNow).any { shows(text.replace("{time}", it)) }

    fun assertShowsAt(text: String, minutesFromNow: Long = 0) =
        assertTrue("expected \"$text\" with {time} one of ${recentClocks(minutesFromNow)} on the screen:\n${screen()}", showsAt(text, minutesFromNow))

    fun assertShows(text: String) = assertTrue("expected \"$text\" on the screen:\n${screen()}", shows(text))

    fun assertGone(text: String) = assertTrue("expected no \"$text\" on the screen:\n${screen()}", !shows(text))

    fun assertCanTap(text: String) = assertTrue("expected \"$text\" to be tappable:\n${screen()}", canTap(text))

    fun assertCannotTap(text: String) = assertTrue("expected \"$text\" shown but not tappable:\n${screen()}", shows(text) && !canTap(text))

    /** Every text, spoken name and window title on the screen, top to bottom, for a failure message. */
    fun screen(): String = compose.onAllNodes(isRoot(), useUnmergedTree = true).printToString(maxDepth = Int.MAX_VALUE)
        .lines().map { it.trimStart(' ', '|', '-').trim() }
        .filter { it.startsWith("Text = ") || it.startsWith("ContentDescription = ") || it.startsWith("PaneTitle = ") || it == "[Disabled]" }
        .joinToString("\n")

    /**
     * Saves what the phone shows now, every window (a sheet over Home included), as [name].png where an AIL derived
     * check is told to (AIL_SHOTS_DIR, passed on by scripts/ail/check.sh). End-to-end checks don't call it.
     */
    fun saveScreen(name: String) {
        val dir = (System.getProperty("ail.shots") ?: System.getenv("AIL_SHOTS_DIR"))?.takeIf { it.isNotBlank() } ?: return
        File(dir).mkdirs()
        compose.waitForIdle()
        captureScreenRoboImage(File(dir, "$name.png").path)
    }

    private fun format(time: LocalTime): String {
        val is24 = DateFormat.is24HourFormat(ApplicationProvider.getApplicationContext<Application>())
        return time.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a", Locale.US))
    }

    private fun named(text: String): SemanticsMatcher =
        hasText(text) or hasContentDescription(text) or SemanticsMatcher("window titled $text") {
            it.config.getOrNull(SemanticsProperties.PaneTitle) == text
        }

    private fun all(text: String) = compose.onAllNodes(named(text), useUnmergedTree = true).fetchSemanticsNodes()

    /** The tappable node that reads [text]: the button itself when the text sits inside one. */
    private fun button(text: String): SemanticsNodeInteraction {
        val clickable = compose.onAllNodes(named(text) and hasClickAction())
        return if (clickable.fetchSemanticsNodes().isNotEmpty()) clickable[0] else compose.onAllNodes(named(text), useUnmergedTree = true)[0]
    }
}
