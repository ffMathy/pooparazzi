package com.ffmathy.pooparazzi.phone.ui.home

import androidx.compose.runtime.Stable
import com.ffmathy.pooparazzi.core.model.Breast
import com.ffmathy.pooparazzi.core.model.FeedType
import com.ffmathy.pooparazzi.core.model.PoopColor
import com.ffmathy.pooparazzi.core.model.PoopTexture

/**
 * Everything the parent can do on Home and in its sheets. Defaults do nothing, so previews and screenshots can pass
 * [HomeActions.None]; the app combines [HomeViewModel] (state changes) with navigation callbacks.
 */
@Stable
interface HomeActions {
    // Top bar and navigation
    fun onOpenHistory() {}
    fun onOpenSettings() {}
    fun onRetrySync() {}

    /** A since-row: the edit sheet for [entryId]. */
    fun onEditEntry(entryId: String) {}

    // Big buttons
    /** "Feed": opens the Feed sheet. */
    fun onFeed() {}
    fun onDiaper() {}

    /**
     * "Start sleep" ([shownRunning] false) or "Woke up" (true), as the button showed it. A sleep started or ended
     * elsewhere since then is not toggled back: the tap shows "Already asleep …" / "Already awake …" (DESIGN_SPEC §4.4).
     */
    fun onSleepToggle(shownRunning: Boolean) {}

    // Lamp
    /** "Adjust start": the Adjust time sheet for the running sleep's start. */
    fun onAdjustStart() {}

    // Undo card
    /** "Adjust time": the Adjust time sheet for the card's entry; the card's window waits while it is open. */
    fun onCardAdjust() {}
    fun onCardEndSleep() {}
    fun onUndo() {}

    // Sheets
    /** Selects an offset chip. In the Adjust time sheet, re-tapping the selected chip returns it to 0. */
    fun onSheetOffset(minutes: Int) {}

    /** Adjust time sheet, "Save": moves the entry to the sheet's time and closes the sheet. */
    fun onAdjustSave() {}

    /** Adjust time sheet, "Edit": saves the chosen offset, closes the sheet and opens the edit sheet for [entryId]. */
    fun onAdjustEdit(entryId: String) {}

    /**
     * Feed sheet: Breast reveals the side checkboxes and "Log feed"; Formula / Food log an instant, null ("Just the
     * time") logs an untyped feed.
     */
    fun onFeedType(type: FeedType?) {}

    /** Feed sheet, Breast step: ticks or unticks [breast]; the tick order is the order recorded (re-ticking appends). */
    fun onSideTick(breast: Breast) {}

    /** Feed sheet, Breast step, "Log feed": logs a breast feed at the sheet's time with the ticked order (or none). */
    fun onLogBreastfeed() {}
    fun onDiaperPee(checked: Boolean) {}
    fun onDiaperPoop(checked: Boolean) {}

    /** Selects [color]; null clears it (tapping the selected chip again). */
    fun onPoopColor(color: PoopColor?) {}

    /** Selects [texture]; null clears it. */
    fun onPoopTexture(texture: PoopTexture?) {}
    fun onDiaperSave() {}

    /** Cancel, a scrim tap, back or a swipe: the sheet closes without logging. */
    fun onSheetDismiss() {}

    /** The sheet has finished its closing animation (starts the 600 ms Home cooldown after a commit). */
    fun onSheetClosed() {}

    /** Does nothing: previews and screenshots. */
    object None : HomeActions
}
