package com.ffmathy.pooparazzi.phone.ui.statistics

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ffmathy.pooparazzi.core.domain.StatsChart
import com.ffmathy.pooparazzi.core.domain.StatsMetric
import com.ffmathy.pooparazzi.core.domain.StatsRange
import com.ffmathy.pooparazzi.core.domain.StatsReport
import com.ffmathy.pooparazzi.core.format.Formatters
import com.ffmathy.pooparazzi.core.format.rememberFormatters
import com.ffmathy.pooparazzi.core.ui.icons.XIcon
import com.ffmathy.pooparazzi.core.ui.icons.XIcons
import com.ffmathy.pooparazzi.core.ui.time.rememberReducedMotion
import com.ffmathy.pooparazzi.core.ui.tokens.LocalXPalette
import com.ffmathy.pooparazzi.core.ui.type.PhoneType
import com.ffmathy.pooparazzi.phone.R
import com.ffmathy.pooparazzi.phone.ui.components.ChipDefaults
import com.ffmathy.pooparazzi.phone.ui.components.XTopBar
import com.ffmathy.pooparazzi.phone.ui.history.HistoryRowDefaults
import com.ffmathy.pooparazzi.phone.ui.preview.StatisticsSamples
import com.ffmathy.pooparazzi.phone.ui.theme.LocalNightLightMotion
import com.ffmathy.pooparazzi.phone.ui.theme.NightLightTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import com.ffmathy.pooparazzi.core.R as CoreR

/** Statistics wired to its view model; Back returns to History. */
@Composable
fun StatisticsRoute(onBack: () -> Unit) {
    val viewModel: StatisticsViewModel = viewModel(factory = StatisticsViewModel.Factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Only while this screen is resumed: a second tap during the exit transition must not pop History as well.
    val back by rememberUpdatedState(dropUnlessResumed(block = onBack))
    val actions = remember(viewModel) {
        object : StatisticsActions by viewModel {
            override fun onBack() = back()
        }
    }
    val loaded = state
    if (loaded != null) {
        StatisticsScreen(loaded, actions)
    } else {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    }
}

/**
 * Statistics (P8, V1_ADDITIONS §8): averages per day of feeds, pee, poop and sleep, the last 7 days (left, large) next
 * to a range (right; the last 30 days unless the chip picks the last 90 days, all days or dates from a dialog). The
 * rows open one at a time; the open row shows its bar chart of the range. Only whole days up to yesterday count, and a
 * day counts for a kind only if one was logged that day. Settings' skeleton: one scrolling column under the top bar,
 * laid out edge to edge from [windowInsets].
 */
@Composable
fun StatisticsScreen(
    state: StatisticsUiState,
    actions: StatisticsActions,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
) {
    val colors = MaterialTheme.colorScheme
    val formatters = rememberFormatters(state.zone)
    val bottomInset = with(LocalDensity.current) { windowInsets.getBottom(this).toDp() }
    val report = state.report
    var choosing by rememberSaveable { mutableStateOf(false) }
    val subtitle: (@Composable ColumnScope.() -> Unit)? = if (report == null) {
        null
    } else {
        { Text(stringResource(R.string.statistics_subtitle), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant) }
    }
    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        XTopBar(
            title = stringResource(R.string.statistics_title),
            navigationIcon = XIcons.back,
            navigationLabel = stringResource(R.string.cd_back),
            onNavigate = actions::onBack,
            below = subtitle,
        )
        // The screen's only scrollable node (the menu and the date dialog are popups, outside it).
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = StatisticsDefaults.Gutter)
                .padding(top = 8.dp, bottom = 24.dp + bottomInset),
        ) {
            if (report == null) {
                Text(
                    stringResource(R.string.statistics_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            } else {
                StatisticsContent(state, report, formatters, actions, onChooseDates = { choosing = true })
            }
        }
    }
    if (choosing && report != null) {
        StatisticsRangeDialog(
            initialFrom = maxOf(report.comparison.from, report.firstDay),
            initialTo = report.comparison.to,
            firstDay = report.firstDay,
            lastDay = report.lastDay,
            onConfirm = { from, to ->
                choosing = false
                actions.onRange(StatsRange.Custom(from, to))
            },
            onDismiss = { choosing = false },
        )
    }
}

/** Layout constants (V1_ADDITIONS §8). */
internal object StatisticsDefaults {
    val Gutter = 20.dp

    /** The rows' content inset; the header starts at it too, so the columns line up. */
    val RowInset = 16.dp

    /** Between the 7-day column and the range column. */
    val ColumnGap = 12.dp
}

/** The period header, the four rows and the footnote. */
@Composable
private fun StatisticsContent(
    state: StatisticsUiState,
    report: StatsReport,
    formatters: Formatters,
    actions: StatisticsActions,
    onChooseDates: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Both number columns line up from the header through every row: (350 − 2 × 16 − 12) / 2 = 153 dp at 390 dp.
        val column = (maxWidth - StatisticsDefaults.RowInset * 2 - StatisticsDefaults.ColumnGap) / 2
        Column {
            PeriodHeader(report, state.today, column, formatters, onRange = actions::onRange, onChooseDates = onChooseDates)
            Column(
                Modifier
                    .padding(top = 12.dp)
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(HistoryRowDefaults.Gap),
            ) {
                StatsMetric.entries.forEachIndexed { index, metric ->
                    val open = metric == state.metric
                    MetricRow(
                        metric = metric,
                        report = report,
                        chart = if (open) state.chart else null,
                        open = open,
                        first = index == 0,
                        last = index == StatsMetric.entries.lastIndex,
                        column = column,
                        today = state.today,
                        formatters = formatters,
                        onClick = { actions.onMetric(metric) },
                    )
                }
            }
            Text(
                stringResource(R.string.statistics_footnote),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
}

/**
 * "Last 7 days" with its dates (a heading for TalkBack: "Last 7 days, 19 October to 25 October") over the left
 * column, and the range chip with the range's dates over the right one. A coverage line ("3 of 7 days logged") follows
 * the dates whenever a window has days with nothing logged.
 */
@Composable
private fun PeriodHeader(
    report: StatsReport,
    today: LocalDate,
    column: Dp,
    formatters: Formatters,
    onRange: (StatsRange) -> Unit,
    onChooseDates: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val week = report.lastSevenDays
    val comparison = report.comparison
    val weekLabel = stringResource(R.string.statistics_last_7_days)
    val weekSpoken = windowSpoken(weekLabel, week, today, formatters)
    val label = rangeLabel(report.range)
    val chipSpoken = windowSpoken(label, comparison, today, formatters)
    // The range column may run into the 16 dp end inset (169 dp at 390 dp).
    Row(Modifier.fillMaxWidth().padding(start = StatisticsDefaults.RowInset)) {
        Column(
            Modifier
                .width(column)
                .semantics(mergeDescendants = true) {
                    heading()
                    contentDescription = weekSpoken
                },
        ) {
            Box(Modifier.heightIn(min = ChipDefaults.TargetHeight), contentAlignment = Alignment.CenterStart) {
                Text(weekLabel, style = MaterialTheme.typography.labelLarge, color = colors.onSurface)
            }
            Text(visibleRange(week.from, week.to, today, formatters), style = PhoneType.dataSmall, color = colors.onSurfaceVariant)
            coverageText(week)?.let { Text(it, style = PhoneType.dataSmall, color = colors.onSurfaceVariant) }
        }
        Spacer(Modifier.width(StatisticsDefaults.ColumnGap))
        Column(Modifier.weight(1f)) {
            RangeChip(report.range, label, chipSpoken, onRange, onChooseDates)
            // The chip's TalkBack name already says the dates and the coverage.
            Text(
                visibleRange(comparison.from, comparison.to, today, formatters),
                style = PhoneType.dataSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.clearAndSetSemantics { },
            )
            coverageText(comparison)?.let {
                Text(it, style = PhoneType.dataSmall, color = colors.onSurfaceVariant, modifier = Modifier.clearAndSetSemantics { })
            }
        }
    }
}

/**
 * The range chip, like the edit sheet's day chip: "Last 30 days ▾" (48 target, 40 pill with an outline border; the
 * label wraps and the pill grows rather than clip). Its menu offers the presets and "Choose dates…".
 */
@Composable
private fun RangeChip(range: StatsRange, label: String, spoken: String, onRange: (StatsRange) -> Unit, onChooseDates: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .heightIn(min = ChipDefaults.TargetHeight)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.statistics_change_range)) { menu = true }
                .clearAndSetSemantics { contentDescription = spoken },
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                Modifier
                    .heightIn(min = ChipDefaults.PillHeight)
                    .clip(ChipDefaults.PillShape)
                    .border(1.dp, colors.outline, ChipDefaults.PillShape)
                    .padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = colors.onSurface, modifier = Modifier.weight(1f, fill = false))
                XIcon(XIcons.chevronDown, contentDescription = null, tint = colors.onSurfaceVariant, size = 16.dp)
            }
        }
        DropdownMenu(
            expanded = menu,
            onDismissRequest = { menu = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = colors.surfaceContainerHigh,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, colors.outlineVariant),
        ) {
            StatisticsRangeMenuItems(
                range = range,
                onRange = {
                    menu = false
                    onRange(it)
                },
                onChooseDates = {
                    menu = false
                    onChooseDates()
                },
            )
        }
    }
}

/** The range menu: Last 30 days · Last 90 days · All days · Choose dates… (selected when the range is chosen dates). */
@Composable
internal fun ColumnScope.StatisticsRangeMenuItems(range: StatsRange, onRange: (StatsRange) -> Unit, onChooseDates: () -> Unit) {
    RangeMenuItem(stringResource(R.string.statistics_last_30_days), selected = range == StatsRange.Last30Days) { onRange(StatsRange.Last30Days) }
    RangeMenuItem(stringResource(R.string.statistics_last_90_days), selected = range == StatsRange.Last90Days) { onRange(StatsRange.Last90Days) }
    RangeMenuItem(stringResource(R.string.statistics_all_days), selected = range == StatsRange.AllDays) { onRange(StatsRange.AllDays) }
    RangeMenuItem(stringResource(R.string.statistics_choose_dates), selected = range is StatsRange.Custom, onClick = onChooseDates)
}

@Composable
private fun RangeMenuItem(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.labelLarge, color = colors.onSurface) },
        onClick = onClick,
        leadingIcon = {
            Box(Modifier.width(16.dp)) {
                if (selected) XIcon(XIcons.check, contentDescription = null, tint = colors.onSurface, size = 16.dp)
            }
        },
        modifier = Modifier.semantics { this.selected = selected },
    )
}

/**
 * "Choose dates…": the M3 date range picker from the first full day of logging ([firstDay]) to yesterday ([lastDay]),
 * opening on [initialFrom]..[initialTo]. OK needs a start; a start alone is a one-day range. Dates travel as UTC
 * midnights, as in the edit sheet's date picker.
 */
@Composable
internal fun StatisticsRangeDialog(
    initialFrom: LocalDate,
    initialTo: LocalDate,
    firstDay: LocalDate,
    lastDay: LocalDate,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = utcMillis(firstDay)
    val last = utcMillis(lastDay)
    val years = firstDay.year..lastDay.year
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = utcMillis(initialFrom),
        initialSelectedEndDateMillis = utcMillis(initialTo),
        initialDisplayedMonthMillis = utcMillis(initialTo),
        yearRange = years,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis in first..last
            override fun isSelectableYear(year: Int): Boolean = year in years
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    state.selectedStartDateMillis?.let { startMillis ->
                        val start = utcDate(startMillis)
                        val end = state.selectedEndDateMillis?.let(::utcDate) ?: start
                        onConfirm(minOf(start, end), maxOf(start, end))
                    }
                },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreR.string.action_cancel)) } },
        tonalElevation = 0.dp,
    ) {
        // A bounded height: the picker's months are a lazy list, so it never goes inside a vertical scroll.
        DateRangePicker(state, Modifier.weight(1f))
    }
}

private fun utcMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun utcDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

/**
 * One row (r 20 / r 6 group corners, surfaceContainer): the kind's glyph in its strong colour and its name, then the
 * 7-day average (displaySmall) and the range's (titleLarge) on one baseline, and "Last 30 days 1.8" under the range's
 * when the range is another. The tappable part is one radio-button node for TalkBack; the open row adds its chart.
 */
@Composable
private fun MetricRow(
    metric: StatsMetric,
    report: StatsReport,
    chart: StatsChart?,
    open: Boolean,
    first: Boolean,
    last: Boolean,
    column: Dp,
    today: LocalDate,
    formatters: Formatters,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val events = LocalXPalette.current.events
    val reducedMotion = rememberReducedMotion()
    val motion = LocalNightLightMotion.current
    val spoken = rowSpoken(metric, report, formatters)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(HistoryRowDefaults.shape(first, last))
            .background(colors.surfaceContainer)
            .then(if (reducedMotion) Modifier else Modifier.animateContentSize(motion.defaultSpatialSpec())),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                // Before clearAndSetSemantics, so the role, the selected state and the click survive it.
                .selectable(selected = open, role = Role.RadioButton, onClick = onClick)
                .clearAndSetSemantics { contentDescription = spoken }
                .padding(start = StatisticsDefaults.RowInset, end = StatisticsDefaults.RowInset, top = 12.dp, bottom = 8.dp),
        ) {
            Row(Modifier.fillMaxWidth().heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                XIcon(metricIcon(metric), contentDescription = null, tint = metricStrong(metric, events), size = 20.dp)
                Spacer(Modifier.width(8.dp))
                Text(metricName(metric), style = MaterialTheme.typography.labelLarge, color = colors.onSurface, modifier = Modifier.weight(1f))
                if (!open) XIcon(XIcons.chevronDown, contentDescription = null, tint = colors.onSurfaceVariant, size = 20.dp)
            }
            Spacer(Modifier.height(4.dp))
            // The numbers shrink rather than clip (a 360 dp phone, a large font): "14h 01m" is about 38 sp in 153 dp.
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                val large = MaterialTheme.typography.displaySmall
                BasicText(
                    averageText(report.lastSevenDays.average(metric), metric, formatters),
                    style = large.copy(color = colors.onSurface),
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = large.fontSize, stepSize = 0.5.sp),
                    modifier = Modifier.width(column).alignByBaseline(),
                )
                Spacer(Modifier.width(StatisticsDefaults.ColumnGap))
                val small = MaterialTheme.typography.titleLarge
                BasicText(
                    averageText(report.comparison.average(metric), metric, formatters),
                    style = small.copy(color = colors.onSurface),
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = small.fontSize, stepSize = 0.5.sp),
                    modifier = Modifier.weight(1f).alignByBaseline(),
                )
            }
            if (report.range != StatsRange.Last30Days) {
                Row(Modifier.padding(top = 2.dp)) {
                    Spacer(Modifier.width(column + StatisticsDefaults.ColumnGap))
                    Text(
                        stringResource(
                            R.string.join_space,
                            stringResource(R.string.statistics_last_30_days),
                            averageText(report.lastThirtyDays.average(metric), metric, formatters),
                        ),
                        style = PhoneType.dataSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
        if (open && chart != null) {
            StatisticsChart(
                chart = chart,
                report = report,
                today = today,
                formatters = formatters,
                modifier = Modifier.padding(start = StatisticsDefaults.RowInset, end = StatisticsDefaults.RowInset, top = 4.dp, bottom = 12.dp),
            )
        }
    }
}

private fun metricIcon(metric: StatsMetric): ImageVector = when (metric) {
    StatsMetric.FEEDS -> XIcons.feed
    StatsMetric.PEE -> XIcons.pee
    StatsMetric.POOP -> XIcons.poop
    StatsMetric.SLEEP -> XIcons.sleep
}

@Preview(name = "Day · last 30 days", widthDp = 390, heightDp = 1000)
@Composable
private fun StatisticsDayPreview() {
    NightLightTheme(night = false) { StatisticsScreen(StatisticsSamples.lastThirtyDays(), StatisticsActions.None) }
}

@Preview(name = "Night · 03:12", widthDp = 390, heightDp = 844)
@Composable
private fun StatisticsNightPreview() {
    NightLightTheme(night = true) { StatisticsScreen(StatisticsSamples.night(), StatisticsActions.None) }
}

@Preview(name = "Night · chosen dates · Danish", widthDp = 390, heightDp = 1000, locale = "da")
@Composable
private fun StatisticsCustomPreview() {
    NightLightTheme(night = true) { StatisticsScreen(StatisticsSamples.custom(), StatisticsActions.None) }
}

@Preview(name = "Day · empty", widthDp = 390, heightDp = 844)
@Composable
private fun StatisticsEmptyPreview() {
    NightLightTheme(night = false) { StatisticsScreen(StatisticsSamples.empty(), StatisticsActions.None) }
}
