package com.ffmathy.xaternity.phone.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.ffmathy.xaternity.core.format.DOT_SEPARATOR
import com.ffmathy.xaternity.core.format.Formatters
import com.ffmathy.xaternity.core.format.Labels
import com.ffmathy.xaternity.core.model.DiaperKind
import com.ffmathy.xaternity.core.model.Entry
import com.ffmathy.xaternity.core.model.EntryKind
import com.ffmathy.xaternity.core.model.FeedType
import com.ffmathy.xaternity.core.model.diaperKind
import com.ffmathy.xaternity.core.ui.icons.XIcons
import com.ffmathy.xaternity.core.ui.icons.rememberBothIcon
import com.ffmathy.xaternity.core.ui.tokens.EventPalette
import com.ffmathy.xaternity.phone.R
import com.ffmathy.xaternity.core.R as CoreR

/** An icon with its colour; [tint] is `Color.Unspecified` for the two-tone `both`. */
data class TintedIcon(val icon: ImageVector, val tint: Color)

/** The kind glyph of a diaper: `pee`, `poop` or the two-tone `both` (never the generic `diaper`, DESIGN_SPEC §3.3.1). */
@Composable
fun diaperIcon(kind: DiaperKind, events: EventPalette): TintedIcon = when (kind) {
    DiaperKind.PEE -> TintedIcon(XIcons.pee, events.pee.strong)
    DiaperKind.POOP -> TintedIcon(XIcons.poop, events.poop.strong)
    DiaperKind.BOTH -> TintedIcon(rememberBothIcon(events.pee.strong, events.poop.strong), Color.Unspecified)
}

/** The icon of an entry, in its event's strong colour. */
@Composable
fun entryIcon(entry: Entry, events: EventPalette): TintedIcon = when (entry.kind) {
    EntryKind.SLEEP -> TintedIcon(XIcons.sleep, events.sleep.strong)
    EntryKind.FEED -> TintedIcon(XIcons.forFeedType(entry.feedType ?: FeedType.FORMULA), events.feed.strong)
    EntryKind.DIAPER -> diaperIcon(entry.diaperKind ?: DiaperKind.PEE, events)
}

/** "Formula" (with a soft hyphen in Danish where [compact]); "Breast"; "Food". */
@Composable
@ReadOnlyComposable
fun feedTypeLabel(type: FeedType, compact: Boolean = false): String = stringResource(Labels.feedType(type, compact))

/** "Pee", "Poop", "Pee + poop" (receipts). */
@Composable
@ReadOnlyComposable
fun diaperLabel(kind: DiaperKind): String = stringResource(Labels.diaperKind(kind))

/** "Pee", "Poop", "Both" (status surfaces). */
@Composable
@ReadOnlyComposable
fun diaperStatusLabel(kind: DiaperKind): String = stringResource(Labels.diaperKindStatus(kind))

/** "Diaper · 14:32" / "Feed · 14:32". */
@Composable
@ReadOnlyComposable
fun titleWithTime(@androidx.annotation.StringRes kind: Int, time: String): String =
    stringResource(R.string.join_dot, stringResource(kind), time)

/** The label of an entry for TalkBack: "formula", "pee", "pee + poop" (lower case inside a sentence). */
@Composable
@ReadOnlyComposable
fun spokenEntryLabel(entry: Entry, formatters: Formatters): String {
    val label = when (entry.kind) {
        EntryKind.SLEEP -> stringResource(CoreR.string.kind_sleep)
        EntryKind.FEED -> feedTypeLabel(entry.feedType ?: FeedType.FORMULA)
        EntryKind.DIAPER -> diaperLabel(entry.diaperKind ?: DiaperKind.PEE)
    }
    return label.lowercase(formatters.clock.locale)
}

/** " · 13:44" style suffix. */
fun dotted(text: String): String = DOT_SEPARATOR + text
