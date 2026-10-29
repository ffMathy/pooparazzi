package com.ffmathy.pooparazzi.phone.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.TextAlign
import com.ffmathy.pooparazzi.core.ui.icons.XDrawables
import com.ffmathy.pooparazzi.phone.R
import com.ffmathy.pooparazzi.phone.launch.HomeLaunch
import com.ffmathy.pooparazzi.phone.system.inAppLanguage
import com.ffmathy.pooparazzi.core.R as CoreR

/**
 * The 3×1 home-screen widget (P5, DESIGN_SPEC §3.3.6): Feed, Diaper and the sleep toggle, like the 4×2 without its
 * status row. Feed and Diaper open the app's Feed and Diaper sheets; the toggle logs in place.
 *
 * It was a 2×1 (Feed and the toggle) before Diaper joined; widgets placed at that size keep working, with icon-only
 * buttons where the labels do not fit.
 */
class QuickWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(QuickLayout.Sizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val source = WidgetSource.from(context)
        val initial = source.current()
        val snapshots = source.snapshots()
        provideContent {
            val snapshot by snapshots.collectAsState(initial)
            QuickWidgetContent(snapshot)
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { QuickWidgetContent(WidgetSamples.idle(night = true)) }
    }
}

/**
 * Three equal buttons. [compact] tightens the padding and gaps (8 and 4 dp) for an old 2×1 so the three icons still
 * fit; [width] is the size the widget is composed for.
 */
internal data class QuickLayout(val width: Dp, val compact: Boolean) {
    val padding: Dp get() = if (compact) 8.dp else 12.dp
    val gap: Dp get() = if (compact) 4.dp else 8.dp

    /** One of the three buttons. */
    val buttonWidth: Dp get() = (width - padding * 2 - gap * 2) / 3

    companion object {
        val Sizes: Set<DpSize> = setOf(
            DpSize(110.dp, 60.dp),
            DpSize(180.dp, 60.dp),
            DpSize(220.dp, 60.dp),
            DpSize(260.dp, 60.dp),
        )

        fun of(size: DpSize): QuickLayout = QuickLayout(width = size.width, compact = size.width < 180.dp)
    }
}

/** The 3×1 widget for [snapshot], laid out for the current [LocalSize]. */
@Composable
internal fun QuickWidgetContent(snapshot: WidgetSnapshot) {
    val context = LocalContext.current
    val strings = remember(context, snapshot.language, snapshot.zone) { WidgetStrings(context.inAppLanguage(snapshot.language), snapshot.zone) }
    val layout = QuickLayout.of(LocalSize.current)
    val measure = remember(context) { WidgetMeasure(context) }
    val colors = WidgetColors(snapshot.night)
    val asleep = snapshot.asleep
    val feedLabel = strings.string(CoreR.string.kind_feed)
    val diaperLabel = strings.string(CoreR.string.kind_diaper)
    // Labels may wrap to two lines but never break inside a word.
    val room = layout.buttonWidth - QuickLabelPadding * 2
    fun fits(label: String) = label.split(' ').all { measure.fits(it, QuickLabelSize, room) }
    val sleepLabel = when {
        asleep -> strings.string(CoreR.string.action_woke_up)
        fits(strings.string(CoreR.string.action_start_sleep)) -> strings.string(CoreR.string.action_start_sleep)
        else -> strings.string(CoreR.string.kind_sleep)
    }
    // All three show their label, or none does (icons stay on one line).
    val labels = listOf(feedLabel, diaperLabel, sleepLabel).all(::fits)
    // With two or more babies TalkBack names the baby the buttons log for (BABIES_AND_BACKUP §2.4).
    val baby = strings.babyLabel(snapshot)
    WidgetSurface(colors, snapshot.backgroundLaunch, GlanceModifier.padding(layout.padding)) {
        Row(GlanceModifier.fillMaxSize()) {
            val cell = GlanceModifier.defaultWeight().fillMaxHeight()
            val feed = colors.feed
            SheetButton(HomeLaunch.Feed, feed, strings.forBaby(strings.string(R.string.widget_cd_log_feed), baby), cell) {
                QuickFace(XDrawables.feed, feed.strong, feedLabel, feed.onContainer, labels)
            }
            Spacer(GlanceModifier.width(layout.gap))
            val diaper = colors.diaper
            SheetButton(HomeLaunch.Diaper, diaper, strings.forBaby(strings.string(R.string.widget_cd_log_diaper), baby), cell) {
                QuickFace(XDrawables.diaper, diaper.strong, diaperLabel, diaper.onContainer, labels)
            }
            Spacer(GlanceModifier.width(layout.gap))
            val sleep = colors.sleep
            WidgetButton(
                modifier = cell,
                fill = if (asleep) sleep.containerLit else sleep.container,
                border = if (asleep) sleep.strong else null,
                radius = 20.dp,
                action = actionRunCallback<ToggleSleepAction>(),
                description = strings.forBaby(
                    strings.string(if (asleep) R.string.cd_woke_up_end_sleep else CoreR.string.action_start_sleep),
                    baby,
                ),
            ) {
                QuickFace(if (asleep) XDrawables.wake else XDrawables.sleep, sleep.strong, sleepLabel, sleep.onContainer, labels)
            }
        }
    }
}

private val QuickLabelSize = 14.sp
private val QuickLabelPadding = 4.dp

/** Icon 24 over a 14 sp label of up to two lines ("Start" / "sleep"). */
@Composable
private fun QuickFace(icon: Int, iconColor: Color, label: String, labelColor: Color, showLabel: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        WidgetIcon(icon, iconColor, 24.dp)
        if (showLabel) {
            Spacer(GlanceModifier.height(6.dp))
            WidgetText(label, labelColor, QuickLabelSize, maxLines = 2, align = TextAlign.Center, modifier = GlanceModifier.padding(horizontal = QuickLabelPadding))
        }
    }
}
