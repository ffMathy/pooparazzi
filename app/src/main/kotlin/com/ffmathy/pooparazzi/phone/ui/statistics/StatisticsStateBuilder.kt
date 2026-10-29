package com.ffmathy.pooparazzi.phone.ui.statistics

import com.ffmathy.pooparazzi.core.domain.StatsMetric
import com.ffmathy.pooparazzi.core.domain.StatsRange
import com.ffmathy.pooparazzi.core.domain.chart
import com.ffmathy.pooparazzi.core.domain.localDate
import com.ffmathy.pooparazzi.core.domain.statsReport
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.core.model.Entry
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
        return StatisticsUiState(zone, localDate(now, zone), metric, report, report?.chart(metric), babies, babyId)
    }
}
