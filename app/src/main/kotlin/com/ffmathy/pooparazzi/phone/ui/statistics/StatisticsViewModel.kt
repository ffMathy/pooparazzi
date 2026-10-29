package com.ffmathy.pooparazzi.phone.ui.statistics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ffmathy.pooparazzi.core.Core
import com.ffmathy.pooparazzi.core.data.Clock
import com.ffmathy.pooparazzi.core.data.EntryRepository
import com.ffmathy.pooparazzi.core.domain.StatsMetric
import com.ffmathy.pooparazzi.core.domain.StatsRange
import com.ffmathy.pooparazzi.core.domain.normalised
import com.ffmathy.pooparazzi.phone.app.launchMinuteTicker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId

/**
 * Statistics' state (P8, V1_ADDITIONS §8): the open row and the comparison range, both kept across process death, over
 * the whole log. A new visit starts at the last 30 days with Poop open. Actions run one at a time, in tap order.
 */
class StatisticsViewModel(
    private val repository: EntryRepository,
    private val clock: Clock,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel(), StatisticsActions {

    private val now = MutableStateFlow(clock.now())
    private val metric = MutableStateFlow(restoredMetric())
    private val range = MutableStateFlow(restoredRange())
    private val actions = Mutex()

    /** Null until the entries have loaded. */
    val state: StateFlow<StatisticsUiState?> = combine(repository.observeEntries(), now, metric, range) { entries, _, m, r ->
        // [now] only re-runs this each minute (so the windows move at midnight); the time is read fresh, so an entry
        // logged elsewhere is counted at once.
        StatisticsStateBuilder.build(entries, clock.now(), zone, m, r)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launchMinuteTicker(clock, now)
    }

    override fun onMetric(metric: StatsMetric) = act {
        this.metric.value = metric
        savedState[KEY_METRIC] = metric.name
    }

    override fun onRange(range: StatsRange) = act {
        val report = state.value?.report
        // Dates that equal a preset's become that preset; dates outside the log are cut to it.
        val stored = if (range is StatsRange.Custom && report != null) range.normalised(report.firstDay, report.lastDay) else range
        this.range.value = stored
        savedState[KEY_RANGE] = when (stored) {
            StatsRange.Last30Days -> RANGE_LAST_30_DAYS
            StatsRange.Last90Days -> RANGE_LAST_90_DAYS
            StatsRange.AllDays -> RANGE_ALL_DAYS
            is StatsRange.Custom -> RANGE_CUSTOM
        }
        savedState[KEY_FROM] = (stored as? StatsRange.Custom)?.from?.toEpochDay()
        savedState[KEY_TO] = (stored as? StatsRange.Custom)?.to?.toEpochDay()
    }

    /** The saved row; an unknown name opens Poop. */
    private fun restoredMetric(): StatsMetric {
        val name = savedState.get<Any>(KEY_METRIC) as? String
        return StatsMetric.entries.firstOrNull { it.name == name } ?: StatsMetric.POOP
    }

    /**
     * The saved range; anything unknown or incomplete (chosen dates without both ends, or in the wrong order) is the
     * last 30 days.
     */
    private fun restoredRange(): StatsRange = when (savedState.get<Any>(KEY_RANGE) as? String) {
        RANGE_LAST_90_DAYS -> StatsRange.Last90Days
        RANGE_ALL_DAYS -> StatsRange.AllDays
        RANGE_CUSTOM -> {
            val from = savedState.get<Any>(KEY_FROM) as? Long
            val to = savedState.get<Any>(KEY_TO) as? Long
            if (from != null && to != null && from <= to) {
                StatsRange.Custom(LocalDate.ofEpochDay(from), LocalDate.ofEpochDay(to))
            } else {
                StatsRange.Last30Days
            }
        }
        else -> StatsRange.Last30Days
    }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            actions.withLock {
                now.value = clock.now()
                block()
                now.value = clock.now()
            }
        }
    }

    companion object {
        private const val KEY_METRIC = "statistics_metric"
        private const val KEY_RANGE = "statistics_range"
        private const val KEY_FROM = "statistics_from"
        private const val KEY_TO = "statistics_to"
        private const val RANGE_LAST_30_DAYS = "LAST_30_DAYS"
        private const val RANGE_LAST_90_DAYS = "LAST_90_DAYS"
        private const val RANGE_ALL_DAYS = "ALL_DAYS"
        private const val RANGE_CUSTOM = "CUSTOM"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { StatisticsViewModel(Core.repository, Core.clock, savedState = createSavedStateHandle()) }
        }
    }
}
