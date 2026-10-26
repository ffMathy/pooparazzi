package com.ffmathy.xaternity.core.format

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.ZoneId

/** All formatters for one locale, 12/24 h setting and time zone. */
@Immutable
class Formatters(
    val duration: DurationFormatter,
    val clock: ClockFormatter,
    val relative: RelativeFormatter,
    val labels: LabelFormatter,
    val zone: ZoneId,
) {
    companion object {
        fun from(context: Context, zone: ZoneId = ZoneId.systemDefault()): Formatters {
            val duration = DurationFormatter.from(context)
            val clock = ClockFormatter.from(context)
            return Formatters(
                duration = duration,
                clock = clock,
                relative = RelativeFormatter(duration, clock, RelativePhrases.from(context)),
                labels = LabelFormatter(context),
                zone = zone,
            )
        }
    }
}

/**
 * [Formatters] for the current composition's locale and the device 12/24 h setting. Rebuilt when the
 * configuration (e.g. the app language) changes.
 */
@Composable
fun rememberFormatters(zone: ZoneId = ZoneId.systemDefault()): Formatters {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val is24Hour = ClockFormatter.uses24Hour(context, ClockFormatter.locale(context))
    return remember(context, configuration, configuration.locales, is24Hour, zone) { Formatters.from(context, zone) }
}
