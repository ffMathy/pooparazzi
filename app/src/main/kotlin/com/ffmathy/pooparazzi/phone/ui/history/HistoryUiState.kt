package com.ffmathy.pooparazzi.phone.ui.history

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.ffmathy.pooparazzi.core.domain.DayCounts
import com.ffmathy.pooparazzi.core.domain.Ribbon
import com.ffmathy.pooparazzi.core.model.Entry
import com.ffmathy.pooparazzi.core.sync.SyncStatus
import java.time.LocalDate
import java.time.ZoneId

/**
 * Everything History shows (DESIGN_SPEC §3.3.3, V1_ADDITIONS §4) for one local day, as data. Text is formatted by the
 * screen with the current locale's resources, so the same state renders in English and Danish.
 *
 * @property now the minute the screen describes (epoch ms).
 * @property day the day shown; [today] is the day containing [now].
 * @property canGoBack there is something logged before [day] ("Previous day" is enabled).
 * @property canGoForward [day] is before today ("Next day" is disabled on today).
 * @property ribbon the day ribbon geometry (glance-only).
 * @property counts feeds · pee · poop · sleep for [day].
 * @property entries the day's entries, newest first, including running ones.
 * @property receipt the last delete, offered for Undo in a snackbar.
 */
@Immutable
data class HistoryUiState(
    val now: Long,
    val zone: ZoneId,
    val day: LocalDate,
    val today: LocalDate,
    val sync: SyncStatus,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val ribbon: Ribbon,
    val counts: DayCounts,
    val entries: List<Entry>,
    val receipt: DeletedReceipt? = null,
) {
    val isToday: Boolean get() = day == today
}

/** A soft delete that can be undone; [key] tells two deletes of the same entry apart. */
@Immutable
data class DeletedReceipt(val key: Long, val entry: Entry)

/** Everything the parent can do on History. Defaults do nothing, so previews and screenshots can pass [None]. */
@Stable
interface HistoryActions {
    fun onBack() {}
    fun onRetrySync() {}
    fun onPreviousDay() {}
    fun onNextDay() {}

    /** A row tap or its "Edit time" action: the edit sheet for [entryId]. */
    fun onEdit(entryId: String) {}

    /** Swipe-to-reveal Delete or the row's "Delete" action: a soft delete with Undo. */
    fun onDelete(entryId: String) {}

    /** The "Add entry" FAB: the edit sheet in "Add past entry" mode. */
    fun onAddEntry() {}

    /** Undo in the snackbar: restores [receipt]'s entry. */
    fun onUndoDelete(receipt: DeletedReceipt) {}

    /** The snackbar for [receipt] went away without Undo. */
    fun onReceiptDismissed(receipt: DeletedReceipt) {}

    /** The `chart` button in the top bar: Statistics (P8). */
    fun onOpenStatistics() {}

    object None : HistoryActions
}
