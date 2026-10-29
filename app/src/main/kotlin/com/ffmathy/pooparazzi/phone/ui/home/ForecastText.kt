package com.ffmathy.pooparazzi.phone.ui.home

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ffmathy.pooparazzi.core.domain.Expected
import com.ffmathy.pooparazzi.core.format.Formatters
import com.ffmathy.pooparazzi.phone.R
import java.time.ZoneId

/** A Home button's second line (V1_ADDITIONS §9): "in 1h 05m" and, for TalkBack, "next feed in about 1 hour 5 minutes, around 15:35". */
internal class ForecastText(val shown: String, val spoken: String)

/** What the forecast line of each Home button says. */
internal enum class ForecastKind(
    @param:StringRes val inFormat: Int,
    @param:StringRes val soon: Int,
    @param:StringRes val spokenFormat: Int,
    @param:StringRes val spokenSoon: Int,
) {
    FEED(R.string.forecast_in, R.string.forecast_soon, R.string.forecast_spoken_feed, R.string.forecast_spoken_feed_soon),

    /** Under Diaper: only poop is worth predicting, so it is named ("poop in 3h"). */
    POOP(R.string.forecast_poop_in, R.string.forecast_poop_soon, R.string.forecast_spoken_poop, R.string.forecast_spoken_poop_soon),

    /** Under Start sleep. */
    SLEEP(R.string.forecast_in, R.string.forecast_soon, R.string.forecast_spoken_sleep, R.string.forecast_spoken_sleep_soon),

    /** Under Woke up, while a sleep runs. */
    WAKE(R.string.forecast_in, R.string.forecast_soon, R.string.forecast_spoken_wake, R.string.forecast_spoken_wake_soon),
}

/** The forecast line for [expected], or null when the week predicts nothing for it. */
@Composable
internal fun forecastText(expected: Expected?, kind: ForecastKind, now: Long, zone: ZoneId, formatters: Formatters): ForecastText? {
    expected ?: return null
    val remaining = expected.remainingMillis(now) ?: return ForecastText(stringResource(kind.soon), stringResource(kind.spokenSoon))
    return ForecastText(
        shown = stringResource(kind.inFormat, formatters.duration.format(remaining)),
        spoken = stringResource(kind.spokenFormat, formatters.duration.spoken(remaining), formatters.clock.format(expected.atMillis, zone)),
    )
}
