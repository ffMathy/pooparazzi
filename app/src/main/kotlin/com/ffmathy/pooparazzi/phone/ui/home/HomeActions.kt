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

    /** The cloud icon in "Couldn't back up": backs up again (BABIES_AND_BACKUP §3.2). */
    fun onRetryBackup() {}

    /** The name with two or more babies: opens the Switch baby sheet (BABIES_AND_BACKUP §2.4). */
    fun onSwitchBaby() {}

    /** Switch baby sheet: makes [babyId] the current baby and closes the sheet. */
    fun onSelectBaby(babyId: String) {}

    /** Switch baby sheet: Cancel, a scrim tap, back or a swipe. */
    fun onSwitchBabyDismiss() {}

    /** A since-row or the Adjust card's Edit chip: the edit sheet for [entryId]. */
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
    fun onAdjustStart() {}

    // Undo card
    fun onCardOffset(minutes: Int) {}
    fun onCardEndSleep() {}
    fun onUndo() {}
    fun onCardDone() {}

    // Sheets
    fun onSheetOffset(minutes: Int) {}

    /**
     * Feed sheet: Breast reveals the side checkboxes and "Log feed", Formula the amount slider and "Log feed"; Food
     * logs an instant, null ("Just the time") logs an untyped feed.
     */
    fun onFeedType(type: FeedType?) {}

    /** Feed sheet, Breast step: ticks or unticks [breast]; the tick order is the order recorded (re-ticking appends). */
    fun onSideTick(breast: Breast) {}

    /** Feed sheet, Breast step, "Log feed": logs a breast feed at the sheet's time with the ticked order (or none). */
    fun onLogBreastfeed() {}

    /** Feed sheet, Formula step: the slider's amount in millilitres; null is no amount (the slider at its start). */
    fun onFormulaAmount(amountMl: Int?) {}

    /** Feed sheet, Formula step, "Log feed": logs a formula feed at the sheet's time with the amount (or none). */
    fun onLogFormula() {}
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
