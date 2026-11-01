package com.ffmathy.pooparazzi.phone.ui.history

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
import com.ffmathy.pooparazzi.core.data.observeCurrentLog
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.settings.SettingsRepository
import com.ffmathy.pooparazzi.phone.app.launchMinuteTicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * History's state (DESIGN_SPEC §3.3.3): the day shown (kept across process death), its ribbon, counts and entries,
 * soft deletes with an Undo receipt, and whose log it is ([settings]' current baby). Writes go through [repository];
 * actions run one at a time, in tap order.
 */
class HistoryViewModel(
    private val repository: EntryRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel(), HistoryActions {

    private val now = MutableStateFlow(clock.now())
    private val day = MutableStateFlow(savedState.get<Long>(KEY_DAY)?.let(LocalDate::ofEpochDay))
    private val receipt = MutableStateFlow<DeletedReceipt?>(null)
    private var nextReceiptKey = 0L
    private val actions = Mutex()

    /**
     * Null until the entries have loaded. The settings and the baby's entries come as one ([observeCurrentLog]), so a
     * switch of baby never lists the previous baby's log under the new baby's label.
     */
    val state: StateFlow<HistoryUiState?> = combine(
        repository.observeCurrentLog(settings.settings),
        now,
        day,
        receipt,
    ) { log, _, d, r ->
        val s = log.settings
        // [now] only re-runs this each minute; the time is read fresh, so an entry logged elsewhere within the current
        // minute (the watch, a widget) is listed at once.
        HistoryStateBuilder.build(
            log.entries, clock.now(), zone, d, r,
            babies = s.babies,
            babyId = s.selectedBaby.id,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launchMinuteTicker(clock, now)
    }

    override fun onPreviousDay() = act {
        val current = currentState() ?: return@act
        if (current.canGoBack) showDay(current.day.minusDays(1), current.today)
    }

    override fun onNextDay() = act {
        val current = currentState() ?: return@act
        if (current.canGoForward) showDay(current.day.plusDays(1), current.today)
    }

    override fun onDelete(entryId: String) = act {
        val previous = attempt { repository.delete(entryId) } ?: return@act
        if (!previous.deleted) onEntryDeleted(previous)
    }

    /** A delete made elsewhere on this screen (the edit sheet): offer Undo for [previous]. */
    fun onEntryDeleted(previous: Entry) {
        receipt.value = DeletedReceipt(++nextReceiptKey, previous)
    }

    override fun onUndoDelete(receipt: DeletedReceipt) = act {
        clearReceipt(receipt)
        // A running sleep cannot come back while another one runs; the entry then stays deleted.
        attempt { repository.restore(receipt.entry) }
    }

    override fun onReceiptDismissed(receipt: DeletedReceipt) = act { clearReceipt(receipt) }

    /** The default time of "Add past entry" on the day shown: now − 15 min, or that clock time on an earlier day. */
    fun addEntryDefaultTime(): Long {
        val t = clock.now() - ADD_DEFAULT_OFFSET_MILLIS
        val shown = state.value?.day ?: return t
        val at = Instant.ofEpochMilli(t).atZone(zone)
        if (shown >= at.toLocalDate()) return t
        return shown.atTime(at.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
    }

    private fun clearReceipt(shown: DeletedReceipt) {
        if (receipt.value?.key == shown.key) receipt.value = null
    }

    private fun showDay(next: LocalDate, today: LocalDate) {
        val value = if (next >= today) null else next
        day.value = value
        savedState[KEY_DAY] = value?.toEpochDay()
    }

    private suspend fun currentState(): HistoryUiState? = state.value ?: run {
        val log = repository.observeCurrentLog(settings.settings).first()
        val s = log.settings
        HistoryStateBuilder.build(
            log.entries, clock.now(), zone, day.value, receipt.value,
            babies = s.babies,
            babyId = s.selectedBaby.id,
        )
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

    private inline fun <T> attempt(block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    companion object {
        private const val KEY_DAY = "history_day"
        private const val ADD_DEFAULT_OFFSET_MILLIS = 15 * 60_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HistoryViewModel(
                    Core.repository, Core.settings, Core.clock,
                    savedState = createSavedStateHandle(),
                )
            }
        }
    }
}
