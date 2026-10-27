package com.ffmathy.xaternity.phone.ui.preview

import com.ffmathy.xaternity.core.model.Entry
import com.ffmathy.xaternity.core.model.EntryKind
import com.ffmathy.xaternity.core.model.FeedType
import com.ffmathy.xaternity.core.sample.SampleData
import com.ffmathy.xaternity.phone.ui.edit.EditDraft
import com.ffmathy.xaternity.phone.ui.edit.EditMode
import com.ffmathy.xaternity.phone.ui.edit.EditRules
import com.ffmathy.xaternity.phone.ui.edit.EditUiState

/** Edit-sheet states from the reference scenario, built through [EditRules] like the app's own. */
object EditSamples {
    private val zone = SampleData.ZONE
    private const val MINUTE = 60_000L

    /** The 12:40 – 13:27 sleep with its start nudged −5m: 12:35 "was 12:40", "Duration 52m · was 47m". */
    fun sleepNudged(): EditUiState {
        val sleep = entry("sample-sleep-1240")
        return edit(sleep, EditDraft.of(sleep).copy(start = sleep.startMillis - 5 * MINUTE))
    }

    /** The 08:00 – 08:14 breastfeed (Right → Left): type, start, end, the sides group and the duration. */
    fun breastfeed(): EditUiState {
        val feed = entry("sample-breast-0800")
        return edit(feed, EditDraft.of(feed))
    }

    /** The 08:20 diaper: Poop with Brown and Mushy selected. */
    fun diaperPoop(): EditUiState {
        val diaper = entry("sample-poop-0820")
        return edit(diaper, EditDraft.of(diaper))
    }

    /** "Add past entry" from History at 14:32: Feed preselected (as the last feed, Formula), at 14:17. */
    fun addPastEntry(): EditUiState {
        val start = SampleData.NOW_1432 - 15 * MINUTE
        val draft = EditRules.newDraft("sample-new", EntryKind.FEED, start, SampleData.NOW_1432, FeedType.FORMULA)
        return EditRules.state(1, EditMode.ADD, draft, null, false, SampleData.scenario(zone), SampleData.NOW_1432, zone)
    }

    /** The same sleep with its end moved before its start: the blocking validation message. */
    fun sleepEndBeforeStart(): EditUiState {
        val sleep = entry("sample-sleep-1240")
        return edit(sleep, EditDraft.of(sleep).copy(end = sleep.startMillis - 10 * MINUTE))
    }

    private fun entry(id: String): Entry = SampleData.scenario(zone).single { it.id == id }

    private fun edit(original: Entry, draft: EditDraft): EditUiState =
        EditRules.state(1, EditMode.EDIT, draft, original, false, SampleData.scenario(zone), SampleData.NOW_1432, zone)
}
