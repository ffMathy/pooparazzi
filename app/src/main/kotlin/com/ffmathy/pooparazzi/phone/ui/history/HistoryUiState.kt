package com.ffmathy.pooparazzi.phone.ui.history

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.ffmathy.pooparazzi.core.cloud.CloudStatus
import com.ffmathy.pooparazzi.core.domain.DayCounts
import com.ffmathy.pooparazzi.core.domain.Ribbon
import com.ffmathy.pooparazzi.core.model.Baby
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
 * @property cloud the backup's status; [CloudStatus.Off] (nobody signed in) shows no cloud icon.
 * @property canGoBack there is something logged before [day] ("Previous day" is enabled).
 * @property canGoForward [day] is before today ("Next day" is disabled on today).
 * @property ribbon the day ribbon geometry (glance-only).
 * @property counts feeds · pee · poop · sleep for [day].
 * @property entries the day's entries, newest first, including running ones.
 * @property receipt the last delete, offered for Undo in a snackbar.
 * @property babies all babies, in order; with two or more the current baby's label shows under the title.
 * @property babyId the current baby, whose log this is.
 */
@Immutable
data class HistoryUiState(
    val now: Long,
    val zone: ZoneId,
    val day: LocalDate,
    val today: LocalDate,
    val sync: SyncStatus,
    val cloud: CloudStatus = CloudStatus.Off,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val ribbon: Ribbon,
    val counts: DayCounts,
    val entries: List<Entry>,
    val receipt: DeletedReceipt? = null,
    val babies: List<Baby> = listOf(Baby(Baby.FIRST_ID)),
    val babyId: String = babies.first().id,
) {
    val isToday: Boolean get() = day == today

    /** Two or more babies: the title shows whose log this is (BABIES_AND_BACKUP §2.4). */
    val hasSeveralBabies: Boolean get() = babies.size > 1

    /** The current baby (the first when [babyId] is not one of [babies]). */
    val baby: Baby get() = babies.firstOrNull { it.id == babyId } ?: babies.first()
}

/** A soft delete that can be undone; [key] tells two deletes of the same entry apart. */
@Immutable
data class DeletedReceipt(val key: Long, val entry: Entry)

/** Everything the parent can do on History. Defaults do nothing, so previews and screenshots can pass [None]. */
@Stable
interface HistoryActions {
    fun onBack() {}
    fun onRetrySync() {}
    fun onRetryBackup() {}
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
