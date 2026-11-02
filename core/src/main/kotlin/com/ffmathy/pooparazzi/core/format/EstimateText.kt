package com.ffmathy.pooparazzi.core.format

import android.content.Context
import android.content.res.Resources
import androidx.annotation.StringRes
import com.ffmathy.pooparazzi.core.R
import com.ffmathy.pooparazzi.core.domain.Expected

/** Which estimate a line words: they share [EstimateText]'s rules and differ only in their words. */
enum class Estimate(@StringRes val inId: Int, @StringRes val soonId: Int, @StringRes val nowId: Int) {
    /** Under Feed and under "Start sleep" / "Woke up" (which say what is next): "Next in 55m", "Next soon", "Due now". */
    NEXT(R.string.estimate_next_in, R.string.estimate_next_soon, R.string.estimate_next_now),

    /** Under Diaper (only poop is predicted): "Next poop in 3h 10m", "Next poop soon", "Poop due now". */
    POOP(R.string.estimate_poop_in, R.string.estimate_poop_soon, R.string.estimate_poop_now),
}

/**
 * An estimate relative to now, on every surface that shows one (the phone's widgets, the watch's Tile and app), as the
 * phone's Home words it (V1_ADDITIONS §9): the time until it rounded up to five minutes ("in 55m": a week's typical
 * gap, not a minute-exact promise), "soon" under five minutes, and "now" once it is due: never a time in the past
 * ([Expected.remainingMillis], [Expected.isDue]).
 */
class EstimateText(private val res: Resources, private val duration: DurationFormatter) {
    constructor(context: Context) : this(context.resources, DurationFormatter.from(context))

    /** [kind]'s line at [now]: "Next in 55m", "Next soon" or "Due now". */
    fun format(kind: Estimate, expected: Expected, now: Long): String = format(kind.inId, kind.soonId, kind.nowId, expected, now)

    /** The same rules in other words ([inId] "feed in %1$s", [soonId] "feed soon", [nowId] "feed due now"). */
    fun format(@StringRes inId: Int, @StringRes soonId: Int, @StringRes nowId: Int, expected: Expected, now: Long): String {
        if (expected.isDue(now)) return res.getString(nowId)
        expected.remainingMillis(now)?.let { return res.getString(inId, duration.format(it)) }
        return res.getString(soonId)
    }

    /**
     * The line after an `upcoming` icon, which already says "next" (Home's buttons, the 4×2 widget, the watch's Main
     * and Tile): the time alone, "55m", "soon", or "now" once it is due.
     */
    fun formatShort(expected: Expected, now: Long): String =
        format(R.string.estimate_time, R.string.estimate_soon, R.string.estimate_now, expected, now)

    /** [formatShort]'s three words, for a surface that builds the line itself (the Tile's dynamic expression). */
    fun shortTemplates(): Triple<String, String, String> =
        Triple(res.getString(R.string.estimate_time), res.getString(R.string.estimate_soon), res.getString(R.string.estimate_now))

    /** [kind]'s three words, for a surface that builds the line itself (the Tile's dynamic expression). */
    fun templates(kind: Estimate): Triple<String, String, String> =
        Triple(res.getString(kind.inId), res.getString(kind.soonId), res.getString(kind.nowId))
}
