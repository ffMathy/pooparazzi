package com.ffmathy.pooparazzi.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.ffmathy.pooparazzi.core.format.DOT_SEPARATOR
import com.ffmathy.pooparazzi.core.format.DurationFormatter
import com.ffmathy.pooparazzi.core.format.Formatters
import com.ffmathy.pooparazzi.core.ui.icons.XIcon
import com.ffmathy.pooparazzi.core.ui.icons.XIcons
import com.ffmathy.pooparazzi.wear.R
import com.ffmathy.pooparazzi.wear.state.MAX_SHIFT_MINUTES
import com.ffmathy.pooparazzi.wear.theme.WatchAmbient
import com.ffmathy.pooparazzi.wear.theme.isAmbient
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import com.ffmathy.pooparazzi.core.R as CoreR

/*
 * The inline time (V1_ADDITIONS §2.2, §3.2): the last two items of Feed type, Side, Bottle and Diaper. A readout
 * ("Earlier?", then "14:22 · −10m") over three nudges, "−15m", "−5m" and "+5m", whose taps add up. Never later than
 * the Feed or Diaper tap, at most 12 hours back. Minute-exact changes are made after saving, from Logged (the Time
 * screen), or with TalkBack's "1 minute earlier / later" actions on the readout.
 */

/** The nudges, in minutes, in their order on screen. */
val TIME_NUDGES = listOf(-15, -5, 5)

/**
 * What the inline time measured once for this configuration: the readout's line count at its widest
 * ("23:59 · −12h 00m"), so it never grows as the offset does, and the nudges' columns (3, or 2 + 1 at large fonts).
 */
@Immutable
data class TimeNudgeLayout(val readoutLines: Int, val nudgeColumns: Int, val nudgeCell: Dp)

/** Measures the inline time for this screen ([TimeNudgeLayout]); call it before the list's builder. */
@Composable
fun rememberTimeNudgeLayout(f: Formatters): TimeNudgeLayout {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val width = listContentWidth() - READOUT_ICON_INSET
    val time = MaterialTheme.typography.titleMedium
    val label = MaterialTheme.typography.labelMedium
    val earlier = stringResource(CoreR.string.action_earlier_question)
    val widest = readoutText(f.clock.format(widestTimeMillis(f), f.zone), offsetLabel(f, -MAX_SHIFT_MINUTES), time, label)
    val readoutLines = remember(widest, earlier, density, configuration.fontScale, configuration.screenWidthDp) {
        val constraints = with(density) { Constraints(maxWidth = width.roundToPx()) }
        maxOf(
            measurer.measure(widest, label, constraints = constraints, density = density).lineCount,
            measurer.measure(earlier, label, constraints = constraints, density = density).lineCount,
        ).coerceIn(1, READOUT_MAX_LINES)
    }
    val columns = rememberGridColumns(TIME_NUDGES.map(f.duration::offset), StepLabelStyle, chrome = STEP_PADDING * 2, max = TIME_NUDGES.size)
    return TimeNudgeLayout(readoutLines, columns, gridCellWidth(listContentWidth(), columns))
}

/**
 * The inline time's two items, `timeReadout` and the `timeNudges` row(s), at the end of a picker list.
 *
 * @param offsetMinutes the offset from the Feed or Diaper tap (0 to −[MAX_SHIFT_MINUTES]); [at] the time it gives.
 * @param eventColor the time's colour: FEED strong on Feed type, Side and Bottle; DIAPER text on Diaper.
 * @param enabled false during the screen's cooldowns; the nudges then show [alpha].
 * @param onShift a nudge or a TalkBack action: minutes to add (negative is earlier).
 */
fun TransformingLazyColumnScope.timeNudgeItems(
    offsetMinutes: Int,
    at: Long,
    f: Formatters,
    eventColor: Color,
    layout: TimeNudgeLayout,
    enabled: Boolean,
    alpha: Float,
    onShift: (Int) -> Unit,
    spec: TransformationSpec,
) {
    item(key = "timeReadout") {
        TimeReadout(offsetMinutes, at, f, eventColor, layout.readoutLines, onShift, Modifier.transformedHeight(this, spec))
    }
    choiceGridItems("timeNudges", TIME_NUDGES.size, layout.nudgeColumns, layout.nudgeCell, spec) { index, modifier ->
        val minutes = TIME_NUDGES[index]
        val possible = if (minutes > 0) offsetMinutes < 0 else offsetMinutes > -MAX_SHIFT_MINUTES
        StepButton(
            label = f.duration.offset(minutes),
            spoken = f.relative.offsetSpoken(minutes),
            enabled = enabled && possible,
            onClick = { onShift(minutes) },
            modifier = modifier.alpha(alpha),
            width = null,
        )
    }
}

/**
 * "Earlier?" (no offset yet), then "14:22 · −10m": the time in the event colour, the offset after it. As tall as its
 * widest text from the start ([lines]). Read as "Time, 14:32" or "10 minutes earlier, 14:22", a polite live region,
 * with "1 minute earlier" / "1 minute later" actions for minute-exact changes.
 */
@Composable
private fun TimeReadout(
    offsetMinutes: Int,
    at: Long,
    f: Formatters,
    eventColor: Color,
    lines: Int,
    onShift: (Int) -> Unit,
    modifier: Modifier,
) {
    val ambient = isAmbient()
    val variant = if (ambient) WatchAmbient.content else MaterialTheme.colorScheme.onSurfaceVariant
    val strong = if (ambient) WatchAmbient.content else eventColor
    val label = MaterialTheme.typography.labelMedium
    val time = f.clock.format(at, f.zone)
    val text = if (offsetMinutes == 0) {
        AnnotatedString(stringResource(CoreR.string.action_earlier_question))
    } else {
        readoutText(time, offsetLabel(f, offsetMinutes), MaterialTheme.typography.titleMedium.copy(color = strong), label)
    }
    val spoken = if (offsetMinutes == 0) spokenText().pair(stringResource(R.string.spoken_time), time) else f.spokenOffset(offsetMinutes, at)
    val earlier = f.relative.offsetSpoken(-1)
    val later = f.relative.offsetSpoken(1)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 24.dp)
            .padding(top = 2.dp)
            .clearAndSetSemantics {
                contentDescription = spoken
                liveRegion = LiveRegionMode.Polite
                customActions = listOfNotNull(
                    CustomAccessibilityAction(earlier) { onShift(-1); true }.takeIf { offsetMinutes > -MAX_SHIFT_MINUTES },
                    CustomAccessibilityAction(later) { onShift(1); true }.takeIf { offsetMinutes < 0 },
                )
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        XIcon(XIcons.clock, null, tint = strong, size = READOUT_ICON)
        Spacer(Modifier.width(READOUT_ICON_GAP))
        Text(
            text,
            style = label,
            color = variant,
            textAlign = TextAlign.Center,
            minLines = lines,
            maxLines = READOUT_MAX_LINES,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** "14:22 · −10m": the time in [time]'s style, the rest in [label]'s. */
private fun readoutText(clock: String, offset: String, time: TextStyle, label: TextStyle): AnnotatedString = buildAnnotatedString {
    withStyle(time.toSpanStyle()) { append(clock) }
    withStyle(label.toSpanStyle()) { append(DOT_SEPARATOR + offset) }
}

/** A time as wide as the clock gets: two-digit hours (and PM in 12 h), for the readout's height. */
private fun widestTimeMillis(f: Formatters): Long =
    LocalDate.of(2026, 1, 1).atTime(LocalTime.of(22, 58)).atZone(f.zone).toInstant().toEpochMilli()

/** "−5m", "+45m"; from an hour on "−1h 15m" (the spoken form is [com.ffmathy.pooparazzi.core.format.RelativeFormatter.offsetSpoken]). */
internal fun offsetLabel(f: Formatters, minutes: Int): String {
    val size = abs(minutes)
    if (size < MINUTES_PER_HOUR) return f.duration.offset(minutes)
    val sign = if (minutes < 0) DurationFormatter.MINUS else "+"
    return sign + f.duration.format(size * MINUTE_MILLIS)
}

private const val MINUTES_PER_HOUR = 60
private const val MINUTE_MILLIS = 60_000L
private const val READOUT_MAX_LINES = 2
private val READOUT_ICON = 16.dp
private val READOUT_ICON_GAP = 6.dp

/** The readout's width its text cannot use: the clock icon and the gap after it. */
private val READOUT_ICON_INSET = READOUT_ICON + READOUT_ICON_GAP
