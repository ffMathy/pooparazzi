package com.ffmathy.xaternity.phone.ui.home

import androidx.compose.runtime.Stable
import com.ffmathy.xaternity.core.model.BreastSide
import com.ffmathy.xaternity.core.model.FeedType
import com.ffmathy.xaternity.core.model.PoopColor
import com.ffmathy.xaternity.core.model.PoopTexture

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
    /** Idle: opens the Feed sheet. While a breastfeed runs ("End feed"): opens the Side sheet. */
    fun onFeed() {}
    fun onDiaper() {}
    fun onSleepToggle() {}

    // Lamp
    fun onAdjustStart() {}

    // Undo card
    fun onCardOffset(minutes: Int) {}
    fun onCardEndSleep() {}
    fun onUndo() {}
    fun onCardDone() {}

    // Sheets
    fun onSheetOffset(minutes: Int) {}
    fun onFeedType(type: FeedType) {}
    fun onSide(side: BreastSide) {}
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
