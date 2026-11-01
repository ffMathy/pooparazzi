package com.ffmathy.pooparazzi.phone.ui.home

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ffmathy.pooparazzi.core.domain.Expected
import com.ffmathy.pooparazzi.core.format.Formatters
import com.ffmathy.pooparazzi.phone.R
import java.time.ZoneId
import com.ffmathy.pooparazzi.core.R as CoreR

/**
 * A Home button's second line (V1_ADDITIONS §9), after the `upcoming` icon that says it is what comes next, so it is
 * the time alone, as on the 4×2 widget: "1h 05m", "soon", and once the estimate has passed "24m ago". TalkBack says it
 * in full: "next feed in about 1 hour 5 minutes, around 15:35".
 */
internal class ForecastText(val shown: String, val spoken: String)

/** What the forecast line of each Home button says. */
internal enum class ForecastKind(
    @param:StringRes val spokenFormat: Int,
    @param:StringRes val spokenSoon: Int,
    @param:StringRes val spokenAgoFormat: Int,
) {
    FEED(R.string.forecast_spoken_feed, R.string.forecast_spoken_feed_soon, R.string.forecast_spoken_feed_ago),

    /** Under Diaper: only poop is worth predicting, so TalkBack names it ("next poop in about 3 hours 10 minutes"). */
    POOP(R.string.forecast_spoken_poop, R.string.forecast_spoken_poop_soon, R.string.forecast_spoken_poop_ago),

    /** Under Start sleep. */
    SLEEP(R.string.forecast_spoken_sleep, R.string.forecast_spoken_sleep_soon, R.string.forecast_spoken_sleep_ago),

    /** Under Woke up, while a sleep runs. */
    WAKE(R.string.forecast_spoken_wake, R.string.forecast_spoken_wake_soon, R.string.forecast_spoken_wake_ago),
}

/** The forecast line for [expected], or null when the week predicts nothing for it. */
@Composable
internal fun forecastText(expected: Expected?, kind: ForecastKind, now: Long, zone: ZoneId, formatters: Formatters): ForecastText? {
    expected ?: return null
    val clock = formatters.clock.format(expected.atMillis, zone)
    expected.agoMillis(now)?.let { ago ->
        return ForecastText(
            shown = stringResource(CoreR.string.rel_ago_format, formatters.duration.format(ago)),
            spoken = stringResource(kind.spokenAgoFormat, formatters.duration.spoken(ago), clock),
        )
    }
    val remaining = expected.remainingMillis(now) ?: return ForecastText(stringResource(CoreR.string.estimate_soon), stringResource(kind.spokenSoon))
    return ForecastText(
        shown = stringResource(R.string.forecast_time, formatters.duration.format(remaining)),
        spoken = stringResource(kind.spokenFormat, formatters.duration.spoken(remaining), clock),
    )
}
