package com.ffmathy.pooparazzi.phone

import android.app.Application
import android.content.Context
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.phone.widget.LogWidgetContent
import com.ffmathy.pooparazzi.phone.widget.QuickWidgetContent
import com.ffmathy.pooparazzi.phone.widget.WidgetSamples
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The widgets with two or more babies (BABIES_AND_BACKUP §2.4): the 4×2 status row starts with the current baby's
 * label, and both widgets name the baby to TalkBack; with one baby they read as before. Native graphics, so the text
 * the widgets measure has its real width.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi", application = Application::class)
class WidgetBabiesTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun use24HourClock() {
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
    }

    private fun render(size: DpSize, content: @Composable () -> Unit): View = runBlocking {
        val widget = object : GlanceAppWidget() {
            override val sizeMode: SizeMode = SizeMode.Exact

            override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent { content() }
        }
        widget.compose(context, size = size).apply(context, FrameLayout(context))
    }

    private fun View.all(): List<View> = listOf(this) + ((this as? ViewGroup)?.let { g -> (0 until g.childCount).flatMap { g.getChildAt(it).all() } } ?: emptyList())

    // Non-breaking spaces (kept inside "2h 10m") read as plain ones here.
    private fun View.texts(): List<String> = all().filterIsInstance<TextView>().map { it.text.toString().replace("\u00A0", " ") }.filter { it.isNotEmpty() }

    private fun View.descriptions(): List<String> = all().mapNotNull { it.contentDescription?.toString() }

    @Test
    fun theStatusRowStartsWithTheBabysLabel() {
        val twins = render(DpSize(358.dp, 208.dp)) { LogWidgetContent(WidgetSamples.twins(night = true)) }
        val texts = twins.texts()
        assertEquals("the row starts with the name", "Noa", texts.first())
        // The times are in their buttons, as on the watch: never repeated in the row above.
        assertEquals("Feed and its time since: $texts", listOf("Feed", "2h 10m"), texts.subList(1, 3))

        val one = render(DpSize(358.dp, 208.dp)) { LogWidgetContent(WidgetSamples.idle(night = true)) }
        assertEquals("one baby: the row names the baby, as the watch's title", "Noa", one.texts().first())
    }

    @Test
    fun aNarrowWidgetKeepsTheTimesInTheirButtons() {
        val narrow = render(DpSize(250.dp, 190.dp)) { LogWidgetContent(WidgetSamples.twins(night = false)) }
        val texts = narrow.texts()
        assertEquals("Noa", texts.first())
        // The sleep status stays in the sleep toggle, under its label, not up in the row.
        // Its `awake` icon says what "1h 05m" is, so the line is the time alone.
        assertTrue(texts.toString(), texts.indexOf("1h 05m") > texts.indexOf("Start sleep"))
        assertEquals(texts.toString(), 1, texts.count { it == "1h 05m" })
    }

    @Test
    fun bothWidgetsNameTheBabyToTalkBack() {
        val log = render(DpSize(358.dp, 208.dp)) { LogWidgetContent(WidgetSamples.twins(night = true)) }.descriptions()
        // Each button goes on with the time since the last one and what the week expects next (V1_ADDITIONS §9).
        assertTrue(log.toString(), "Log diaper for Noa, Last diaper 48 minutes ago" in log)
        assertTrue(log.toString(), "Log feed for Noa, Last feed 2 hours 10 minutes ago, next feed expected around 15:30" in log)
        assertTrue(log.toString(), log.any { it.startsWith("Start sleep for Noa") })

        val quick = render(DpSize(171.dp, 100.dp)) { QuickWidgetContent(WidgetSamples.twins(night = true)) }.descriptions()
        assertTrue(quick.toString(), "Log feed for Noa" in quick)
        assertTrue(quick.toString(), "Log diaper for Noa" in quick)
        assertTrue(quick.toString(), "Start sleep for Noa" in quick)

        val one = render(DpSize(171.dp, 100.dp)) { QuickWidgetContent(WidgetSamples.idle(night = true)) }.descriptions()
        assertTrue("one baby reads as before: $one", "Log feed" in one)
        assertTrue("one baby reads as before: $one", "Log diaper" in one)
    }
}
