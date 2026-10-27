package com.ffmathy.xaternity.phone.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.TextAlign
import com.ffmathy.xaternity.core.model.DiaperKind
import com.ffmathy.xaternity.core.model.FeedType
import com.ffmathy.xaternity.core.ui.icons.XDrawables
import com.ffmathy.xaternity.phone.R
import com.ffmathy.xaternity.phone.launch.DeepLinks
import com.ffmathy.xaternity.phone.launch.HomeLaunch
import com.ffmathy.xaternity.phone.system.inAppLanguage
import com.ffmathy.xaternity.core.R as CoreR

/**
 * The 4×2 home-screen widget (P5, DESIGN_SPEC §3.3.6 with V1_ADDITIONS §2.1): a status row (the latest feed and diaper
 * as absolute times, or the receipt of the last widget log with Undo), Feed · Pee · Poop · Both, and the sleep toggle.
 *
 * Pee, Poop, Both and the toggle log in place without opening the app. Feed opens the app's Feed sheet; while a
 * breastfeed runs it is "End feed", which opens the Side sheet.
 */
class LogWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(LogLayout.Sizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val source = WidgetSource.from(context)
        val initial = source.current()
        val snapshots = source.snapshots()
        provideContent {
            val snapshot by snapshots.collectAsState(initial)
            LogWidgetContent(snapshot)
        }
    }

    /** The generated picker preview (Glance 1.2, Android 15+): the reference scenario at 14:32. */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { LogWidgetContent(WidgetSamples.idle(night = true)) }
    }
}

/**
 * Which parts of the 4×2 widget fit at a size (DESIGN_SPEC §10.11 breakpoints). [width] is the size the widget is
 * composed for (Glance's responsive bucket, never wider than the real widget), so text measured against it fits.
 */
internal data class LogLayout(val statusRow: Boolean, val labels: Boolean, val wide: Boolean, val width: Dp) {
    /** Row 1's width inside the 12 dp padding. */
    val rowWidth: Dp get() = width - 24.dp

    /** One of the four log buttons (gaps 8). */
    val buttonWidth: Dp get() = (width - 24.dp - 24.dp) / 4

    companion object {
        val Sizes: Set<DpSize> = setOf(
            DpSize(120.dp, 100.dp),
            DpSize(240.dp, 130.dp),
            DpSize(240.dp, 180.dp),
            DpSize(320.dp, 130.dp),
            DpSize(320.dp, 180.dp),
        )

        /**
         * Row 1 needs 180 dp of height (below it the widget keeps only the buttons, like a 2×2); labels need 240 dp
         * of width; under 320 dp, or from 1.3× font scale, row 3 drops its end-aligned status and row 1 shows one fact.
         */
        fun of(size: DpSize, fontScale: Float): LogLayout {
            val statusRow = size.height >= 180.dp
            return LogLayout(
                statusRow = statusRow,
                labels = size.width >= 240.dp && size.height >= 130.dp,
                wide = size.width >= 320.dp && fontScale < 1.3f,
                width = size.width,
            )
        }
    }
}

/** The 4×2 widget for [snapshot], laid out for the current [LocalSize]. */
@Composable
internal fun LogWidgetContent(snapshot: WidgetSnapshot) {
    val context = LocalContext.current
    val strings = remember(context, snapshot.language, snapshot.zone) { WidgetStrings(context.inAppLanguage(snapshot.language), snapshot.zone) }
    val layout = LogLayout.of(LocalSize.current, context.resources.configuration.fontScale)
    val measure = remember(context) { WidgetMeasure(context) }
    val colors = WidgetColors(snapshot.night)
    WidgetSurface(colors, snapshot.backgroundLaunch) {
        Column(
            GlanceModifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = if (layout.statusRow) 0.dp else 12.dp),
        ) {
            if (layout.statusRow) {
                // 48 dp band: the 24 dp row centred 12 dp from the top, so Undo gets a 48 dp target (DESIGN_SPEC §9.3).
                StatusRow(snapshot, strings, colors, layout, measure, GlanceModifier.fillMaxWidth().height(48.dp))
                Spacer(GlanceModifier.height(4.dp))
            }
            LogButtons(snapshot, strings, colors, layout, measure, GlanceModifier.fillMaxWidth().defaultWeight())
            Spacer(GlanceModifier.height(8.dp))
            SleepToggle(snapshot, strings, colors, layout, GlanceModifier.fillMaxWidth().defaultWeight())
        }
    }
}

@Composable
private fun StatusRow(
    snapshot: WidgetSnapshot,
    strings: WidgetStrings,
    colors: WidgetColors,
    layout: LogLayout,
    measure: WidgetMeasure,
    modifier: GlanceModifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val notice = snapshot.notice
        val receipt = snapshot.receipt
        when {
            notice != null -> NoticeLine(notice, strings, colors)
            receipt != null -> ReceiptLine(receipt, strings, colors, layout, measure)
            else -> StatusFacts(snapshot, strings, colors, layout, measure)
        }
    }
}

/**
 * Idle and asleep: "Fed at 12:22" and "Pee at 13:44" (the diaper only when both fit); feeding: "Breastfeeding since
 * 14:32" ("Since 14:32" when that does not fit).
 */
@Composable
private fun RowScope.StatusFacts(snapshot: WidgetSnapshot, strings: WidgetStrings, colors: WidgetColors, layout: LogLayout, measure: WidgetMeasure) {
    fun factWidth(text: String) = FactIconWidth + measure.width(text, FactSize)
    val feedStart = snapshot.runningFeedStart
    val sleepStart = snapshot.runningSleepStart
    val lastFeed = snapshot.lastFeed
    when {
        feedStart != null -> Fact(
            icon = XDrawables.breast,
            color = colors.feed.strong,
            text = strings.breastfeedingSince(feedStart).takeIf { factWidth(it) <= layout.rowWidth } ?: strings.sinceOnly(feedStart),
            colors = colors,
        )
        // Narrow: the toggle row has no room for "Asleep since 02:05", so it moves up here (DESIGN_SPEC §10.11).
        sleepStart != null && !layout.wide -> Fact(XDrawables.sleepRunning, colors.sleep.strong, strings.asleepSince(sleepStart), colors)
        else -> {
            val feedText = lastFeed?.let { strings.fedAt(it.startMillis) } ?: strings.noFeeds()
            Fact(XDrawables.forFeedType(lastFeed?.feedType ?: FeedType.FORMULA), colors.feed.strong, feedText, colors)
            val diaper = snapshot.lastDiaper
            val kind = diaper?.let { DiaperKind.of(it.pee, it.poop) }
            if (diaper != null && kind != null) {
                val diaperText = strings.diaperAt(kind, diaper.startMillis)
                if (factWidth(feedText) + FactGap + factWidth(diaperText) <= layout.rowWidth) {
                    Spacer(GlanceModifier.width(FactGap))
                    val icon = if (kind == DiaperKind.BOTH) colors.bothIcon else XDrawables.forDiaperKind(kind)
                    Fact(icon, if (kind == DiaperKind.BOTH) null else colors.diaper(kind).strong, diaperText, colors)
                }
            }
        }
    }
}

@Composable
private fun Fact(icon: Int, color: Color?, text: String, colors: WidgetColors) {
    WidgetIcon(icon, color, 16.dp)
    Spacer(GlanceModifier.width(6.dp))
    WidgetText(text, colors.onSurface, FactSize)
}

private val FactSize = 15.sp
private val FactIconWidth = 22.dp
private val FactGap = 16.dp

/**
 * "Poop logged at 14:32" with Undo at the end (48 dp target, never where a log button is). Where the line does not fit
 * beside Undo ("Tis + bæ registreret kl. 14:32") it drops the verb: the check mark already says it was logged.
 */
@Composable
private fun RowScope.ReceiptLine(receipt: WidgetReceipt, strings: WidgetStrings, colors: WidgetColors, layout: LogLayout, measure: WidgetMeasure) {
    val undo = strings.string(CoreR.string.action_undo)
    val available = layout.rowWidth - FactIconWidth - (12.dp + 22.dp + measure.width(undo, FactSize) + 12.dp)
    val text = strings.receipt(receipt).takeIf { measure.fits(it, FactSize, available) } ?: strings.receiptShort(receipt)
    WidgetIcon(XDrawables.check, colors.onSurfaceVariant, 16.dp)
    Spacer(GlanceModifier.width(6.dp))
    WidgetText(text, colors.onSurface, FactSize, GlanceModifier.defaultWeight())
    Row(
        GlanceModifier
            .fillMaxHeight()
            .cornerRadius(24.dp)
            .padding(horizontal = 12.dp)
            .clickableUndo(receipt, strings.undoDescription(receipt)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetIcon(XDrawables.undo, colors.primary, 16.dp)
        Spacer(GlanceModifier.width(6.dp))
        WidgetText(undo, colors.primary, FactSize)
    }
}

private fun GlanceModifier.clickableUndo(receipt: WidgetReceipt, description: String): GlanceModifier =
    clickable(actionRunCallback<UndoWidgetLogAction>(actionParametersOf(UndoWidgetLogAction.EntryId to receipt.entryId)))
        .semantics { contentDescription = description }

/** The 2 s guard, dedupe and "Undone" lines (DESIGN_SPEC §10.7). */
@Composable
private fun NoticeLine(notice: WidgetNotice, strings: WidgetStrings, colors: WidgetColors) {
    val kind = notice.diaperKind
    val (icon, iconColor, textColor) = when (notice.type) {
        NoticeType.ALREADY_ASLEEP -> Triple(XDrawables.sleep, colors.sleep.strong, colors.sleep.text)
        NoticeType.ALREADY_AWAKE -> Triple(XDrawables.awake, colors.sleep.strong, colors.sleep.text)
        NoticeType.ALREADY_LOGGED -> when (kind) {
            DiaperKind.BOTH -> Triple(colors.bothIcon, null, colors.onSurfaceVariant)
            null -> Triple(XDrawables.diaper, colors.pee.strong, colors.onSurfaceVariant)
            else -> Triple(XDrawables.forDiaperKind(kind), colors.diaper(kind).strong, colors.onSurfaceVariant)
        }
        NoticeType.UNDONE -> Triple(XDrawables.undo, colors.onSurfaceVariant, colors.onSurface)
    }
    WidgetIcon(icon, iconColor, 16.dp)
    Spacer(GlanceModifier.width(6.dp))
    WidgetText(strings.notice(notice), textColor, 15.sp)
}

/** Feed (or End feed while a breastfeed runs), Pee, Poop and Both: four equal buttons, gap 8, r 20. */
@Composable
private fun LogButtons(
    snapshot: WidgetSnapshot,
    strings: WidgetStrings,
    colors: WidgetColors,
    layout: LogLayout,
    measure: WidgetMeasure,
    modifier: GlanceModifier,
) {
    val context = LocalContext.current
    val feedLabel = strings.string(if (snapshot.feeding) CoreR.string.action_end_feed else CoreR.string.kind_feed)
    val diaperLabels = DiaperKind.entries.associateWith { strings.string(diaperLabel(it)) }
    // One face for the whole row: when any label needs two lines ("Afslut amning", or a narrow widget) every button
    // uses the compact face, so the icons and labels stay aligned and both lines fit the 68 dp button.
    val style = when {
        !layout.labels -> FaceStyle.IconOnly
        measure.fits(feedLabel, LabelSize, layout.buttonWidth - 8.dp - (if (snapshot.feeding) 4.dp else 0.dp)) &&
            diaperLabels.values.all { measure.fits(it, LabelSize, layout.buttonWidth - 8.dp) } -> FaceStyle.Regular
        else -> FaceStyle.Compact
    }
    Row(modifier) {
        val cell = GlanceModifier.defaultWeight().fillMaxHeight()
        if (snapshot.feeding) {
            WidgetButton(
                modifier = cell,
                fill = colors.feed.containerLit,
                border = colors.feed.strong,
                radius = 20.dp,
                action = actionStartActivity(DeepLinks.intent(context, HomeLaunch.Side)),
                description = strings.string(R.string.cd_end_feed_choose_side),
            ) {
                ButtonFace(XDrawables.breast, colors.feed.strong, feedLabel, colors.feed.onContainer, style)
            }
        } else {
            WidgetButton(
                modifier = cell,
                fill = colors.feed.container,
                radius = 20.dp,
                action = actionStartActivity(DeepLinks.intent(context, HomeLaunch.Feed)),
                description = strings.string(R.string.widget_cd_log_feed),
            ) {
                ButtonFace(XDrawables.feed, colors.feed.strong, feedLabel, colors.feed.onContainer, style)
            }
        }
        Spacer(GlanceModifier.width(8.dp))
        DiaperButton(DiaperKind.PEE, diaperLabels.getValue(DiaperKind.PEE), strings, colors, cell, style)
        Spacer(GlanceModifier.width(8.dp))
        DiaperButton(DiaperKind.POOP, diaperLabels.getValue(DiaperKind.POOP), strings, colors, cell, style)
        Spacer(GlanceModifier.width(8.dp))
        DiaperButton(DiaperKind.BOTH, diaperLabels.getValue(DiaperKind.BOTH), strings, colors, cell, style)
    }
}

@Composable
private fun DiaperButton(
    kind: DiaperKind,
    label: String,
    strings: WidgetStrings,
    colors: WidgetColors,
    modifier: GlanceModifier,
    style: FaceStyle,
) {
    val action = actionRunCallback<LogDiaperAction>(actionParametersOf(LogDiaperAction.Kind to kind.name))
    val description = strings.string(
        when (kind) {
            DiaperKind.PEE -> R.string.widget_cd_log_pee
            DiaperKind.POOP -> R.string.widget_cd_log_poop
            DiaperKind.BOTH -> R.string.widget_cd_log_both
        },
    )
    if (kind == DiaperKind.BOTH) {
        // Both is a 50/50 split of the PEE and POOP containers with a two-tone glyph and a POOP onContainer label.
        Box(modifier.cornerRadius(20.dp).clickable(action).semantics { contentDescription = description }) {
            Row(GlanceModifier.fillMaxSize()) {
                Box(GlanceModifier.defaultWeight().fillMaxHeight().background(colors.pee.container.provider())) {}
                Box(GlanceModifier.defaultWeight().fillMaxHeight().background(colors.poop.container.provider())) {}
            }
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ButtonFace(colors.bothIcon, null, label, colors.poop.onContainer, style)
            }
        }
    } else {
        val event = colors.diaper(kind)
        WidgetButton(modifier = modifier, fill = event.container, radius = 20.dp, action = action, description = description) {
            ButtonFace(XDrawables.forDiaperKind(kind), event.strong, label, event.onContainer, style)
        }
    }
}

/** The button labels: "Pee", "Poop" and the short status word "Both" (the 77 dp buttons have no room for "Pee + poop"). */
private fun diaperLabel(kind: DiaperKind): Int = when (kind) {
    DiaperKind.PEE -> CoreR.string.diaper_pee
    DiaperKind.POOP -> CoreR.string.diaper_poop
    DiaperKind.BOTH -> CoreR.string.diaper_both
}

/** How a log button shows its label: icon 24 over 15 sp, the compact two-line face, or the icon alone (small widget). */
internal enum class FaceStyle { Regular, Compact, IconOnly }

private val LabelSize = 15.sp

/** Icon over label, centred: 24 dp + 6 + 15 sp, or 20 dp + 2 + two lines of 13 sp ("Afslut" / "amning"). */
@Composable
private fun ButtonFace(icon: Int, iconColor: Color?, label: String, labelColor: Color, style: FaceStyle) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        WidgetIcon(icon, iconColor, if (style == FaceStyle.Compact) 20.dp else 24.dp)
        if (style != FaceStyle.IconOnly) {
            Spacer(GlanceModifier.height(if (style == FaceStyle.Compact) 2.dp else 6.dp))
            WidgetText(
                text = label,
                color = labelColor,
                size = if (style == FaceStyle.Compact) 13.sp else LabelSize,
                maxLines = 2,
                align = TextAlign.Center,
                modifier = GlanceModifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/**
 * The sleep toggle: "Start sleep" with "Awake since 13:27" at the end, or the checked "Woke up" (r 20, lit fill, 2 dp
 * strong border) with "Asleep since 02:05". The end text wraps rather than clips, and is dropped when narrow.
 */
@Composable
private fun SleepToggle(snapshot: WidgetSnapshot, strings: WidgetStrings, colors: WidgetColors, layout: LogLayout, modifier: GlanceModifier) {
    val sleep = colors.sleep
    val sleepStart = snapshot.runningSleepStart
    val awakeSince = snapshot.awakeSinceMillis
    val label = strings.string(if (sleepStart != null) CoreR.string.action_woke_up else CoreR.string.action_start_sleep)
    val status = when {
        sleepStart != null -> strings.asleepSince(sleepStart)
        awakeSince != null -> strings.awakeSince(awakeSince)
        else -> null
    }
    val name = strings.string(if (sleepStart != null) R.string.cd_woke_up_end_sleep else CoreR.string.action_start_sleep)
    WidgetButton(
        modifier = modifier,
        fill = if (sleepStart != null) sleep.containerLit else sleep.container,
        border = if (sleepStart != null) sleep.strong else null,
        radius = if (sleepStart != null) 20.dp else 34.dp,
        action = actionRunCallback<ToggleSleepAction>(),
        description = status?.let { strings.string(R.string.spoken_pair, name, it) } ?: name,
    ) {
        val icon = if (sleepStart != null) XDrawables.wake else XDrawables.sleep
        if (!layout.labels) {
            WidgetIcon(icon, sleep.strong, 24.dp)
        } else {
            SleepToggleRow(icon, label, status, sleepStart != null, layout, colors)
        }
    }
}

@Composable
private fun SleepToggleRow(icon: Int, label: String, status: String?, asleep: Boolean, layout: LogLayout, colors: WidgetColors) {
    val sleep = colors.sleep
    Row(
        // Inside the 2 dp border the content keeps its 20 dp inset.
        GlanceModifier.fillMaxSize().padding(horizontal = if (asleep) 18.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetIcon(icon, sleep.strong, 24.dp)
        Spacer(GlanceModifier.width(10.dp))
        WidgetText(label, sleep.onContainer, 16.sp)
        if (layout.wide && status != null) {
            Spacer(GlanceModifier.width(12.dp))
            Spacer(GlanceModifier.defaultWeight())
            if (asleep) {
                WidgetIcon(XDrawables.sleepRunning, sleep.strong, 16.dp)
                Spacer(GlanceModifier.width(6.dp))
            }
            WidgetText(status, sleep.onContainer, 14.sp, maxLines = 2, align = TextAlign.End)
        }
    }
}
