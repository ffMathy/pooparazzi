package com.ffmathy.xaternity.phone.widget

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
import androidx.glance.appwidget.action.actionStartActivity
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
import com.ffmathy.xaternity.core.ui.icons.XDrawables
import com.ffmathy.xaternity.phone.R
import com.ffmathy.xaternity.phone.launch.DeepLinks
import com.ffmathy.xaternity.phone.launch.HomeLaunch
import com.ffmathy.xaternity.phone.system.inAppLanguage
import com.ffmathy.xaternity.core.R as CoreR

/**
 * The 2×1 home-screen widget (P5, DESIGN_SPEC §3.3.6): Feed and the sleep toggle. Feed opens the app's Feed sheet
 * (End feed, while a breastfeed runs, opens the Side sheet); the toggle logs in place.
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

/** 2×1 breakpoints (DESIGN_SPEC §10.11): "Sleep" under 160 dp, icon-only buttons under 120 dp. */
internal data class QuickLayout(val labels: Boolean, val shortSleepLabel: Boolean, val narrowFeed: Boolean) {
    companion object {
        val Sizes: Set<DpSize> = setOf(DpSize(90.dp, 60.dp), DpSize(120.dp, 60.dp), DpSize(160.dp, 60.dp))

        fun of(size: DpSize): QuickLayout = QuickLayout(
            labels = size.width >= 120.dp,
            shortSleepLabel = size.width < 160.dp,
            narrowFeed = size.width >= 160.dp,
        )
    }
}

/** The 2×1 widget for [snapshot], laid out for the current [LocalSize]. */
@Composable
internal fun QuickWidgetContent(snapshot: WidgetSnapshot) {
    val context = LocalContext.current
    val strings = remember(context, snapshot.language, snapshot.zone) { WidgetStrings(context.inAppLanguage(snapshot.language), snapshot.zone) }
    val layout = QuickLayout.of(LocalSize.current)
    val measure = remember(context) { WidgetMeasure(context) }
    val colors = WidgetColors(snapshot.night)
    WidgetSurface(colors, snapshot.backgroundLaunch, GlanceModifier.padding(12.dp)) {
        Row(GlanceModifier.fillMaxSize()) {
            // The mockup's Feed is the narrower button (52 of 147 dp), widened to its label ("Måltid") so the word
            // never breaks; End feed needs the room of an equal half.
            val feedLabel = strings.string(CoreR.string.kind_feed)
            val feedWidth = if (layout.narrowFeed && !snapshot.feeding) {
                GlanceModifier.width(maxOf(52.dp, measure.width(feedLabel, QuickLabelSize) + 16.dp))
            } else {
                GlanceModifier.defaultWeight()
            }
            val feed = colors.feed
            if (snapshot.feeding) {
                WidgetButton(
                    modifier = feedWidth.fillMaxHeight(),
                    fill = feed.containerLit,
                    border = feed.strong,
                    radius = 20.dp,
                    action = actionStartActivity(DeepLinks.intent(context, HomeLaunch.Side)),
                    description = strings.string(R.string.cd_end_feed_choose_side),
                ) {
                    QuickFace(XDrawables.breast, feed.strong, strings.string(CoreR.string.action_end_feed), feed.onContainer, layout.labels)
                }
            } else {
                WidgetButton(
                    modifier = feedWidth.fillMaxHeight(),
                    fill = feed.container,
                    radius = 20.dp,
                    action = actionStartActivity(DeepLinks.intent(context, HomeLaunch.Feed)),
                    description = strings.string(R.string.widget_cd_log_feed),
                ) {
                    QuickFace(XDrawables.feed, feed.strong, feedLabel, feed.onContainer, layout.labels, labelPadding = 4.dp)
                }
            }
            Spacer(GlanceModifier.width(8.dp))
            val sleep = colors.sleep
            val asleep = snapshot.asleep
            val label = when {
                asleep -> strings.string(CoreR.string.action_woke_up)
                layout.shortSleepLabel -> strings.string(CoreR.string.kind_sleep)
                else -> strings.string(CoreR.string.action_start_sleep)
            }
            WidgetButton(
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                fill = if (asleep) sleep.containerLit else sleep.container,
                border = if (asleep) sleep.strong else null,
                radius = 20.dp,
                action = actionRunCallback<ToggleSleepAction>(),
                description = strings.string(if (asleep) R.string.cd_woke_up_end_sleep else CoreR.string.action_start_sleep),
            ) {
                QuickFace(if (asleep) XDrawables.wake else XDrawables.sleep, sleep.strong, label, sleep.onContainer, layout.labels)
            }
        }
    }
}

private val QuickLabelSize = 14.sp

/** Icon 24 over a 14 sp label of up to two lines ("Start" / "sleep" at large font scales), padding 0 8. */
@Composable
private fun QuickFace(icon: Int, iconColor: Color, label: String, labelColor: Color, showLabel: Boolean, labelPadding: Dp = 8.dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        WidgetIcon(icon, iconColor, 24.dp)
        if (showLabel) {
            Spacer(GlanceModifier.height(6.dp))
            WidgetText(label, labelColor, QuickLabelSize, maxLines = 2, align = TextAlign.Center, modifier = GlanceModifier.padding(horizontal = labelPadding))
        }
    }
}
