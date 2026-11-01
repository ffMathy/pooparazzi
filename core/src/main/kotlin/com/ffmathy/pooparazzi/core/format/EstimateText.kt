package com.ffmathy.pooparazzi.core.format

import android.content.Context
import android.content.res.Resources
import androidx.annotation.StringRes
import com.ffmathy.pooparazzi.core.R
import com.ffmathy.pooparazzi.core.domain.Expected

/** Which estimate a line words: they share [EstimateText]'s rules and differ only in their words. */
enum class Estimate(@StringRes val inId: Int, @StringRes val soonId: Int, @StringRes val agoId: Int) {
    /** Under Feed: "Next in 55m", "Next soon", "Due 24m ago". */
    FEED(R.string.estimate_feed_in, R.string.estimate_feed_soon, R.string.estimate_feed_ago),

    /** Under Diaper: "Poop in 2h 10m", "Poop soon", "Poop due 24m ago". */
    POOP(R.string.estimate_poop_in, R.string.estimate_poop_soon, R.string.estimate_poop_ago),

    /** Awake, on its own: "Next sleep in 50m", "Next sleep soon", "Next sleep 24m ago". */
    NEXT_SLEEP(R.string.forecast_next_sleep_in, R.string.forecast_next_sleep_soon, R.string.forecast_next_sleep_ago),

    /** Asleep, on its own: "Wakes in 2h 15m", "Wakes soon", "Wake-up 24m ago". */
    WAKE(R.string.forecast_wakes_in, R.string.forecast_wakes_soon, R.string.forecast_wakes_ago),

    /** Under "Start sleep" or "Woke up", which say which: "in 50m", "soon", "24m ago". */
    SLEEP_SHORT(R.string.estimate_in, R.string.forecast_soon_word, R.string.rel_ago_format),
}

/**
 * An estimate relative to now, on every surface that shows one (the phone's widgets, the watch's Tile and app), as the
 * phone's Home words it (V1_ADDITIONS §9): the time until it rounded up to five minutes ("in 55m": a week's typical
 * gap, not a minute-exact promise), "soon" under five minutes, and how long ago once it is a minute past
 * ([Expected.remainingMillis], [Expected.agoMillis]). A surface that is not redrawn every minute redraws at
 * [Expected.nextTextChange].
 */
class EstimateText(private val res: Resources, private val duration: DurationFormatter) {
    constructor(context: Context) : this(context.resources, DurationFormatter.from(context))

    /** [kind]'s line at [now]: "Next in 55m", "Next soon" or "Due 24m ago". */
    fun format(kind: Estimate, expected: Expected, now: Long): String {
        expected.agoMillis(now)?.let { return res.getString(kind.agoId, duration.format(it)) }
        expected.remainingMillis(now)?.let { return res.getString(kind.inId, duration.format(it)) }
        return res.getString(kind.soonId)
    }

    /** [kind]'s three words, for a surface that builds the line itself (the Tile's dynamic expression). */
    fun templates(kind: Estimate): Triple<String, String, String> =
        Triple(res.getString(kind.inId), res.getString(kind.soonId), res.getString(kind.agoId))
}
