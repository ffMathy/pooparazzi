package com.ffmathy.pooparazzi.phone.ui.sheets

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ffmathy.pooparazzi.core.format.Formatters
import com.ffmathy.pooparazzi.phone.ui.home.HomeActions
import com.ffmathy.pooparazzi.phone.ui.home.HomeSheetUi
import java.time.ZoneId

/** Home's logging sheets: at most one is open; each animates closed before Home's cooldown starts. */
@Composable
internal fun HomeSheets(sheet: HomeSheetUi?, zone: ZoneId, formatters: Formatters, actions: HomeActions, windowInsets: WindowInsets) {
    XModalSheet(
        value = sheet as? HomeSheetUi.Feed,
        onDismissRequest = actions::onSheetDismiss,
        onClosed = actions::onSheetClosed,
        windowInsets = windowInsets,
    ) { feed ->
        FeedSheetContent(feed, zone, formatters, actions::onSheetOffset, actions::onFeedType, actions::onSheetDismiss)
    }
    XModalSheet(
        value = sheet as? HomeSheetUi.Side,
        onDismissRequest = actions::onSheetDismiss,
        onClosed = actions::onSheetClosed,
        windowInsets = windowInsets,
    ) { side ->
        SideSheetContent(side, zone, formatters, actions::onSheetOffset, actions::onSideTick, actions::onEndFeed, actions::onSheetDismiss)
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
}
