package com.ffmathy.xaternity.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.ffmathy.xaternity.core.format.DOT_SEPARATOR
import com.ffmathy.xaternity.core.format.Formatters
import com.ffmathy.xaternity.core.format.Labels
import com.ffmathy.xaternity.core.model.DiaperKind
import com.ffmathy.xaternity.core.model.Entry
import com.ffmathy.xaternity.core.model.EntryKind
import com.ffmathy.xaternity.core.model.FeedType
import com.ffmathy.xaternity.core.model.clockDurationMillis
import com.ffmathy.xaternity.core.model.diaperKind
import java.util.Locale
import com.ffmathy.xaternity.core.R as CoreR

/*
 * Copy built from :core's shared strings and formatters (DESIGN_SPEC §7, V1_ADDITIONS §5). Spoken forms read
 * durations in words (§9.5) and join facts with ", ".
 */

/** "Fed at 12:22", "Formula at 14:32", "Pee + poop at 14:32" (receipt_at_time). */
@Composable
@ReadOnlyComposable
fun labelAtTime(label: String, millis: Long, f: Formatters): String =
    stringResource(CoreR.string.receipt_at_time, label, f.clock.format(millis, f.zone))

/** The feed type word ("Formula"); [compact] adds the soft hyphen for tight places. */
@Composable
@ReadOnlyComposable
fun feedTypeLabel(entry: Entry, compact: Boolean = false): String =
    stringResource(Labels.feedType(entry.feedType ?: FeedType.FORMULA, compact))

/** Receipts and History: "Pee", "Poop", "Pee + poop". */
@Composable
@ReadOnlyComposable
fun diaperLabel(entry: Entry): String = stringResource(Labels.diaperKind(entry.diaperKind ?: DiaperKind.PEE))

/** Status surfaces (cards): "Pee", "Poop", "Both". */
@Composable
@ReadOnlyComposable
fun diaperStatusLabel(entry: Entry): String = stringResource(Labels.diaperKindStatus(entry.diaperKind ?: DiaperKind.PEE))

/** "48m ago · 13:44" (relative + absolute, §7). */
fun Formatters.agoAndClock(fromMillis: Long, now: Long): String =
    relative.ago(clockDurationMillis(fromMillis, now)) + DOT_SEPARATOR + clock.format(fromMillis, zone)

/** "1h 05m · since 13:27". */
fun Formatters.durationAndSince(fromMillis: Long, now: Long): String =
    duration.format(clockDurationMillis(fromMillis, now)) + DOT_SEPARATOR + relative.since(fromMillis, zone)

/** Spoken "{prefix}, {detail}, 48 minutes ago, at 13:44". */
fun Formatters.spokenAgo(prefix: String, detail: String?, fromMillis: Long, now: Long): String =
    listOfNotNull(
        prefix,
        detail?.lowercase(clock.locale),
        relative.agoSpoken(clockDurationMillis(fromMillis, now)),
        relative.at(fromMillis, zone),
    ).joinToString(", ")

/** Spoken "{prefix} 1 hour 5 minutes, since 13:27". */
fun Formatters.spokenDurationSince(prefix: String, fromMillis: Long, now: Long): String =
    prefix + " " + duration.spoken(clockDurationMillis(fromMillis, now)) + ", " + relative.since(fromMillis, zone)

/** Spoken offset choice: "5 minutes earlier, 14:27" (§9.5). */
fun Formatters.spokenOffset(minutes: Int, atMillis: Long): String =
    relative.offsetSpoken(minutes) + ", " + clock.format(atMillis, zone)

/** The title word of an entry for Adjust and Today rows ("Sleep", "Breast", "Formula", "Pee + poop"). */
@Composable
@ReadOnlyComposable
fun entryWord(entry: Entry, compact: Boolean = false): String = when (entry.kind) {
    EntryKind.SLEEP -> stringResource(CoreR.string.kind_sleep)
    EntryKind.FEED -> feedTypeLabel(entry, compact)
    EntryKind.DIAPER -> diaperLabel(entry)
}

/**
 * Keeps a two-side label ("Left → Right", "Venstre → Højre") on one line: a wrapping title then breaks after the
 * preceding "·" instead of leaving the arrow at a line end ("Breast · Left →" / "Right").
 */
fun String.keepSidesTogether(): String = replace(" $SIDE_ARROW ", "\u00A0$SIDE_ARROW\u00A0")

private const val SIDE_ARROW = "→"

/** Lower-cases the first letter for use inside a spoken sentence. */
fun String.inSentence(locale: Locale): String = replaceFirstChar { it.lowercase(locale) }
