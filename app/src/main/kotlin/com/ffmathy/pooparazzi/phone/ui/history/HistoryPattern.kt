package com.ffmathy.pooparazzi.phone.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.ffmathy.pooparazzi.core.domain.DailyPattern
import com.ffmathy.pooparazzi.core.domain.PatternInterval
import com.ffmathy.pooparazzi.core.domain.PatternIntervals
import com.ffmathy.pooparazzi.core.domain.PatternRules
import com.ffmathy.pooparazzi.core.format.Formatters
import com.ffmathy.pooparazzi.core.ui.tokens.LocalXPalette
import com.ffmathy.pooparazzi.core.ui.type.PhoneType
import com.ffmathy.pooparazzi.phone.R
import java.time.LocalDate

/** The typical-day plot inside the Pattern card: the day ribbon's lanes and axis, as shares of the week. */
private object PatternDefaults {
    val PlotHeight = 100.dp
    const val SLEEP_TOP = 13f
    const val FEED_TOP = 34f
    const val DIAPER_TOP = 53f
    const val LANE_HEIGHT = 14f

    /** A slot with anything in it shows at least this strong, so a single feed in a week is still seen. */
    const val MIN_ALPHA = 0.18f
    /** The values sit under the label, past the swatch. */
    val ValueIndent = 18.dp
    val Swatch = 10.dp
}

/**
 * "Typical day" (History, under the day's stats): when the baby usually sleeps, feeds and gets a diaper over the
 * [DailyPattern] week before the day shown, drawn like the day ribbon above it (same lanes and axis, each half hour as
 * strong as it is typical), then the usual time between feeds and diapers, how long a sleep lasts and how long the
 * baby is awake between sleeps, by day and by night: the middle value and, under it, the range half of them fall in.
 * Without a pattern yet (under three logged days) it says so.
 */
@Composable
internal fun HistoryPattern(
    pattern: DailyPattern?,
    day: LocalDate,
    today: LocalDate,
    formatters: Formatters,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val events = LocalXPalette.current.events
    Column(
        modifier
            .fillMaxWidth()
            .clip(RibbonDefaults.Shape)
            .background(colors.surfaceContainer)
            .padding(vertical = 16.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp).semantics(mergeDescendants = true) { heading() }) {
            Text(stringResource(R.string.history_pattern_title), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            Text(
                if (day == today) {
                    stringResource(R.string.history_pattern_last_week)
                } else {
                    stringResource(R.string.history_pattern_week_before, formatters.clock.dayMonth(day))
                },
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
        if (pattern == null) {
            Text(
                stringResource(R.string.history_pattern_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
            return@Column
        }
        PatternPlot(pattern, Modifier.padding(top = 4.dp))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            HeaderRow()
            IntervalRow(stringResource(R.string.history_pattern_between_feeds), events.feed.strong, pattern, formatters) { it.betweenFeeds }
            IntervalRow(stringResource(R.string.history_pattern_between_diapers), events.diaper.strong, pattern, formatters) { it.betweenDiapers }
            IntervalRow(stringResource(R.string.history_pattern_sleep), events.sleep.strong, pattern, formatters) { it.sleep }
            IntervalRow(stringResource(R.string.history_pattern_awake), events.sleep.strong.copy(alpha = 0.45f), pattern, formatters) { it.awake }
        }
    }
}

/** Night bands, the three lanes (sleep, feeds, diapers) with one cell per half hour, and the "00 06 12 18 24" axis. */
@Composable
private fun PatternPlot(pattern: DailyPattern, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val palette = LocalXPalette.current
    val events = palette.events
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val nightBand = palette.colors.ribbonNightBand
    val axisLine = with(LocalDensity.current) { axisStyle.lineHeight.toDp() }
    val height = maxOf(PatternDefaults.PlotHeight, RibbonDefaults.AXIS_TOP.dp + axisLine + 6.dp)
    val description = stringResource(R.string.history_pattern_spoken_plot)
    Box(modifier.fillMaxWidth().height(height).clearAndSetSemantics { contentDescription = description }) {
        Canvas(Modifier.matchParentSize()) {
            val d = density
            val inset = RibbonDefaults.PLOT_INSET * d + 4 * d
            val plotWidth = size.width - 2 * inset
            fun x(fraction: Float) = inset + plotWidth * fraction
            // The default night window, 19:00–07:00, as on the day ribbon.
            nightBand(x(0f), x(7f / 24), nightBand, d)
            nightBand(x(19f / 24), x(1f), nightBand, d)
            lane(pattern.sleep, PatternDefaults.SLEEP_TOP, events.sleep.strong, ::x, d)
            lane(pattern.feeds, PatternDefaults.FEED_TOP, events.feed.strong, ::x, d)
            lane(pattern.diapers, PatternDefaults.DIAPER_TOP, events.diaper.strong, ::x, d)
            hourAxis(measurer, axisStyle, ::x, inset, d)
        }
    }
}

/** "By day" and "By night" over the two value columns. */
@Composable
private fun HeaderRow() {
    val style = MaterialTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(start = PatternDefaults.ValueIndent).clearAndSetSemantics { }) {
        Text(stringResource(R.string.history_pattern_by_day), style = style, color = color, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.history_pattern_by_night), style = style, color = color, modifier = Modifier.weight(1f))
    }
}

/** A swatch in the lane's colour and the label, then under it the day and night values: "2h 50m" over "2h 20m–3h 15m". */
@Composable
private fun IntervalRow(
    label: String,
    swatch: Color,
    pattern: DailyPattern,
    formatters: Formatters,
    pick: (PatternIntervals) -> PatternInterval?,
) {
    val colors = MaterialTheme.colorScheme
    val byDay = pick(pattern.day)
    val byNight = pick(pattern.night)
    val spoken = stringResource(
        R.string.history_pattern_spoken_row,
        label,
        spokenInterval(byDay, formatters),
        spokenInterval(byNight, formatters),
    )
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(PatternDefaults.Swatch).clip(CircleShape).background(swatch))
            Text(label, style = PhoneType.dataSmall, color = colors.onSurface, modifier = Modifier.padding(start = 8.dp))
        }
        Row(Modifier.fillMaxWidth().padding(start = PatternDefaults.ValueIndent, top = 2.dp)) {
            IntervalValue(byDay, formatters, Modifier.weight(1f))
            IntervalValue(byNight, formatters, Modifier.weight(1f))
        }
    }
}

@Composable
private fun IntervalValue(interval: PatternInterval?, formatters: Formatters, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        if (interval == null) {
            Text(stringResource(R.string.statistics_no_value), style = PhoneType.dataMedium, color = colors.onSurfaceVariant)
            return@Column
        }
        Text(formatters.duration.format(rounded(interval.medianMillis)), style = PhoneType.dataMedium, color = colors.onSurface)
        Text(
            stringResource(
                R.string.history_pattern_range,
                formatters.duration.format(rounded(interval.lowMillis)),
                formatters.duration.format(rounded(interval.highMillis)),
            ),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
    }
}

/** "about 2 hours 50 minutes, usually 2 hours 20 minutes to 3 hours 15 minutes", or "not logged". */
@Composable
private fun spokenInterval(interval: PatternInterval?, formatters: Formatters): String {
    interval ?: return stringResource(R.string.statistics_spoken_not_logged)
    val duration = formatters.duration
    return stringResource(
        R.string.history_pattern_spoken_interval,
        duration.spoken(rounded(interval.medianMillis)),
        duration.spoken(rounded(interval.lowMillis)),
        duration.spoken(rounded(interval.highMillis)),
    )
}

/** To the nearest 5 minutes: a week's typical gap, not a minute-exact figure. */
private fun rounded(millis: Long): Long = (millis + ROUND_MILLIS / 2) / ROUND_MILLIS * ROUND_MILLIS

private const val ROUND_MILLIS = 5 * 60_000L

private fun DrawScope.nightBand(from: Float, to: Float, color: Color, d: Float) {
    drawRoundRect(
        color,
        topLeft = Offset(from, RibbonDefaults.BAND_TOP * d),
        size = Size(to - from, (RibbonDefaults.BAND_BOTTOM - RibbonDefaults.BAND_TOP) * d),
        cornerRadius = CornerRadius(RibbonDefaults.BAND_RADIUS * d),
    )
}

/** One cell per slot, as opaque as its share (a faint floor for any share above 0); neighbours join into runs. */
private fun DrawScope.lane(shares: List<Float>, top: Float, color: Color, x: (Float) -> Float, d: Float) {
    val slots = PatternRules.SLOTS
    shares.forEachIndexed { i, share ->
        if (share <= 0f) return@forEachIndexed
        val left = x(i.toFloat() / slots)
        val right = x((i + 1).toFloat() / slots)
        drawRect(
            color.copy(alpha = color.alpha * share.coerceIn(PatternDefaults.MIN_ALPHA, 1f)),
            topLeft = Offset(left, top * d),
            size = Size(right - left, PatternDefaults.LANE_HEIGHT * d),
        )
    }
}

/** "00" start-aligned, "06" "12" "18" centred on their hour, "24" end-aligned, like the day ribbon. */
private fun DrawScope.hourAxis(measurer: TextMeasurer, style: TextStyle, x: (Float) -> Float, inset: Float, d: Float) {
    val top = RibbonDefaults.AXIS_TOP * d
    listOf(0, 6, 12, 18, 24).forEach { hour ->
        val label = measurer.measure(hour.toString().padStart(2, '0'), style)
        val w = label.size.width.toFloat()
        val left = when (hour) {
            0 -> inset
            24 -> size.width - inset - w
            else -> x(hour / 24f) - w / 2
        }
        drawText(label, topLeft = Offset(left, top))
    }
}
