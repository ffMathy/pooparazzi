package com.ffmathy.pooparazzi.phone.ui.sheets

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ffmathy.pooparazzi.core.format.Formatters
import com.ffmathy.pooparazzi.core.model.Baby
import com.ffmathy.pooparazzi.phone.ui.components.RadioGroup
import com.ffmathy.pooparazzi.phone.ui.home.HomeActions
import com.ffmathy.pooparazzi.phone.ui.home.HomeSheetUi
import java.time.ZoneId
import com.ffmathy.pooparazzi.core.R as CoreR

/** The Switch baby sheet's content: the babies in order and the current one. */
@Immutable
internal data class BabySwitcherUi(val babies: List<Baby>, val currentId: String)

/**
 * Home's logging sheets and the Switch baby sheet: at most one is open; each logging sheet animates closed before
 * Home's cooldown starts.
 */
@Composable
internal fun HomeSheets(
    sheet: HomeSheetUi?,
    zone: ZoneId,
    formatters: Formatters,
    actions: HomeActions,
    windowInsets: WindowInsets,
    switcher: BabySwitcherUi? = null,
) {
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
                onAmount = actions::onFormulaAmount,
                onLogFormula = actions::onLogFormula,
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
    XModalSheet(
        value = switcher,
        onDismissRequest = actions::onSwitchBabyDismiss,
        onClosed = {},
        windowInsets = windowInsets,
    ) { content ->
        BabySwitcherContent(content, formatters, actions)
    }
}

/**
 * Switch baby (BABIES_AND_BACKUP §2.4): one 56 dp radio row per baby (its label), the current one selected. A tap
 * selects that baby and closes the sheet.
 */
@Composable
private fun BabySwitcherContent(switcher: BabySwitcherUi, formatters: Formatters, actions: HomeActions) {
    val title = stringResource(CoreR.string.baby_switch)
    SheetColumn(title) {
        SheetHeader(title, actions::onSwitchBabyDismiss)
        Spacer(Modifier.height(12.dp))
        RadioGroup(
            options = switcher.babies,
            selected = switcher.babies.firstOrNull { it.id == switcher.currentId } ?: switcher.babies.first(),
            onSelect = { actions.onSelectBaby(it.id) },
            label = { formatters.labels.baby(it, switcher.babies) },
        )
    }
}
