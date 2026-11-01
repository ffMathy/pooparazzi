package com.ffmathy.pooparazzi.wear.glance

import android.content.res.Resources
import androidx.compose.runtime.Immutable
import com.ffmathy.pooparazzi.core.domain.Expected
import com.ffmathy.pooparazzi.core.domain.Forecast
import com.ffmathy.pooparazzi.core.domain.Summary
import com.ffmathy.pooparazzi.core.domain.forecast
import com.ffmathy.pooparazzi.core.format.Formatters
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.wear.R
import java.time.ZoneId
import com.ffmathy.pooparazzi.core.R as CoreR

/**
 * What the watch shows at a glance, on the Tile and on the app's Main alike (DESIGN_SPEC §3.5.1, §3.6): one model, so
 * both always say the same thing. Domain values only; [GlanceText] words them, and each surface lays them out (the
 * Tile in ProtoLayout, Main in Compose).
 *
 * - the title line ([title]);
 * - the Feed row: the time since the last feed, then the expected next feed;
 * - the Diaper row: the time since the last diaper, then the expected next poop;
 * - the sleep toggle: "Woke up" while [runningSleep] runs, "Start sleep" otherwise, with the expected wake-up or next
 *   sleep under it.
 *
 * @property babyName the name the title shows (empty: unnamed, the title falls back to the last diaper).
 * @property forecast what the past week expects next (V1_ADDITIONS §9); [Forecast.NONE] when it cannot say.
 */
@Immutable
data class WatchGlance(
    val now: Long,
    val zone: ZoneId,
    val babyName: String,
    val lastFeed: Entry?,
    val lastDiaper: Entry?,
    val runningSleep: Entry?,
    val forecast: Forecast = Forecast.NONE,
) {
    /** Asleep, then the name, then the last diaper. */
    val title: GlanceTitle
        get() = when {
            runningSleep != null -> GlanceTitle.Asleep(runningSleep)
            babyName.isNotBlank() -> GlanceTitle.Name(babyName)
            lastDiaper != null -> GlanceTitle.LastDiaper(lastDiaper)
            else -> GlanceTitle.None
        }

    val asleep: Boolean get() = runningSleep != null

    /** The expected wake-up while asleep, the expected next sleep while awake. */
    val nextSleep: Expected? get() = forecast.sleep

    val nextFeed: Expected? get() = forecast.feed

    val nextPoop: Expected? get() = forecast.poop

    companion object {
        /** The glance for [summary] (the current baby's entries), with the forecast the past week gives. */
        fun from(summary: Summary, babyName: String): WatchGlance = WatchGlance(
            now = summary.now,
            zone = summary.zone,
            babyName = babyName.trim(),
            lastFeed = summary.lastFeed,
            lastDiaper = summary.lastDiaper,
            runningSleep = summary.runningSleep,
            forecast = forecast(summary),
        )
    }
}

/** The title line of [WatchGlance.title]. */
sealed interface GlanceTitle {
    /** A sleep runs: "Asleep 1h 07m". */
    data class Asleep(val sleep: Entry) : GlanceTitle

    /** The baby's name. */
    data class Name(val name: String) : GlanceTitle

    /** No name set: the last diaper, kind first ("Pee 48m ago"). */
    data class LastDiaper(val diaper: Entry) : GlanceTitle

    /** Nothing to say yet (no name, no diapers). */
    data object None : GlanceTitle
}

/**
 * The words of a [WatchGlance]'s estimates, shared by the Tile and Main, and the same as on the phone's widget
 * (V1_ADDITIONS §9): a clock time, never a ticking "in 55m", so a surface that is not redrawn every minute stays right.
 */
class GlanceText(private val res: Resources, private val formatters: Formatters) {
    private fun time(expected: Expected): String = formatters.clock.format(expected.roundedAtMillis, formatters.zone)

    /**
     * In the sleep toggle, under "Woke up" or "Start sleep": "~05:25", the expected wake-up while asleep, the expected
     * next sleep while awake. The button's own word says which.
     */
    fun nextSleep(expected: Expected): String = res.getString(R.string.glance_sleep_estimate, time(expected))

    /** Under Feed: "Next ~15:30". */
    fun nextFeed(expected: Expected): String = res.getString(CoreR.string.forecast_feed_at, time(expected))

    /** Under Diaper: "Poop ~18:00". */
    fun nextPoop(expected: Expected): String = res.getString(CoreR.string.forecast_poop_at, time(expected))

    /** TalkBack: "expected to wake around 05:25" / "expected to sleep again around 15:20". */
    fun nextSleepSpoken(expected: Expected, asleep: Boolean): String =
        res.getString(if (asleep) CoreR.string.spoken_forecast_wake_at else CoreR.string.spoken_forecast_sleep_at, time(expected))

    /** TalkBack: "next feed expected around 15:30". */
    fun nextFeedSpoken(expected: Expected): String = res.getString(CoreR.string.spoken_forecast_feed_at, time(expected))

    /** TalkBack: "next poop expected around 18:00". */
    fun nextPoopSpoken(expected: Expected): String = res.getString(CoreR.string.spoken_forecast_poop_at, time(expected))
}
