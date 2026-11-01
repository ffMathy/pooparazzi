package com.ffmathy.pooparazzi.phone.ui.preview

import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.phone.ui.history.HistoryStateBuilder
import com.ffmathy.pooparazzi.phone.ui.history.HistoryUiState
import java.time.LocalDate

/** History states from the reference scenario (V1_ADDITIONS §6), built through [HistoryStateBuilder]. */
object HistorySamples {
    private val zone = SampleData.ZONE

    /** Today at 14:32: ribbon, 5 feeds · 4 pee · 2 poop · 7h 57m, 15 rows. */
    fun today(): HistoryUiState = build(SampleData.scenario(zone), SampleData.NOW_1432)

    /** The night snapshot at 03:12: the 02:05 sleep still running. */
    fun night(): HistoryUiState =
        build(SampleData.scenarioAt0312(zone), SampleData.NOW_0312)

    /** Sun 25 Oct, seen at 14:32 on Monday: the evening's 22:40 sleep runs past midnight. */
    fun yesterday(): HistoryUiState = build(SampleData.scenario(zone), SampleData.NOW_1432, day = SampleData.YESTERDAY)

    /** [today] with twins (Noa current): "Noa" under the title. */
    fun twins(): HistoryUiState = today().copy(
        babies = SampleData.twinSettings.babies,
        babyId = SampleData.twinSettings.selectedBaby.id,
    )

    private fun build(
        entries: List<Entry>,
        now: Long,
        day: LocalDate? = null,
    ): HistoryUiState = HistoryStateBuilder.build(entries, now, zone, day)
}
