package com.ffmathy.pooparazzi.wear.ui

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.ffmathy.pooparazzi.core.format.Formatters
import com.ffmathy.pooparazzi.core.format.Labels
import com.ffmathy.pooparazzi.core.model.BreastSide
import com.ffmathy.pooparazzi.core.model.DiaperKind
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.model.clockDurationMillis
import com.ffmathy.pooparazzi.core.model.diaperKind
import com.ffmathy.pooparazzi.core.model.isBreastfeed
import com.ffmathy.pooparazzi.wear.R
import java.util.Locale
import com.ffmathy.pooparazzi.core.R as CoreR

/*
 * Copy built from :core's shared strings and formatters (DESIGN_SPEC §7, V1_ADDITIONS §5). Spoken forms read
 * durations in words (§9.5), never arrows, "+" or dashes, and are put together with resource templates
 * ([SpokenText]), so word order and punctuation belong to each language.
 */

/** The feed type word ("Formula"), or "Feed" for a feed with no type; [compact] adds the soft hyphen for tight places. */
@Composable
@ReadOnlyComposable
fun feedTypeLabel(entry: Entry, compact: Boolean = false): String =
    stringResource(Labels.feedType(entry.feedType, compact))

/**
 * The feed type for a spoken sentence, with the breast order when one was recorded ("Breast, left then right"), or
 * null for a feed logged without a type, so TalkBack reads "Last feed, 2 hours ago" rather than "Last feed, feed,
 * 2 hours ago" (as the phone does).
 */
@Composable
@ReadOnlyComposable
fun spokenFeedType(entry: Entry): String? {
    val type = entry.feedType?.let { stringResource(Labels.feedType(it)) } ?: return null
    val side = feedSide(entry) ?: return type
    return stringResource(R.string.spoken_pair, type, stringResource(SpokenLabels.side(side)))
}

/** The breast order of a breastfeed, or null (none recorded, or not a breastfeed). */
fun feedSide(entry: Entry): BreastSide? = entry.breastSide?.takeIf { entry.isBreastfeed }

/**
 * The words TalkBack reads where the screen shows a symbol (DESIGN_SPEC §9.4): "Left then right" for "Left → Right",
 * "Pee and poop" for "Pee + poop".
 */
object SpokenLabels {
    @StringRes
    fun side(side: BreastSide): Int = when (side) {
        BreastSide.LEFT -> CoreR.string.side_left
        BreastSide.RIGHT -> CoreR.string.side_right
        BreastSide.LEFT_THEN_RIGHT -> R.string.spoken_side_left_then_right
        BreastSide.RIGHT_THEN_LEFT -> R.string.spoken_side_right_then_left
    }

    @StringRes
    fun diaperKind(kind: DiaperKind): Int = when (kind) {
        DiaperKind.PEE -> CoreR.string.diaper_pee
        DiaperKind.POOP -> CoreR.string.diaper_poop
        DiaperKind.BOTH -> R.string.spoken_pee_and_poop
        DiaperKind.PLAIN -> CoreR.string.kind_diaper
    }
}

/** Spoken sentences from the watch's resource templates (V1_ADDITIONS §5: no sentence glued together in Kotlin). */
class SpokenText(private val res: Resources) {
    /** "{first}, {second}". */
    fun pair(first: String, second: String): String = res.getString(R.string.spoken_pair, first, second)

    /** Facts read in a row: "Last feed, formula, 2 hours 10 minutes ago, at 12:22". */
    fun all(facts: List<String>): String = facts.reduce(::pair)

    /** "Asleep, 1 hour 7 minutes, since 02:05". */
    fun running(status: String, duration: String, since: String): String =
        res.getString(R.string.spoken_running, status, duration, since)
}

/** [SpokenText] in the composition's language (follows configuration changes, as stringResource does). */
@Composable
@ReadOnlyComposable
fun spokenText(): SpokenText = SpokenText(LocalResources.current)

/** Spoken "{prefix}, {detail}, 48 minutes ago, at 13:44". */
@Composable
@ReadOnlyComposable
fun Formatters.spokenAgo(prefix: String, detail: String?, fromMillis: Long, now: Long): String =
    spokenText().all(
        listOfNotNull(
            prefix,
            detail?.lowercase(clock.locale),
            relative.agoSpoken(clockDurationMillis(fromMillis, now)),
            relative.at(fromMillis, zone),
        ),
    )

/** Spoken "{status}, 1 hour 7 minutes, since 02:05" (a running sleep read on its own). */
@Composable
@ReadOnlyComposable
fun Formatters.spokenRunning(status: String, fromMillis: Long, now: Long): String =
    spokenText().running(status, duration.spoken(clockDurationMillis(fromMillis, now)), relative.since(fromMillis, zone))

/** Spoken offset choice: "5 minutes earlier, 14:27" (§9.5). */
@Composable
@ReadOnlyComposable
fun Formatters.spokenOffset(minutes: Int, atMillis: Long): String =
    spokenText().pair(relative.offsetSpoken(minutes), clock.format(atMillis, zone))

/** "Asleep since 02:05" (shown and read). */
@Composable
@ReadOnlyComposable
fun Formatters.statusSince(status: String, millis: Long): String =
    stringResource(R.string.status_since, status, relative.since(millis, zone))

/**
 * Keeps a two-side label ("Left → Right", "Venstre → Højre") on one line: a wrapping title then breaks after the
 * preceding "·" instead of leaving the arrow at a line end ("Breast · Left →" / "Right").
 */
fun String.keepSidesTogether(): String = replace(" $SIDE_ARROW ", " $SIDE_ARROW ")

private const val SIDE_ARROW = "→"

/** Lower-cases the first letter for use inside a spoken sentence. */
fun String.inSentence(locale: Locale): String = replaceFirstChar { it.lowercase(locale) }
