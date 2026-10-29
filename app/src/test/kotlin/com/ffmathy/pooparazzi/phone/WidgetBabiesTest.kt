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

    private fun View.texts(): List<String> = all().filterIsInstance<TextView>().map { it.text.toString() }.filter { it.isNotEmpty() }

    private fun View.descriptions(): List<String> = all().mapNotNull { it.contentDescription?.toString() }

    @Test
    fun theStatusRowStartsWithTheBabysLabel() {
        val twins = render(DpSize(358.dp, 208.dp)) { LogWidgetContent(WidgetSamples.twins(night = true)) }
        val texts = twins.texts()
        assertEquals("the row starts with the name", "Noa", texts.first())
        assertTrue("the feed still fits beside it: $texts", texts.any { it.startsWith("Fed at 12:22") })

        val one = render(DpSize(358.dp, 208.dp)) { LogWidgetContent(WidgetSamples.idle(night = true)) }
        assertFalse("one baby: no name in the row", "Noa" in one.texts())
    }

    @Test
    fun aNarrowWidgetDropsFactsBeforeTheName() {
        val narrow = render(DpSize(250.dp, 190.dp)) { LogWidgetContent(WidgetSamples.twins(night = false)) }
        val texts = narrow.texts()
        assertEquals("Noa", texts.first())
        assertFalse("no room for the diaper beside the name and the feed: $texts", texts.any { it.startsWith("Pee at") })
    }

    @Test
    fun bothWidgetsNameTheBabyToTalkBack() {
        val log = render(DpSize(358.dp, 208.dp)) { LogWidgetContent(WidgetSamples.twins(night = true)) }.descriptions()
        assertTrue(log.toString(), "Log diaper for Noa" in log)
        // The feed button goes on with what the week expects next (V1_ADDITIONS §9).
        assertTrue(log.toString(), log.any { it.startsWith("Log feed for Noa") })
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
