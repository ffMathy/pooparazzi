package com.ffmathy.pooparazzi.phone.widget

import androidx.annotation.StringRes
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
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
import com.ffmathy.pooparazzi.core.model.DiaperKind
import com.ffmathy.pooparazzi.core.ui.icons.XDrawables
import com.ffmathy.pooparazzi.phone.R
import com.ffmathy.pooparazzi.phone.launch.HomeLaunch
import com.ffmathy.pooparazzi.phone.system.inAppLanguage
import com.ffmathy.pooparazzi.core.R as CoreR

/**
 * The 4×2 home-screen widget (P5, DESIGN_SPEC §3.3.6 with V1_ADDITIONS §2.1): a status row (the latest feed and diaper
 * as absolute times, or the receipt of the last widget log with Undo), Feed · Diaper, and the sleep toggle.
 *
 * The toggle logs in place without opening the app. Feed and Diaper open the app's Feed and Diaper sheets, so the
 * details (feed type, breast order, pee/poop, colour, texture) are asked for exactly as in the app.
 *
 * The same content is also offered on the cover screen of Samsung's flip phones ([CoverWidget]).
 */
class LogWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(LogLayout.Sizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) = provideSnapshots(context) { LogWidgetContent(it) }

    /** The generated picker preview (Glance 1.2, Android 15+): the reference scenario at 14:32. */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { LogWidgetContent(WidgetSamples.idle(night = true)) }
    }
}

/**
 * The 4×2 widget's content on the cover screen (Flex Window) of Samsung's flip phones, at one fixed size: the provider's
 * 352 × 339 dp. With the per-size RemoteViews of [SizeMode.Responsive] the widget stayed on its loading layout on a
 * Flip's cover screen, so this widget sends a single one, which every host can apply.
 */
class CoverWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) = provideSnapshots(context) { LogWidgetContent(it) }

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

    /** One of the two log buttons (gap 8). */
    val buttonWidth: Dp get() = (width - 24.dp - 8.dp) / 2

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
        val baby = strings.babyLabel(snapshot)
        when {
            notice != null -> NoticeLine(notice, strings, colors)
            receipt != null -> ReceiptLine(receipt, baby, strings, colors, layout, measure)
            else -> StatusFacts(snapshot, baby, strings, colors, layout, measure)
        }
    }
}

/**
 * Idle and asleep: "Fed at 12:22" and "Pee at 13:44" (the diaper only when both fit). With two or more babies the row
 * starts with the [baby]'s label, and the facts that no longer fit beside it are dropped.
 */
@Composable
private fun RowScope.StatusFacts(
    snapshot: WidgetSnapshot,
    baby: String?,
    strings: WidgetStrings,
    colors: WidgetColors,
    layout: LogLayout,
    measure: WidgetMeasure,
) {
    fun factWidth(text: String) = FactIconWidth + measure.width(text, FactSize)
    val sleepStart = snapshot.runningSleepStart
    val lastFeed = snapshot.lastFeed
    var room = layout.rowWidth
    if (baby != null) {
        BabyLabel(baby, colors)
        room -= measure.width(baby, FactSize) + FactGap
        Spacer(GlanceModifier.width(FactGap))
    }
    when {
        // Narrow: the toggle row has no room for "Asleep since 02:05", so it moves up here (DESIGN_SPEC §10.11).
        sleepStart != null && !layout.wide -> {
            val text = strings.asleepSince(sleepStart)
            if (baby == null || factWidth(text) <= room) Fact(XDrawables.sleepRunning, colors.sleep.strong, text, colors)
        }
        else -> {
            val feedText = lastFeed?.let { strings.fedAt(it) } ?: strings.noFeeds()
            // Without a name the feed fact always shows (as before several babies); beside one only when it fits.
            val feedShown = baby == null || factWidth(feedText) <= room
            if (feedShown) {
                Fact(XDrawables.forFeedType(lastFeed?.feedType), colors.feed.strong, feedText, colors)
                room -= factWidth(feedText) + FactGap
            }
            val diaper = snapshot.lastDiaper
            val kind = diaper?.let { DiaperKind.of(it.pee, it.poop) }
            if (feedShown && diaper != null && kind != null) {
                val diaperText = strings.diaperAt(kind, diaper.startMillis)
                if (factWidth(diaperText) <= room) {
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

/** The current baby's label at the start of the status row (two or more babies), in the facts' type (as measured). */
@Composable
private fun BabyLabel(label: String, colors: WidgetColors) {
    WidgetText(label, colors.onSurface, FactSize)
}

private val FactSize = 15.sp
private val FactIconWidth = 22.dp
private val FactGap = 16.dp

/**
 * "Poop logged at 14:32" with Undo at the end (48 dp target, never where a log button is). Where the line does not fit
 * beside Undo ("Tis + bæ registreret kl. 14:32") it drops the verb: the check mark already says it was logged.
 */
@Composable
private fun RowScope.ReceiptLine(
    receipt: WidgetReceipt,
    baby: String?,
    strings: WidgetStrings,
    colors: WidgetColors,
    layout: LogLayout,
    measure: WidgetMeasure,
) {
    val undo = strings.string(CoreR.string.action_undo)
    val available = layout.rowWidth - FactIconWidth - (12.dp + 22.dp + measure.width(undo, FactSize) + 12.dp)
    // With two or more babies the line starts with the baby's label, where the label and the receipt (short) fit.
    val named = baby?.takeIf { measure.fits(strings.receiptShort(receipt), FactSize, available - measure.width(it, FactSize) - FactGap) }
    val room = if (named != null) available - measure.width(named, FactSize) - FactGap else available
    val text = strings.receipt(receipt).takeIf { measure.fits(it, FactSize, room) } ?: strings.receiptShort(receipt)
    if (named != null) {
        BabyLabel(named, colors)
        Spacer(GlanceModifier.width(FactGap))
    }
    WidgetIcon(XDrawables.check, colors.onSurfaceVariant, 16.dp)
    Spacer(GlanceModifier.width(6.dp))
    WidgetText(text, colors.onSurface, FactSize, GlanceModifier.defaultWeight())
    Row(
        GlanceModifier
            .fillMaxHeight()
            .cornerRadius(24.dp)
            .padding(horizontal = 12.dp)
            .clickableUndo(receipt, strings.forBaby(strings.undoDescription(receipt), baby)),
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

/** The 2 s guard and "Undone" lines (DESIGN_SPEC §10.7). */
@Composable
private fun NoticeLine(notice: WidgetNotice, strings: WidgetStrings, colors: WidgetColors) {
    val (icon, iconColor, textColor) = when (notice.type) {
        NoticeType.ALREADY_ASLEEP -> Triple(XDrawables.sleep, colors.sleep.strong, colors.sleep.text)
        NoticeType.ALREADY_AWAKE -> Triple(XDrawables.awake, colors.sleep.strong, colors.sleep.text)
        NoticeType.UNDONE -> Triple(XDrawables.undo, colors.onSurfaceVariant, colors.onSurface)
    }
    WidgetIcon(icon, iconColor, 16.dp)
    Spacer(GlanceModifier.width(6.dp))
    WidgetText(strings.notice(notice), textColor, 15.sp)
}

/**
 * Feed and Diaper: two equal buttons, gap 8, r 20. Each opens the app on its logging sheet (the same sheet as Home's
 * buttons), where the optional details are chosen: feed type and breast order, pee/poop, colour and texture.
 */
@Composable
private fun LogButtons(
    snapshot: WidgetSnapshot,
    strings: WidgetStrings,
    colors: WidgetColors,
    layout: LogLayout,
    measure: WidgetMeasure,
    modifier: GlanceModifier,
) {
    // With two or more babies TalkBack names the baby the sheets open for (BABIES_AND_BACKUP §2.4).
    val baby = strings.babyLabel(snapshot)
    // Both buttons share one face, so their icons stay aligned: a label that needs two lines compacts both.
    val feedLabel = strings.string(CoreR.string.kind_feed)
    val diaperLabel = strings.string(CoreR.string.kind_diaper)
    val style = when {
        !layout.labels -> FaceStyle.IconOnly
        listOf(feedLabel, diaperLabel).all { measure.fits(it, LabelSize, layout.buttonWidth - 8.dp) } -> FaceStyle.Regular
        else -> FaceStyle.Compact
    }
    // What the last week expects next (V1_ADDITIONS §9), under the label of the regular face only. When either button
    // has a line, both keep its room, so the icons stay aligned.
    val feedNext = snapshot.feedForecast?.takeIf { style == FaceStyle.Regular }
    val poopNext = snapshot.poopForecast?.takeIf { style == FaceStyle.Regular }
    val sub = feedNext != null || poopNext != null
    fun described(@StringRes name: Int, spoken: String?) =
        strings.forBaby(strings.string(name), baby).let { if (spoken == null) it else strings.string(R.string.spoken_pair, it, spoken) }
    Row(modifier) {
        val cell = GlanceModifier.defaultWeight().fillMaxHeight()
        val feed = colors.feed
        SheetButton(HomeLaunch.Feed, feed, described(R.string.widget_cd_log_feed, feedNext?.let(strings::feedForecastSpoken)), cell) {
            ButtonFace(XDrawables.feed, feed.strong, feedLabel, feed.onContainer, style, sub = feedNext?.let(strings::feedForecast) ?: "\u00A0".takeIf { sub })
        }
        Spacer(GlanceModifier.width(8.dp))
        val diaper = colors.diaper
        SheetButton(HomeLaunch.Diaper, diaper, described(R.string.widget_cd_log_diaper, poopNext?.let(strings::poopForecastSpoken)), cell) {
            ButtonFace(XDrawables.diaper, diaper.strong, diaperLabel, diaper.onContainer, style, sub = poopNext?.let(strings::poopForecast) ?: "\u00A0".takeIf { sub })
        }
    }
}

/** How a log button shows its label: icon 24 over 15 sp, the compact two-line face, or the icon alone (small widget). */
internal enum class FaceStyle { Regular, Compact, IconOnly }

private val LabelSize = 15.sp

/** Pushes the compact face's icon down to the regular face's icon line (checked on the widget screenshots). */
private val CompactTopSpace = 10.dp

/**
 * Icon over label, centred: 24 dp + 6 + 15 sp, or the compact 20 dp + two lines of 13 sp ("Afslut" / "amning").
 * The compact face starts with [CompactTopSpace], so that, centred in the same button, its icon's centre lines up with
 * the regular face's.
 */
@Composable
private fun ButtonFace(icon: Int, iconColor: Color?, label: String, labelColor: Color, style: FaceStyle, sub: String? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        if (style == FaceStyle.Compact) Spacer(GlanceModifier.height(CompactTopSpace))
        // With the forecast line the face tightens (icon 20, gap 2), so the three lines keep their padding in the button.
        WidgetIcon(icon, iconColor, if (style == FaceStyle.Compact || sub != null) 20.dp else 24.dp)
        if (style != FaceStyle.IconOnly) {
            Spacer(GlanceModifier.height(if (style == FaceStyle.Compact) 0.dp else if (sub != null) 2.dp else 6.dp))
            WidgetText(
                text = label,
                color = labelColor,
                size = if (style == FaceStyle.Compact) 13.sp else LabelSize,
                maxLines = 2,
                align = TextAlign.Center,
                modifier = GlanceModifier.padding(horizontal = 4.dp),
            )
            if (sub != null) WidgetText(sub, labelColor, 11.sp, maxLines = 1, align = TextAlign.Center)
        }
    }
}

/**
 * The sleep toggle: "Start sleep" with "Awake since 13:27" at the end, or the checked "Woke up" (lit fill, 2 dp
 * strong border) with "Asleep since 02:05". Under it, what the last week expects: "Next sleep ~15:25" or "Wakes ~05:30"
 * (V1_ADDITIONS §9). The end text wraps rather than clips when alone, and is dropped when narrow.
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
    val forecast = snapshot.sleepForecast?.let { strings.sleepForecast(it, sleepStart != null) }
    val forecastSpoken = snapshot.sleepForecast?.let { strings.sleepForecastSpoken(it, sleepStart != null) }
    val name = strings.forBaby(
        strings.string(if (sleepStart != null) R.string.cd_woke_up_end_sleep else CoreR.string.action_start_sleep),
        strings.babyLabel(snapshot),
    )
    WidgetButton(
        modifier = modifier,
        fill = if (sleepStart != null) sleep.containerLit else sleep.container,
        border = if (sleepStart != null) sleep.strong else null,
        radius = 34.dp,
        action = actionRunCallback<ToggleSleepAction>(),
        description = listOfNotNull(status, forecastSpoken).fold(name) { said, part -> strings.string(R.string.spoken_pair, said, part) },
    ) {
        val icon = if (sleepStart != null) XDrawables.wake else XDrawables.sleep
        if (!layout.labels) {
            WidgetIcon(icon, sleep.strong, 24.dp)
        } else {
            SleepToggleRow(icon, label, status, forecast, sleepStart != null, layout, colors)
        }
    }
}

@Composable
private fun SleepToggleRow(icon: Int, label: String, status: String?, forecast: String?, asleep: Boolean, layout: LogLayout, colors: WidgetColors) {
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
            if (forecast == null) {
                WidgetText(status, sleep.onContainer, 14.sp, maxLines = 2, align = TextAlign.End)
            } else {
                // The forecast goes under the status, one line each (V1_ADDITIONS §9).
                Column(horizontalAlignment = Alignment.End) {
                    WidgetText(status, sleep.onContainer, 14.sp, maxLines = 1, align = TextAlign.End)
                    WidgetText(forecast, sleep.onContainer, 13.sp, maxLines = 1, align = TextAlign.End)
                }
            }
        }
    }
}
