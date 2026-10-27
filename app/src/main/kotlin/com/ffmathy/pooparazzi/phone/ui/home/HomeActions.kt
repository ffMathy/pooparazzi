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

    /** A since-row or the Adjust card's Edit chip: the edit sheet for [entryId]. */
    fun onEditEntry(entryId: String) {}

    // Big buttons
    /**
     * Idle ("Feed"): opens the Feed sheet. While a breastfeed runs ("End feed"): opens the Side sheet. [shownRunning]
     * is the form the button showed (true for "End feed"): when the feed has started or ended elsewhere since (the
     * watch, a widget), the tap is answered with the guard instead of the other sheet (DESIGN_SPEC §4.4).
     */
    fun onFeed(shownRunning: Boolean) {}
    fun onDiaper() {}

    /**
     * "Start sleep" ([shownRunning] false) or "Woke up" (true), as the button showed it. A sleep started or ended
     * elsewhere since then is not toggled back: the tap shows "Already asleep …" / "Already awake …" (DESIGN_SPEC §4.4).
     */
    fun onSleepToggle(shownRunning: Boolean) {}

    // Lamp
    fun onAdjustStart() {}

    // Undo card
    fun onCardOffset(minutes: Int) {}
    fun onCardEndSleep() {}
    fun onUndo() {}
    fun onCardDone() {}

    // Sheets
    fun onSheetOffset(minutes: Int) {}

    /** Feed sheet: Breast starts a timer, Formula / Food log an instant, null ("Just the time") logs an untyped feed. */
    fun onFeedType(type: FeedType?) {}

    /** Side sheet: ticks or unticks [breast]; the tick order is the order recorded (re-ticking appends). */
    fun onSideTick(breast: Breast) {}

    /** Side sheet "End feed": ends the running breastfeed with the ticked order (nothing ticked records no side). */
    fun onEndFeed() {}
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
