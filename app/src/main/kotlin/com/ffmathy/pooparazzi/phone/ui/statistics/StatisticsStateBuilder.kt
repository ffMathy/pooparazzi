package com.ffmathy.pooparazzi.phone.ui.statistics

import com.ffmathy.pooparazzi.core.domain.StatsMetric
import com.ffmathy.pooparazzi.core.domain.StatsRange
import com.ffmathy.pooparazzi.core.domain.chart
import com.ffmathy.pooparazzi.core.domain.dailyPattern
import com.ffmathy.pooparazzi.core.domain.localDate
import com.ffmathy.pooparazzi.core.domain.statsReport
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.phone.ui.theme.NightRule
import java.time.ZoneId

/**
 * Builds [StatisticsUiState] from the log (pure: shared by the view model, previews, screenshots and tests). [entries]
 * are the current baby's ([babyId]) only.
 */
internal object StatisticsStateBuilder {
    fun build(
        entries: List<Entry>,
        now: Long,
        zone: ZoneId,
        metric: StatsMetric,
        range: StatsRange,
        babies: List<Baby> = listOf(Baby(Baby.FIRST_ID)),
        babyId: String = babies.first().id,
    ): StatisticsUiState {
        val report = statsReport(entries, now, zone, range)
        val pattern = report?.let {
            dailyPattern(entries, it.window.from, it.window.to, now, zone, NightRule.DEFAULT_NIGHT_START, NightRule.DEFAULT_NIGHT_END)
        }
        return StatisticsUiState(zone, localDate(now, zone), metric, report, report?.chart(metric), pattern, babies, babyId)
    }
}
