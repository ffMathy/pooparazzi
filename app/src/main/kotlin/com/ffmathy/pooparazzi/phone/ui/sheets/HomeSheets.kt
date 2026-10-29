package com.ffmathy.pooparazzi.phone.ui.sheets

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ffmathy.pooparazzi.core.format.Formatters
import com.ffmathy.pooparazzi.phone.ui.home.HomeActions
import com.ffmathy.pooparazzi.phone.ui.home.HomeSheetUi
import java.time.ZoneId

/** Home's logging sheets and the Adjust time sheet: at most one is open; each animates closed before Home's cooldown starts. */
@Composable
internal fun HomeSheets(sheet: HomeSheetUi?, zone: ZoneId, formatters: Formatters, actions: HomeActions, windowInsets: WindowInsets) {
    XModalSheet(
        value = sheet as? HomeSheetUi.Feed,
        onDismissRequest = actions::onSheetDismiss,
        onClosed = actions::onSheetClosed,
        windowInsets = windowInsets,
    ) { feed ->
        FeedSheetContent(
            feed,
            zone,
            formatters,
            FeedSheetCallbacks(
                onOffset = actions::onSheetOffset,
                onType = actions::onFeedType,
                onTick = actions::onSideTick,
                onLogBreast = actions::onLogBreastfeed,
                onCancel = actions::onSheetDismiss,
            ),
        )
    }
    val diaperCallbacks = remember(actions) {
        DiaperSheetCallbacks(
            onOffset = actions::onSheetOffset,
            onPee = actions::onDiaperPee,
            onPoop = actions::onDiaperPoop,
            onColor = actions::onPoopColor,
            onTexture = actions::onPoopTexture,
            onSave = actions::onDiaperSave,
            onCancel = actions::onSheetDismiss,
        )
    }
    XModalSheet(
        value = sheet as? HomeSheetUi.Diaper,
        onDismissRequest = actions::onSheetDismiss,
        onClosed = actions::onSheetClosed,
        windowInsets = windowInsets,
    ) { diaper ->
        DiaperSheetContent(diaper, zone, formatters, diaperCallbacks)
    }
    val adjustCallbacks = remember(actions) {
        AdjustSheetCallbacks(
            onOffset = actions::onSheetOffset,
            onEdit = actions::onAdjustEdit,
            onSave = actions::onAdjustSave,
            onCancel = actions::onSheetDismiss,
        )
    }
    XModalSheet(
        value = sheet as? HomeSheetUi.Adjust,
        onDismissRequest = actions::onSheetDismiss,
        onClosed = actions::onSheetClosed,
        windowInsets = windowInsets,
    ) { adjust ->
        AdjustSheetContent(adjust, zone, formatters, adjustCallbacks)
    }
}
