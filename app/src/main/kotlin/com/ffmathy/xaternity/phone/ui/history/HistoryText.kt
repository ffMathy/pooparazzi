package com.ffmathy.xaternity.phone.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.ffmathy.xaternity.core.domain.DayCounts
import com.ffmathy.xaternity.core.domain.localDate
import com.ffmathy.xaternity.core.format.Formatters
import com.ffmathy.xaternity.core.format.Labels
import com.ffmathy.xaternity.core.model.BreastSide
import com.ffmathy.xaternity.core.model.DiaperKind
import com.ffmathy.xaternity.core.model.Entry
import com.ffmathy.xaternity.core.model.EntryKind
import com.ffmathy.xaternity.core.model.FeedType
import com.ffmathy.xaternity.core.model.clockDurationMillis
import com.ffmathy.xaternity.core.model.diaperKind
import com.ffmathy.xaternity.core.model.isBreastfeed
import com.ffmathy.xaternity.core.model.isRunning
import com.ffmathy.xaternity.phone.R
import java.time.LocalDate
import java.time.ZoneId
import com.ffmathy.xaternity.core.R as CoreR

/** "Today", "Yesterday" or "Sat 24 Oct" (the list heading and the day chips). */
@Composable
@ReadOnlyComposable
internal fun relativeDayName(day: LocalDate, today: LocalDate, formatters: Formatters): String = when (day) {
    today -> stringResource(CoreR.string.nav_today)
    today.minusDays(1) -> stringResource(CoreR.string.nav_yesterday)
    else -> formatters.clock.dayMonth(day)
}

/** "Today · Mon 26 Oct", "Yesterday · Sun 25 Oct", or "Sat 24 Oct" for earlier days (the day switcher, edit sub-line). */
@Composable
@ReadOnlyComposable
internal fun dayLabel(day: LocalDate, today: LocalDate, formatters: Formatters): String {
    val date = formatters.clock.dayMonth(day)
    return when (day) {
        today -> stringResource(R.string.join_dot, stringResource(CoreR.string.nav_today), date)
        today.minusDays(1) -> stringResource(R.string.join_dot, stringResource(CoreR.string.nav_yesterday), date)
        else -> date
    }
}

/**
 * "Sleep · 47m", "Breast · Left → Right", "Breast · running", "Formula", "Pee + poop" (V1_ADDITIONS §4). Formula uses
 * the soft-hyphen form so Danish can break it in the list.
 */
@Composable
@ReadOnlyComposable
internal fun historyTitle(entry: Entry, now: Long, formatters: Formatters): String =
    if (entry.kind == EntryKind.FEED && !entry.isBreastfeed) {
        stringResource(Labels.feedType(entry.feedType ?: FeedType.FORMULA, compact = true))
    } else {
        formatters.labels.historyTitle(entry, formatters.duration, now)
    }

/** "12:40 – 13:27", "Sun 22:40 – 01:35", "12:00 – 12:18 · 18m", "since 14:32", "12:22", "05:41 · Yellow · Seedy". */
internal fun historySupporting(entry: Entry, day: LocalDate, zone: ZoneId, now: Long, formatters: Formatters): String =
    formatters.labels.historySupporting(entry, day, zone, now, formatters.clock, formatters.duration, formatters.relative)

/**
 * The row's TalkBack name: "Sleep, 47 minutes, 12:40 to 13:27", "Breast, left then right, 18 minutes, 12:00 to 12:18",
 * "Sleep, running, since 14:32", "Formula, 12:22", "Pee and poop, 05:41, Yellow, Seedy".
 */
@Composable
@ReadOnlyComposable
internal fun historySpoken(entry: Entry, day: LocalDate, zone: ZoneId, now: Long, formatters: Formatters): String {
    val clock = formatters.clock
    val parts = mutableListOf<String>()
    parts += spokenEntryName(entry)
    when {
        entry.isRunning -> {
            parts += stringResource(CoreR.string.status_running)
            parts += formatters.relative.since(entry.startMillis, zone)
        }
        entry.kind == EntryKind.SLEEP || entry.isBreastfeed -> {
            entry.breastSide?.let { parts += spokenSide(it) }
            val end = entry.endMillis ?: now
            parts += formatters.duration.spoken(entry.clockDurationMillis(now))
            val startDay = localDate(entry.startMillis, zone)
            val start = clock.format(entry.startMillis, zone).let { if (startDay != day) clock.weekdayShort(entry.startMillis, zone) + " " + it else it }
            parts += stringResource(R.string.spoken_range, start, clock.format(end, zone))
        }
        else -> {
            parts += clock.format(entry.startMillis, zone)
            if (entry.poop) {
                entry.poopColor?.let { parts += stringResource(Labels.poopColor(it)) }
                entry.poopTexture?.let { parts += stringResource(Labels.poopTexture(it)) }
            }
        }
    }
    return parts.joinToString(SPOKEN_SEPARATOR)
}

/** "Sleep", "Breast", "Formula", "Pee", "Pee and poop" (spoken; the "+" of "Pee + poop" reads badly). */
@Composable
@ReadOnlyComposable
internal fun spokenEntryName(entry: Entry): String = when (entry.kind) {
    EntryKind.SLEEP -> stringResource(CoreR.string.kind_sleep)
    EntryKind.FEED -> stringResource(Labels.feedType(entry.feedType ?: FeedType.FORMULA))
    EntryKind.DIAPER -> when (val kind = entry.diaperKind) {
        DiaperKind.BOTH -> stringResource(R.string.spoken_pee_and_poop)
        null -> stringResource(CoreR.string.kind_diaper)
        else -> stringResource(Labels.diaperKind(kind))
    }
}

/** "Left", "Right", "left then right" (the arrow of "Left → Right" is not read well). */
@Composable
@ReadOnlyComposable
internal fun spokenSide(side: BreastSide): String = when (side) {
    BreastSide.LEFT, BreastSide.RIGHT -> stringResource(Labels.side(side))
    BreastSide.LEFT_THEN_RIGHT -> stringResource(R.string.spoken_side_left_then_right)
    BreastSide.RIGHT_THEN_LEFT -> stringResource(R.string.spoken_side_right_then_left)
}

/**
 * The receipt label of a deleted entry: "Sleep", "Feed" (a breastfeed), "Formula", "Food", "Pee", "Pee + poop", used in
 * "Sleep deleted".
 */
@Composable
@ReadOnlyComposable
internal fun deletedLabel(entry: Entry): String = when (entry.kind) {
    EntryKind.SLEEP -> stringResource(CoreR.string.kind_sleep)
    EntryKind.FEED -> if (entry.isBreastfeed) {
        stringResource(CoreR.string.kind_feed)
    } else {
        stringResource(Labels.feedType(entry.feedType ?: FeedType.FORMULA, compact = true))
    }
    EntryKind.DIAPER -> entry.diaperKind?.let { stringResource(Labels.diaperKind(it)) } ?: stringResource(CoreR.string.kind_diaper)
}

/** "Today: 5 feeds, 4 pee, 2 poop, 7 hours 57 minutes sleep" (the ribbon's text alternative). */
@Composable
@ReadOnlyComposable
internal fun countsSpoken(dayName: String, counts: DayCounts, formatters: Formatters): String {
    val labels = formatters.labels
    return stringResource(
        R.string.spoken_today,
        dayName,
        counts.feeds.toString(),
        labels.feedsCaption(counts.feeds),
        counts.pee.toString(),
        labels.peeCaption(),
        counts.poop.toString(),
        labels.poopCaption(),
        formatters.duration.spoken(counts.sleepMinutes * MINUTE_MILLIS),
        labels.sleepCaption(),
    )
}

private const val SPOKEN_SEPARATOR = ", "
internal const val MINUTE_MILLIS = 60_000L
