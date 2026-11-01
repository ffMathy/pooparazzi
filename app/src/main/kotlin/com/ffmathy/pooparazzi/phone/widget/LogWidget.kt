package com.ffmathy.pooparazzi.phone.widget

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
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
import com.ffmathy.pooparazzi.core.domain.Expected
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

    override suspend fun provideGlance(context: Context, id: GlanceId) = provideSnapshots(context, id) { LogWidgetContent(it) }

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

    override suspend fun provideGlance(context: Context, id: GlanceId) = provideSnapshots(context, id) { LogWidgetContent(it) }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { LogWidgetContent(WidgetSamples.idle(night = true)) }
    }
}

/**
 * Which parts of the 4×2 widget fit at a size (DESIGN_SPEC §10.11 breakpoints). [width] is the size the widget is
 * composed for (Glance's responsive bucket, never wider than the real widget), so text measured against it fits.
 */
internal data class LogLayout(val statusRow: Boolean, val labels: Boolean, val width: Dp, val tall: Boolean = false) {
    /** Row 1's width inside the 12 dp padding. */
    val rowWidth: Dp get() = width - 24.dp

    /** One of the three buttons (gap 8). */
    val buttonWidth: Dp get() = (width - 24.dp - 16.dp) / 3

    companion object {
        /** From this height the three buttons are stacked, each a full-width row. */
        val TALL_HEIGHT = 280.dp

        val Sizes: Set<DpSize> = setOf(
            DpSize(120.dp, 100.dp),
            DpSize(240.dp, 130.dp),
            DpSize(240.dp, 180.dp),
            DpSize(320.dp, 130.dp),
            DpSize(320.dp, 180.dp),
        )

        /**
         * Row 1 needs 180 dp of height (below it the widget keeps only the buttons, like a 2×2); labels need 240 dp
         * of width. Where a label does not fit its third on one line, the buttons take the compact face. A tall widget
         * (from [TALL_HEIGHT], as on a Samsung flip's cover screen, 352 × 339 dp) stacks the three buttons instead.
         */
        fun of(size: DpSize): LogLayout {
            val statusRow = size.height >= 180.dp
            return LogLayout(
                statusRow = statusRow,
                labels = size.width >= 240.dp && size.height >= 130.dp,
                width = size.width,
                tall = size.height >= TALL_HEIGHT && size.width >= 240.dp,
            )
        }
    }
}

/** The 4×2 widget for [snapshot], laid out for the current [LocalSize]. */
@Composable
internal fun LogWidgetContent(snapshot: WidgetSnapshot) {
    val context = LocalContext.current
    val strings = remember(context, snapshot.language, snapshot.zone) { WidgetStrings(context.inAppLanguage(snapshot.language), snapshot.zone) }
    val layout = LogLayout.of(LocalSize.current)
    val measure = remember(context) { WidgetMeasure(context) }
    val colors = WidgetColors(snapshot.night)
    WidgetSurface(colors, snapshot.backgroundLaunch, snapshot.launchBabyId) {
        Column(
            GlanceModifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = if (layout.statusRow) 0.dp else 12.dp),
        ) {
            if (layout.statusRow) {
                // 48 dp band: the 24 dp row centred 12 dp from the top, so Undo gets a 48 dp target (DESIGN_SPEC §9.3).
                StatusRow(snapshot, strings, colors, layout, measure, GlanceModifier.fillMaxWidth().height(48.dp))
                Spacer(GlanceModifier.height(4.dp))
            }
            LogButtons(snapshot, strings, colors, layout, measure, GlanceModifier.fillMaxWidth().defaultWeight())
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
            else -> StatusTitle(snapshot, baby, strings, colors)
        }
    }
}

/**
 * Idle and asleep: the baby's name, as the watch's title (nothing when one baby is unnamed). With two or more babies it
 * is the widget's baby switcher, as the name with a chevron is on Home: a tap moves this widget on to the next baby
 * (Noa → Ida → Noa), so twins can each have a widget. The times sit in the buttons they belong to, as on the watch:
 * "2h 10m ago" in Feed and Diaper, "Awake 1h 05m" in the sleep toggle.
 */
@Composable
private fun StatusTitle(snapshot: WidgetSnapshot, baby: String?, strings: WidgetStrings, colors: WidgetColors) {
    if (baby != null) {
        BabySwitcher(baby, strings, colors)
        return
    }
    val name = snapshot.baby.name.trim().takeIf { it.isNotEmpty() } ?: return
    BabyLabel(name, colors)
}

/** The widget's baby with a chevron, in a pill: a tap moves the widget on to the next baby ([NextBabyAction]). */
@Composable
private fun BabySwitcher(label: String, strings: WidgetStrings, colors: WidgetColors) {
    Row(
        GlanceModifier
            .height(36.dp)
            .cornerRadius(18.dp)
            .background(colors.surfaceHigh.provider())
            .padding(start = 12.dp, end = 8.dp)
            .clickable(actionRunCallback<NextBabyAction>())
            .semantics { contentDescription = strings.string(CoreR.string.cd_baby_switch, label) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetText(label, colors.onSurface, FactSize)
        Spacer(GlanceModifier.width(4.dp))
        WidgetIcon(XDrawables.chevronDown, colors.onSurfaceVariant, 16.dp)
    }
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
 * Feed, Diaper and the sleep toggle: three equal buttons side by side, gap 8, r 20, as on the 3×1 widget but taller.
 * Feed and Diaper open the app on their logging sheet (the same sheet as Home's buttons), where the optional details are
 * chosen: feed type and breast order, pee/poop, colour and texture. The sleep toggle starts or ends the sleep in place.
 *
 * Under each label, a small icon says what each time is, so the lines stay short: `history` + the time since the last
 * one ("2h 10m"; the sleep toggle `awake` or `sleep` + "1h 05m"), then `upcoming` + what the last week expects
 * next ("55m", "soon", "24m ago"; under Diaper the next poop, V1_ADDITIONS §9). The three keep the same number of
 * lines, so their icons and labels stay aligned.
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
    // With two or more babies TalkBack names the baby the buttons are for (BABIES_AND_BACKUP §2.4).
    val baby = strings.babyLabel(snapshot)
    val sleepStart = snapshot.runningSleepStart
    val asleep = sleepStart != null
    val feedLabel = strings.string(CoreR.string.kind_feed)
    val diaperLabel = strings.string(CoreR.string.kind_diaper)
    val sleepLabel = strings.string(if (asleep) CoreR.string.action_woke_up else CoreR.string.action_start_sleep)
    // All three share one face, so their icons stay aligned: a label that needs two lines compacts all three.
    val style = when {
        !layout.labels -> FaceStyle.IconOnly
        listOf(feedLabel, diaperLabel, sleepLabel).all { measure.fits(it, LabelSize, layout.buttonWidth - 8.dp) } -> FaceStyle.Regular
        else -> FaceStyle.Compact
    }
    // The times show on both faces with labels; the icon-only face (a small widget) has no room.
    val regular = style != FaceStyle.IconOnly
    val now = snapshot.now
    val lastFeed = snapshot.lastFeed?.takeIf { regular }
    val lastDiaper = snapshot.lastDiaper?.takeIf { regular }
    val feedNext = snapshot.feedForecast?.takeIf { regular }
    val poopNext = snapshot.poopForecast?.takeIf { regular }
    val sleepNext = snapshot.sleepForecast?.takeIf { regular }
    val sleepStatus = when {
        !regular -> null
        sleepStart != null -> SubLine(XDrawables.sleep, strings.lasting(sleepStart, now))
        snapshot.awakeSinceMillis != null -> SubLine(XDrawables.awake, strings.lasting(snapshot.awakeSinceMillis, now))
        else -> null
    }
    val sleepStatusSpoken = when {
        sleepStart != null -> strings.asleepFor(sleepStart, now)
        snapshot.awakeSinceMillis != null -> strings.awakeFor(snapshot.awakeSinceMillis, now)
        else -> null
    }
    fun next(expected: Expected?) = expected?.let { SubLine(XDrawables.upcoming, strings.forecast(it, now)) }
    val feedLines = listOfNotNull(lastFeed?.let { SubLine(XDrawables.history, strings.fedSince(it, now)) }, next(feedNext))
    val diaperLines = listOfNotNull(lastDiaper?.let { SubLine(XDrawables.history, strings.since(it.startMillis, now)) }, next(poopNext))
    val stacked = layout.tall && style != FaceStyle.IconOnly
    val sleepLines = listOfNotNull(sleepStatus, next(sleepNext))
    val lines = maxOf(feedLines.size, diaperLines.size, sleepLines.size)
    fun padded(sub: List<SubLine>) = sub + List(lines - sub.size) { SubLine(null, "\u00A0") }
    fun described(name: String, vararg spoken: String?) =
        spoken.filterNotNull().fold(name) { said, part -> strings.string(R.string.spoken_pair, said, part) }
    // A tall widget stacks them: each a full-width row with the icon at the start and its lines beside it.
    @Composable
    fun face(icon: Int, iconColor: Color?, label: String, labelColor: Color, lines: List<SubLine>) =
        if (stacked) RowFace(icon, iconColor, label, labelColor, lines) else ButtonFace(icon, iconColor, label, labelColor, style, sub = padded(lines))
    StackOrRow(stacked, modifier) { cell, gap ->
        val feed = colors.feed
        val feedSaid = described(
            strings.forBaby(strings.string(R.string.widget_cd_log_feed), baby),
            lastFeed?.let { strings.agoSpoken(CoreR.string.spoken_last_feed, it.startMillis, now) },
            snapshot.feedForecast?.let(strings::feedForecastSpoken),
        )
        SheetButton(HomeLaunch.Feed, snapshot.launchBabyId, feed, feedSaid, cell) {
            face(XDrawables.forFeedType(snapshot.lastFeed?.feedType), feed.strong, feedLabel, feed.onContainer, feedLines)
        }
        Spacer(gap)
        val diaper = colors.diaper
        val diaperSaid = described(
            strings.forBaby(strings.string(R.string.widget_cd_log_diaper), baby),
            lastDiaper?.let { strings.agoSpoken(CoreR.string.spoken_last_diaper, it.startMillis, now) },
            snapshot.poopForecast?.let(strings::poopForecastSpoken),
        )
        SheetButton(HomeLaunch.Diaper, snapshot.launchBabyId, diaper, diaperSaid, cell) {
            face(XDrawables.diaper, diaper.strong, diaperLabel, diaper.onContainer, diaperLines)
        }
        Spacer(gap)
        val sleep = colors.sleep
        val sleepSaid = described(
            strings.forBaby(strings.string(if (asleep) R.string.cd_woke_up_end_sleep else CoreR.string.action_start_sleep), baby),
            sleepStatusSpoken,
            snapshot.sleepForecast?.let { strings.sleepForecastSpoken(it, asleep) },
        )
        WidgetButton(
            modifier = cell,
            fill = if (asleep) sleep.containerLit else sleep.container,
            border = if (asleep) sleep.strong else null,
            radius = 20.dp,
            action = actionRunCallback<ToggleSleepAction>(),
            description = sleepSaid,
        ) {
            face(if (asleep) XDrawables.wake else XDrawables.sleep, sleep.strong, sleepLabel, sleep.onContainer, sleepLines)
        }
    }
}

/**
 * A line that wraps only where it reads well: never inside a duration ("1t 00m") or a breast order ("L → R"), so a
 * narrow button breaks "1h 32m · / L → R".
 */
internal fun keepTogether(text: String): String =
    text.replace(Regex("""(\d+\p{L}+) (\d)"""), "$1\u00A0$2").replace(" → ", "\u00A0→\u00A0")

/** The three buttons side by side, or stacked ([stacked]); [content] gets each button's modifier and the gap after it. */
@Composable
private fun StackOrRow(stacked: Boolean, modifier: GlanceModifier, content: @Composable (cell: GlanceModifier, gap: GlanceModifier) -> Unit) {
    if (stacked) {
        Column(modifier) { content(GlanceModifier.fillMaxWidth().defaultWeight(), GlanceModifier.height(8.dp)) }
    } else {
        Row(modifier) { content(GlanceModifier.defaultWeight().fillMaxHeight(), GlanceModifier.width(8.dp)) }
    }
}

/** A stacked button's face: icon 24 at the start, then the label 16 sp over its lines in 13 sp, as the watch's rows. */
@Composable
private fun RowFace(icon: Int, iconColor: Color?, label: String, labelColor: Color, lines: List<SubLine>) {
    Row(GlanceModifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        WidgetIcon(icon, iconColor, 24.dp)
        Spacer(GlanceModifier.width(14.dp))
        Column {
            WidgetText(label, labelColor, 16.sp, maxLines = 1)
            lines.forEach { SubLineText(it, labelColor, 13.sp, 13.dp, maxLines = 1) }
        }
    }
}

/** How a log button shows its label: icon 24 over 15 sp, the compact two-line face, or the icon alone (small widget). */
internal enum class FaceStyle { Regular, Compact, IconOnly }

private val LabelSize = 15.sp

/** Pushes the compact face's icon down to the regular face's icon line (checked on the widget screenshots). */
private val CompactTopSpace = 10.dp

/**
 * Icon over label, centred: 24 dp + 6 + 15 sp, then the [sub] lines (icon 12 dp + 3 + 12 sp, up to two lines each); or the compact
 * 20 dp + two lines of 13 sp ("Start" / "sleep") with the [sub] lines in 11 sp. The compact face starts with [CompactTopSpace], so that, centred in
 * the same button, its icon's centre lines up with the regular face's.
 */
@Composable
private fun ButtonFace(icon: Int, iconColor: Color?, label: String, labelColor: Color, style: FaceStyle, sub: List<SubLine> = emptyList()) {
    Column(
        GlanceModifier.padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (style == FaceStyle.Compact) Spacer(GlanceModifier.height(CompactTopSpace))
        WidgetIcon(icon, iconColor, if (style == FaceStyle.Compact) 20.dp else 24.dp)
        if (style != FaceStyle.IconOnly) {
            Spacer(GlanceModifier.height(if (style == FaceStyle.Compact) 0.dp else 6.dp))
            WidgetText(
                text = label,
                color = labelColor,
                size = if (style == FaceStyle.Compact) 13.sp else LabelSize,
                maxLines = 2,
                align = TextAlign.Center,
            )
            if (sub.isNotEmpty()) Spacer(GlanceModifier.height(2.dp))
            val compact = style == FaceStyle.Compact
            sub.forEach { SubLineText(it, labelColor, if (compact) 11.sp else 12.sp, if (compact) 11.dp else 12.dp, maxLines = 2) }
        }
    }
}

/** One line under a button's label: [icon] (`history`, `upcoming`, `awake`, `sleep`) says what [text]'s time is. */
internal data class SubLine(@DrawableRes val icon: Int?, val text: String)

/**
 * [line]'s icon at the text's size, 3 dp, then the text; an icon-less line (a reserved blank) is the text alone. The
 * icon sits by the first line, so a line that wraps ("1h 32m · / L → R") keeps it beside the time.
 */
@Composable
private fun SubLineText(line: SubLine, color: Color, size: TextUnit, iconSize: Dp, maxLines: Int) {
    Row(verticalAlignment = Alignment.Top) {
        if (line.icon != null) {
            // Centres the icon on the first line (its line height is about 4/3 of the text size).
            Box(GlanceModifier.padding(top = (size.value / 3f / 2f + 1f).dp)) { WidgetIcon(line.icon, color, iconSize) }
            Spacer(GlanceModifier.width(3.dp))
        }
        WidgetText(keepTogether(line.text), color, size, maxLines = maxLines)
    }
}
