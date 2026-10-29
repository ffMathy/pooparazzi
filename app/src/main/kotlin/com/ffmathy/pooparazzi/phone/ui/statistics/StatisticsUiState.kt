package com.ffmathy.pooparazzi.phone.ui.statistics

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.ffmathy.pooparazzi.core.domain.StatsChart
import com.ffmathy.pooparazzi.core.domain.StatsMetric
import com.ffmathy.pooparazzi.core.domain.StatsRange
import com.ffmathy.pooparazzi.core.domain.StatsReport
import java.time.LocalDate
import java.time.ZoneId

/**
 * Everything Statistics (P8, V1_ADDITIONS §8) shows, as data. Text is formatted by the screen with the current
 * locale's resources, so the same state renders in English and Danish. There is no `now`: the numbers only change at
 * midnight or when an entry changes, so a minute tick builds an equal state and nothing recomposes.
 *
 * @property today the day containing now; every window ends the day before.
 * @property metric the open row, whose chart is shown.
 * @property report the last 7 days, the last 30 days and the comparison range; null until a whole day has been
 * logged (the empty state).
 * @property chart [report]'s chart of [metric].
 */
@Immutable
data class StatisticsUiState(
    val zone: ZoneId,
    val today: LocalDate,
    val metric: StatsMetric,
    val report: StatsReport?,
    val chart: StatsChart?,
) {
    val isEmpty: Boolean get() = report == null
}

/** Everything the parent can do on Statistics. Defaults do nothing, so previews and screenshots can pass [None]. */
@Stable
interface StatisticsActions {
    fun onBack() {}

    /** A row tap: open that row's chart (one row is open at a time; tapping the open row changes nothing). */
    fun onMetric(metric: StatsMetric) {}

    /** A choice in the range menu, or OK in the date dialog. */
    fun onRange(range: StatsRange) {}

    object None : StatisticsActions
}
