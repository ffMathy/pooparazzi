package com.ffmathy.pooparazzi.phone.ui.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.unit.dp
import com.ffmathy.pooparazzi.core.format.Formatters
import com.ffmathy.pooparazzi.core.format.Labels
import com.ffmathy.pooparazzi.core.ui.icons.XIcon
import com.ffmathy.pooparazzi.core.ui.icons.XIcons
import com.ffmathy.pooparazzi.core.ui.tokens.LocalXPalette
import com.ffmathy.pooparazzi.core.ui.type.PhoneType
import com.ffmathy.pooparazzi.phone.R
import com.ffmathy.pooparazzi.phone.ui.components.XChip
import com.ffmathy.pooparazzi.phone.ui.history.deletedLabel
import com.ffmathy.pooparazzi.phone.ui.history.spokenSideCapitalised
import com.ffmathy.pooparazzi.phone.ui.home.HomeSheetUi
import com.ffmathy.pooparazzi.phone.ui.home.ReceiptVariant
import com.ffmathy.pooparazzi.phone.ui.home.TintedIcon
import com.ffmathy.pooparazzi.phone.ui.home.diaperIcon
import com.ffmathy.pooparazzi.phone.ui.home.diaperLabel
import com.ffmathy.pooparazzi.phone.ui.home.entryIcon
import com.ffmathy.pooparazzi.phone.ui.home.feedTypeLabel
import java.time.ZoneId
import com.ffmathy.pooparazzi.core.R as CoreR

/** What the receipt sheet can ask for. */
internal class ReceiptSheetCallbacks(
    val onUndo: () -> Unit,
    val onOffset: (Int) -> Unit,
    val onEndSleep: () -> Unit,
    val onEdit: (String) -> Unit,
    val onDone: () -> Unit,
)

/**
 * The receipt sheet after every log (DESIGN_SPEC §3.3.1): the event icon, "Formula logged" and below it the effective
 * time, which follows the chips ("14:27", "14:27 · Left → Right", "14:32 · Slept 1h 07m"), with Undo at the end; the
 * dedupe line; −15m · −10m · −5m · +5m, applied at once; "End sleep" for a feed or diaper logged during a running sleep; then "Edit" and "Done". It has
 * no timeout: it stays open until Done, Undo, Edit or a dismiss, and the log stays as shown.
 */
@Composable
internal fun ReceiptSheetContent(sheet: HomeSheetUi.Receipt, zone: ZoneId, formatters: Formatters, callbacks: ReceiptSheetCallbacks) {
    val readiness = rememberPickerReadiness()
    val colors = MaterialTheme.colorScheme
    val content = receiptContent(sheet.variant, formatters)
    // The effective time leads the second line ("14:27 · Left → Right"); it follows the chips.
    val time = if (sheet.chips.isEmpty()) null else formatters.clock.format(sheet.time, zone)
    val line = listOfNotNull(time, content.sub).takeIf { it.isNotEmpty() }?.joinToString(DOT)
    val lineSpoken = listOfNotNull(time, content.subSpoken ?: content.sub).joinToString(", ")
    val paneTitle = time?.let { stringResource(R.string.join_dot, content.title, it) } ?: content.title
    SheetColumn(paneTitle) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            XIcon(content.icon.icon, contentDescription = null, tint = content.icon.tint, size = 24.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    content.title,
                    style = MaterialTheme.typography.titleLarge.copy(hyphens = Hyphens.Auto),
                    color = colors.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                line?.let {
                    // TalkBack reads the spoken form ("14:27, Left then right", "Slept 1 hour 7 minutes"), not "→" and
                    // "1h 07m"; it is re-read when a chip moves the time.
                    Text(
                        it,
                        style = PhoneType.dataMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.semantics {
                            contentDescription = lineSpoken
                            liveRegion = LiveRegionMode.Polite
                        },
                    )
                }
            }
            if (sheet.undoable) UndoButton(enabled = readiness.ready, onClick = callbacks.onUndo, modifier = Modifier.alpha(readiness.alpha))
        }
        sheet.dedupe?.let { existing ->
            val icon = entryIcon(existing, LocalXPalette.current.events)
            Row(
                Modifier.padding(top = 4.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                XIcon(icon.icon, contentDescription = null, tint = icon.tint, size = 18.dp)
                Text(
                    stringResource(CoreR.string.guard_already_logged, formatters.clock.format(existing.startMillis, zone)),
                    style = PhoneType.dataMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        if (sheet.chips.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SheetChipRow(sheet.chips, formatters, readiness, callbacks.onOffset)
        }
        sheet.endSleep?.let { enabled ->
            Spacer(Modifier.height(8.dp))
            XChip(
                selected = false,
                onClick = callbacks.onEndSleep,
                label = stringResource(CoreR.string.action_end_sleep),
                enabled = enabled,
                role = Role.Button,
                acceptsTaps = readiness.ready,
                modifier = Modifier.alpha(readiness.alpha),
                leading = { XIcon(XIcons.wake, contentDescription = null, tint = colors.onSurface, size = 18.dp) },
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            sheet.editEntryId?.let { id -> EditButton(enabled = readiness.ready, onClick = { callbacks.onEdit(id) }) }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            SaveButton(enabled = readiness.ready, onClick = callbacks.onDone, label = stringResource(CoreR.string.action_done))
        }
    }
}

private const val DOT = " · "

/** Undo text button: 48 tall, r 24, `undo` 18 + 6 + "Undo" labelLarge, primary. */
@Composable
private fun UndoButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            XIcon(XIcons.undo, contentDescription = null, tint = colors.primary, size = 18.dp)
            Text(stringResource(CoreR.string.action_undo), style = MaterialTheme.typography.labelLarge, color = colors.primary, maxLines = 1)
        }
    }
}

/** A receipt's icon, time-free title and optional sub-line, with the sub-line's spoken form. */
private class ReceiptContent(val icon: TintedIcon, val title: String, val sub: String?, val subSpoken: String? = null)

@Composable
private fun receiptContent(variant: ReceiptVariant, formatters: Formatters): ReceiptContent {
    val events = LocalXPalette.current.events
    val duration = formatters.duration
    val wake = TintedIcon(XIcons.wake, events.sleep.strong)
    return when (variant) {
        is ReceiptVariant.FeedLogged -> ReceiptContent(
            TintedIcon(XIcons.forFeedType(variant.type), events.feed.strong),
            stringResource(CoreR.string.receipt_logged, feedTypeLabel(variant.type, compact = true)),
            variant.side?.let { stringResource(Labels.side(it)) } ?: variant.amountMl?.let(formatters.volume::format),
            variant.side?.let { spokenSideCapitalised(it) },
        )
        is ReceiptVariant.DiaperLogged -> ReceiptContent(
            diaperIcon(variant.kind, events),
            stringResource(CoreR.string.receipt_logged, diaperLabel(variant.kind)),
            null,
        )
        ReceiptVariant.SleepStarted -> ReceiptContent(TintedIcon(XIcons.sleep, events.sleep.strong), stringResource(CoreR.string.receipt_sleep_started), null)
        is ReceiptVariant.WokeUp -> ReceiptContent(
            wake,
            stringResource(CoreR.string.action_woke_up),
            stringResource(CoreR.string.receipt_slept, duration.format(variant.sleptMillis)),
            stringResource(CoreR.string.receipt_slept, duration.spoken(variant.sleptMillis)),
        )
        ReceiptVariant.SleepCancelled -> ReceiptContent(
            wake,
            stringResource(CoreR.string.receipt_sleep_cancelled),
            stringResource(CoreR.string.receipt_slept, duration.format(0L)),
            stringResource(CoreR.string.receipt_slept, duration.spoken(0L)),
        )
        is ReceiptVariant.Deleted -> ReceiptContent(
            entryIcon(variant.entry, events),
            stringResource(CoreR.string.receipt_deleted, deletedLabel(variant.entry)),
            null,
        )
    }
}
