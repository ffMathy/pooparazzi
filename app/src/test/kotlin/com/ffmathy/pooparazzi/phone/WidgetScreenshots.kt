package com.ffmathy.pooparazzi.phone

import android.app.Application
import android.content.Context
import android.provider.Settings
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import androidx.test.core.app.ApplicationProvider
import com.ffmathy.pooparazzi.phone.widget.LogWidgetContent
import com.ffmathy.pooparazzi.phone.widget.QuickWidgetContent
import com.ffmathy.pooparazzi.phone.widget.WidgetSamples
import com.ffmathy.pooparazzi.phone.widget.WidgetSnapshot
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The real Glance widgets, rendered the way a launcher shows them: each widget is composed by Glance into
 * [RemoteViews] at a fixed size, applied to a view and captured (Robolectric native graphics). Each state sits on a
 * panel in its theme's wallpaper tone, as in the mockup. Written to `app/screenshots/<lang>/17-widget-4x2.png` and
 * `18-widget-3x1.png`, `19-widget-cover.png` and
 * `27-widget-twins.png`. The canvas is the phone's width but taller (960 dp), to hold four 4×2 states.
 */
abstract class WidgetScreenshots(private val language: String) {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun use24HourClock() {
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
    }

    /** Idle (Night), just logged (Day), asleep at 03:12 (Night under Auto) and after a breast feed (Day). */
    @Test
    fun widget4x2() {
        val size = DpSize(358.dp, 208.dp)
        val states = listOf(
            WidgetSamples.idle(night = true),
            WidgetSamples.justLogged(night = false),
            WidgetSamples.asleep(),
            WidgetSamples.afterBreastfeed(night = false),
        )
        val views = states.map { render(size) { LogWidgetContent(it) } }
        compose.setContent {
            Column(Modifier.testTag(TAG)) {
                states.zip(views).forEach { (state, view) -> Panel(state, size, view) }
            }
        }
        compose.onNodeWithTag(TAG).captureRoboImage("screenshots/$language/17-widget-4x2.png")
    }

    /**
     * Idle and asleep (Night), after a breast feed and idle (Day) at the reference 260 dp, then the narrow sizes: the
     * 160 dp minimum and an old 2×1 at 110 dp (compact spacing, icon-only).
     */
    @Test
    fun widget3x1() {
        val full = DpSize(260.dp, 100.dp)
        val rows = listOf(
            listOf(WidgetSamples.idle(night = true) to full),
            listOf(WidgetSamples.asleep() to full),
            listOf(WidgetSamples.afterBreastfeed(night = false) to full),
            listOf(WidgetSamples.idle(night = false) to full),
            listOf(WidgetSamples.idle(night = true) to DpSize(160.dp, 100.dp), WidgetSamples.asleep() to DpSize(110.dp, 100.dp)),
        )
        val views = rows.map { row -> row.map { (state, size) -> render(size) { QuickWidgetContent(state) } } }
        compose.setContent {
            Column(Modifier.testTag(TAG).background(NightBackdrop)) {
                rows.zip(views).forEach { (row, rendered) ->
                    Row { row.zip(rendered).forEach { (cell, view) -> Panel(cell.first, cell.second, view, padding = 8.dp) } }
                }
            }
        }
        compose.onNodeWithTag(TAG).captureRoboImage("screenshots/$language/18-widget-3x1.png")
    }

    /** The 4×2 on a Samsung flip's cover screen (Flex Window, 352 × 339 dp): idle (Night) and asleep. */
    @Test
    fun widgetCoverScreen() {
        val size = DpSize(352.dp, 339.dp)
        val states = listOf(WidgetSamples.idle(night = true), WidgetSamples.asleep())
        val views = states.map { render(size) { LogWidgetContent(it) } }
        compose.setContent {
            Column(Modifier.testTag(TAG)) {
                states.zip(views).forEach { (state, view) -> Panel(state, size, view) }
            }
        }
        compose.onNodeWithTag(TAG).captureRoboImage("screenshots/$language/19-widget-cover.png")
    }

    /** Not part of the tour: the 4×2 at 1.3× font scale (end text moves, labels wrap) and a narrow 276 dp widget. */
    @Test
    fun widgetBreakpoints() {
        RuntimeEnvironment.setFontScale(1.3f)
        val wide = DpSize(358.dp, 208.dp)
        val narrow = DpSize(276.dp, 190.dp)
        val asleep = WidgetSamples.asleep()
        val idle = WidgetSamples.idle(night = false)
        val views = listOf(render(wide) { LogWidgetContent(asleep) }, render(narrow) { LogWidgetContent(idle) }, render(narrow) { LogWidgetContent(asleep) })
        compose.setContent {
            Column(Modifier.testTag(TAG)) {
                Panel(asleep, wide, views[0])
                Panel(idle, narrow, views[1])
                Panel(asleep, narrow, views[2])
            }
        }
        compose.onNodeWithTag(TAG).captureRoboImage("build/outputs/roborazzi/widget-breakpoints-$language.png")
    }

    /** Twins: the 4×2 status row starting with "Noa" (Night and Day) and the 3×1. */
    @Test
    fun widgetTwins() {
        val size = DpSize(358.dp, 208.dp)
        val states = listOf(WidgetSamples.twins(night = true), WidgetSamples.twins(night = false))
        val views = states.map { render(size) { LogWidgetContent(it) } }
        val quick = render(DpSize(260.dp, 100.dp)) { QuickWidgetContent(states.first()) }
        compose.setContent {
            // The night backdrop fills the canvas beside the narrower 3×1, so no blank strip shows.
            Column(Modifier.testTag(TAG).background(NightBackdrop)) {
                states.zip(views).forEach { (state, view) -> Panel(state, size, view) }
                Panel(states.first(), DpSize(260.dp, 100.dp), quick)
            }
        }
        compose.onNodeWithTag(TAG).captureRoboImage("screenshots/$language/27-widget-twins.png")
    }

    /**
     * Not part of the tour: the static picker previews (`previewLayout`, Android 12–14) inflated as RemoteViews, as a
     * launcher does, so a view RemoteViews does not allow fails here. Night configuration (values-night palette).
     */
    @Test
    @Config(qualifiers = "+night")
    fun staticPickerPreviews() {
        val log = RemoteViews(context.packageName, R.layout.widget_log_preview)
        val quick = RemoteViews(context.packageName, R.layout.widget_quick_preview)
        compose.setContent {
            Column(Modifier.testTag(TAG).background(NightBackdrop).padding(16.dp)) {
                AndroidView(factory = { log.apply(it, FrameLayout(it)) }, modifier = Modifier.size(358.dp, 208.dp))
                Box(Modifier.size(16.dp))
                AndroidView(factory = { quick.apply(it, FrameLayout(it)) }, modifier = Modifier.size(260.dp, 100.dp))
            }
        }
        compose.onNodeWithTag(TAG).captureRoboImage("build/outputs/roborazzi/widget-static-previews-$language.png")
    }

    /** One widget on a panel of its theme's wallpaper tone (mockup backdrops). */
    @Composable
    private fun Panel(state: WidgetSnapshot, size: DpSize, view: RemoteViews, padding: androidx.compose.ui.unit.Dp = 16.dp) {
        Box(Modifier.background(if (state.night) NightBackdrop else DayBackdrop).padding(padding)) {
            AndroidView(factory = { view.apply(it, FrameLayout(it)) }, modifier = Modifier.size(size.width, size.height))
        }
    }

    private fun render(size: DpSize, content: @Composable () -> Unit): RemoteViews = runBlocking {
        val widget = object : GlanceAppWidget() {
            override val sizeMode: SizeMode = SizeMode.Exact

            override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent { content() }
        }
        widget.compose(context, size = size)
    }

    private companion object {
        const val TAG = "widgets"
        val NightBackdrop = Color(0xFF24201C)
        val DayBackdrop = Color(0xFFDCD5CB)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w390dp-h960dp-xxhdpi", application = Application::class)
class WidgetScreenshotsEnglish : WidgetScreenshots("en")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "da-w390dp-h960dp-xxhdpi", application = Application::class)
class WidgetScreenshotsDanish : WidgetScreenshots("da")
