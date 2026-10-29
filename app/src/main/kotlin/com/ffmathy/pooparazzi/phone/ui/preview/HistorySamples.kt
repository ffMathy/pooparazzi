package com.ffmathy.pooparazzi.phone.ui.preview

import com.ffmathy.pooparazzi.core.cloud.CloudStatus
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.sample.SampleData
import com.ffmathy.pooparazzi.core.sync.SyncStatus
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
        build(SampleData.scenarioAt0312(zone), SampleData.NOW_0312, SyncStatus.Synced(SampleData.SYNCED_0311))

    /** Sun 25 Oct, seen at 14:32 on Monday: the evening's 22:40 sleep runs past midnight. */
    fun yesterday(): HistoryUiState = build(SampleData.scenario(zone), SampleData.NOW_1432, day = SampleData.YESTERDAY)

    /** [today] with twins (Noa current): "Noa" under the title; signed in and backed up at 14:31 (the cloud icon). */
    fun twins(): HistoryUiState = today().copy(
        babies = SampleData.twinSettings.babies,
        babyId = SampleData.twinSettings.selectedBaby.id,
        cloud = CloudStatus.Synced(SampleData.SYNCED_1431),
    )

    /** [today] with the watch sync and the backup in the given states (the status icons, DESIGN_SPEC §4.5). */
    fun syncStates(sync: SyncStatus, cloud: CloudStatus): HistoryUiState = today().copy(sync = sync, cloud = cloud)

    private fun build(
        entries: List<Entry>,
        now: Long,
        sync: SyncStatus = SyncStatus.Synced(SampleData.SYNCED_1431),
        day: LocalDate? = null,
    ): HistoryUiState = HistoryStateBuilder.build(entries, sync, now, zone, day)
}
