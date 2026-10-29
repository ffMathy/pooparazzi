package com.ffmathy.pooparazzi.phone.ui.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ffmathy.pooparazzi.core.domain.StatsChart
import com.ffmathy.pooparazzi.core.domain.StatsMetric
import com.ffmathy.pooparazzi.core.domain.StatsReport
import com.ffmathy.pooparazzi.core.format.Formatters
import com.ffmathy.pooparazzi.core.ui.tokens.EventPalette
import com.ffmathy.pooparazzi.core.ui.tokens.LocalXPalette
import com.ffmathy.pooparazzi.phone.R
import com.ffmathy.pooparazzi.phone.ui.home.LARGE_FONT_SCALE
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

/** Chart geometry in dp (V1_ADDITIONS §8): a plot the width of the card's content, 112 tall. */
internal object StatisticsChartDefaults {
    val PlotHeight = 112.dp

    /** A bar is 60 % of its slot, between 2 and 12 wide, at least 3 tall when its value is above 0. */
    const val BAR_FRACTION = 0.6f
    const val BAR_MIN_WIDTH = 2f
    const val BAR_MAX_WIDTH = 12f
    const val BAR_MIN_HEIGHT = 3f
    const val BAR_RADIUS = 3f

    /** A counted day with a value of 0: a 2 dp mark on the baseline (a gap has no mark at all). */
    const val ZERO_HEIGHT = 2f

    const val HAIRLINE = 1f

    /** The last-7-days band: 1 inside its first and last slots, r 6, the plot's full height. */
    const val BAND_INSET = 1f
    const val BAND_RADIUS = 6f

    /** The 7-day (solid) and comparison (dashed) lines, each over a halo that knocks out the bars beneath. */
    const val LINE_WIDTH = 2f
    const val HALO_WIDTH = 4f
    const val DASH_ON = 6f
    const val DASH_OFF = 4f

    val SwatchWidth = 20.dp
    val SwatchHeight = 12.dp
    const val SWATCH_RADIUS = 3f
}

/** The event colour of [metric]'s bars and row icon: a mark colour, never text. */
internal fun metricStrong(metric: StatsMetric, events: EventPalette): Color = when (metric) {
    StatsMetric.FEEDS -> events.feed.strong
    StatsMetric.PEE -> events.pee.strong
    StatsMetric.POOP -> events.poop.strong
    StatsMetric.SLEEP -> events.sleep.strong
}

/**
 * The open row's bar chart of [chart] (V1_ADDITIONS §8): the scale label over a decorative hairline, the plot (bars in
 * the metric's strong colour, 2 dp marks for real zeros, nothing for gaps; the last 7 days on a band with their average
 * as a solid line; the range's average dashed), the first and last dates, the legend and the gaps note. TalkBack hears
 * one summary instead ([chartSpoken]). With nothing counted it is just "Nothing logged on these days.".
 */
@Composable
internal fun StatisticsChart(chart: StatsChart, report: StatsReport, today: LocalDate, formatters: Formatters, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    if (chart.isEmpty) {
        Text(
            stringResource(R.string.statistics_chart_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = modifier.padding(vertical = 8.dp),
        )
        return
    }
    val summary = chartSpoken(chart, report, today, formatters)
    // Colours are read here, never inside the Canvas (as in HistoryRibbon).
    val bar = metricStrong(chart.metric, LocalXPalette.current.events)
    val band = colors.surfaceContainerHighest
    val card = colors.surfaceContainer
    val hairline = colors.outlineVariant
    val weekLine = colors.onSurface
    val rangeLine = colors.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    val year = showsYear(chart.from, chart.to, today)
    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = summary }) {
        Text(scaleLabel(chart, formatters), style = labelStyle, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Canvas(Modifier.fillMaxWidth().height(StatisticsChartDefaults.PlotHeight)) {
            plot(chart, bar = bar, band = band, card = card, hairline = hairline, weekColor = weekLine, rangeColor = rangeLine)
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(
                visibleDate(chart.from, year, formatters),
                style = labelStyle,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
            if (chart.to != chart.from) {
                Text(
                    visibleDate(chart.to, year, formatters),
                    style = labelStyle,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Legend(chart, report, band = band, weekColor = weekLine, rangeColor = rangeLine)
        if (chart.gapDays > 0) {
            Text(
                gapsNote(chart.metric),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Band, hairline, bars and zero marks, the dashed comparison line, then the 7-day line (V1_ADDITIONS §8). */
private fun DrawScope.plot(chart: StatsChart, bar: Color, band: Color, card: Color, hairline: Color, weekColor: Color, rangeColor: Color) {
    val d = density
    val height = size.height
    val slot = size.width / chart.bars.size
    val barWidth = (slot * StatisticsChartDefaults.BAR_FRACTION)
        .coerceIn(StatisticsChartDefaults.BAR_MIN_WIDTH * d, StatisticsChartDefaults.BAR_MAX_WIDTH * d)
    fun y(value: Double): Float = height - (value / chart.top).toFloat() * height

    val bandBars = chart.bandBars
    val bandLeft = if (bandBars != null) bandBars.first * slot + StatisticsChartDefaults.BAND_INSET * d else 0f
    val bandRight = if (bandBars != null) (bandBars.last + 1) * slot - StatisticsChartDefaults.BAND_INSET * d else 0f
    if (bandBars != null && bandRight > bandLeft) {
        drawRoundRect(
            band,
            topLeft = Offset(bandLeft, 0f),
            size = Size(bandRight - bandLeft, height),
            cornerRadius = CornerRadius(StatisticsChartDefaults.BAND_RADIUS * d),
        )
    }

    drawRect(hairline, topLeft = Offset.Zero, size = Size(size.width, StatisticsChartDefaults.HAIRLINE * d))

    chart.bars.forEachIndexed { index, stat ->
        val value = stat.value ?: return@forEachIndexed
        val left = (index + 0.5f) * slot - barWidth / 2
        if (value <= 0.0) {
            val zero = StatisticsChartDefaults.ZERO_HEIGHT * d
            drawRect(bar, topLeft = Offset(left, height - zero), size = Size(barWidth, zero))
        } else {
            val barHeight = max((value / chart.top).toFloat() * height, StatisticsChartDefaults.BAR_MIN_HEIGHT * d)
            drawRoundRect(
                bar,
                topLeft = Offset(left, height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(min(StatisticsChartDefaults.BAR_RADIUS * d, barWidth / 2)),
            )
        }
    }

    chart.rangeLine?.let { value ->
        val at = y(value)
        val dashes = PathEffect.dashPathEffect(floatArrayOf(StatisticsChartDefaults.DASH_ON * d, StatisticsChartDefaults.DASH_OFF * d))
        drawLine(card, Offset(0f, at), Offset(size.width, at), strokeWidth = StatisticsChartDefaults.HALO_WIDTH * d, pathEffect = dashes)
        drawLine(rangeColor, Offset(0f, at), Offset(size.width, at), strokeWidth = StatisticsChartDefaults.LINE_WIDTH * d, pathEffect = dashes)
    }

    val week = chart.weekLine
    if (week != null && bandBars != null && bandRight > bandLeft) {
        val at = y(week)
        drawLine(band, Offset(bandLeft, at), Offset(bandRight, at), strokeWidth = StatisticsChartDefaults.HALO_WIDTH * d, cap = StrokeCap.Butt)
        drawLine(weekColor, Offset(bandLeft, at), Offset(bandRight, at), strokeWidth = StatisticsChartDefaults.LINE_WIDTH * d, cap = StrokeCap.Butt)
    }
}

/**
 * "Last 7 days" (a band swatch, with the solid line when it is drawn), the range ("Last 30 days", a dashed line) and
 * "Each bar is 7 days" for bars of several days. One row for up to two items; stacked for three, or from 130 % font.
 */
@Composable
private fun Legend(chart: StatsChart, report: StatsReport, band: Color, weekColor: Color, rangeColor: Color) {
    val showWeek = chart.bandBars != null
    val showBins = chart.binDays > 1
    val items = 1 + (if (showWeek) 1 else 0) + (if (showBins) 1 else 0)
    val stacked = items >= 3 || LocalDensity.current.fontScale >= LARGE_FONT_SCALE
    val content: @Composable () -> Unit = {
        if (showWeek) {
            LegendItem(stringResource(R.string.statistics_last_7_days)) {
                Canvas(Modifier.size(StatisticsChartDefaults.SwatchWidth, StatisticsChartDefaults.SwatchHeight)) {
                    drawRoundRect(band, cornerRadius = CornerRadius(StatisticsChartDefaults.SWATCH_RADIUS * density))
                    if (chart.weekLine != null) {
                        val y = size.height / 2
                        drawLine(weekColor, Offset(0f, y), Offset(size.width, y), strokeWidth = StatisticsChartDefaults.LINE_WIDTH * density)
                    }
                }
            }
        }
        LegendItem(rangeLabel(report.range)) {
            Canvas(Modifier.size(StatisticsChartDefaults.SwatchWidth, StatisticsChartDefaults.SwatchHeight)) {
                val y = size.height / 2
                val dashes = PathEffect.dashPathEffect(
                    floatArrayOf(StatisticsChartDefaults.DASH_ON * density, StatisticsChartDefaults.DASH_OFF * density),
                )
                drawLine(rangeColor, Offset(0f, y), Offset(size.width, y), strokeWidth = StatisticsChartDefaults.LINE_WIDTH * density, pathEffect = dashes)
            }
        }
        if (showBins) {
            Text(
                pluralStringResource(R.plurals.statistics_bar_days, chart.binDays, chart.binDays),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

/** A 20 × 12 swatch, 6, then the label (labelSmall onSurfaceVariant). */
@Composable
private fun LegendItem(label: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        swatch()
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
